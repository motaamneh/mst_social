# Verification API

Start the server using the [setup guide](README.md). It applies V4 automatically,
adding `api_rate_limits` to the existing database. For tenant creation, credential
setup, and an importable Postman collection/environment, follow the
[start-to-finish Postman guide](POSTMAN_GUIDE.md). You can also import
[openapi.yaml](openapi.yaml) or create the requests below manually.

All `/api/v1` endpoints require `Authorization: Bearer <tenant-api-key>`.
The key identifies your application. Your backend must authenticate its own users
and authorize which `subjectId`, verification, and account they may access. Keep
the tenant key out of browser and mobile code.

## Interactive documentation

Use [Swagger UI](../docs/SWAGGER_GUIDE.md) at `/swagger-ui.html` when
`MST_API_DOCS_ENABLED=true` (default in Docker Compose). The live definition is
available at `/v3/api-docs` and `/v3/api-docs.yaml`. The Java annotations and
OpenAPI configuration are the source of the checked-in YAML export.

## Endpoints

Paths below start with `/api/v1`. Resource IDs are UUIDs.

| Method and path | Purpose | Successful response |
| --- | --- | --- |
| `GET /tenant` | Inspect authenticated tenant and credential IDs | `200` |
| `POST /verifications` | Create a request and return its code once | `201` + `Location` |
| `GET /verifications/{id}` | Read current request status | `200` |
| `POST /verifications/{id}/verify` | Check the provider's current profile evidence | `200` on success |
| `DELETE /verifications/{id}` | Cancel an unfinished request | `200` with current status |
| `GET /social-identities/{id}` | Read a verified account association | `200` |
| `DELETE /social-identities/{id}` | Revoke an account association | `200` with `active: false` |

Successful bodies use `application/json`. Errors use `application/problem+json`.
Verification and account responses include `Cache-Control: no-store`.
Resources belonging to another tenant have the same `404` response as missing resources.

## 1. Create a verification

In Postman, set **Authorization → Bearer Token** to your tenant key. Send
`POST http://localhost:8080/api/v1/verifications` with **Body → raw → JSON**:

```json
{
  "subjectId": "user-123",
  "platform": "INSTAGRAM",
  "handle": "@your_username"
}
```

`subjectId` is the user ID from your application; MST Social does not generate it.
The tenant comes from authentication, so there is no `tenantId` input field.
`INSTAGRAM` and `TIKTOK` are supported. Known but unsupported platform values,
`X` and `FACEBOOK`, return `422`. Unknown strings and numeric enum values return `400`.

The subject must be nonblank, at most 128 Java characters, and contain no ASCII
control characters. The submitted handle must be nonblank and at most 256 Java
characters. The core then normalizes it and applies the platform-specific rules
described in the [core guide](../mst-social-core/README.md#handle-normalization).

A `201` response has this shape; values below are illustrative:

```json
{
  "requestId": "01b52c00-3308-4ea1-97bc-a4c45fa0ee9a",
  "code": "<the generated verification code>",
  "expiresAt": "2030-01-01T12:15:00Z",
  "instructions": "Add this exact code to your social profile bio, then submit a verification attempt before expiry."
}
```

The `Location` header is `/api/v1/verifications/<requestId>` (prefixed by the
configured servlet context path, if any). Save the ID in your backend and show the
code privately to the user. Do not log this response. Creation makes no SearchAPI
call. The database stores a digest, never the plaintext code.

Only creation returns the code. `GET` cannot recover it. If the creation response
is lost, repeating the same active request returns `409 DUPLICATE_ACTIVE_REQUEST`.
Durable creation replay is not implemented: every mutation rejects an
`Idempotency-Key` header with `400 IDEMPOTENCY_NOT_SUPPORTED`.

## 2. Read the status

Send `GET /api/v1/verifications/<requestId>` with the same authorization. A newly
created request returns:

```json
{
  "requestId": "01b52c00-3308-4ea1-97bc-a4c45fa0ee9a",
  "subjectId": "user-123",
  "platform": "INSTAGRAM",
  "normalizedHandle": "your_username",
  "status": "PENDING",
  "failedAttempts": 0,
  "maxFailedAttempts": 10,
  "createdAt": "2030-01-01T12:00:00Z",
  "expiresAt": "2030-01-01T12:15:00Z",
  "verifiedAt": null,
  "canceledAt": null
}
```

GET does not call SearchAPI or increase failed attempts. It persists expiration
when the deadline has passed. An expired request is still readable with `200`
and `status: EXPIRED`; attempting to verify it returns `410`.

## 3. Verify after the user updates their bio

Send `POST /api/v1/verifications/<requestId>/verify` with **Body → none**.
Even `{}` is rejected. You cannot supply a biography, verification code, or provider
account ID as evidence. The server fetches the profile itself.

One eligible verify call performs one provider lookup, with no automatic retries.
Private profiles are allowed when the provider supplies a usable username and bio.
A successful response has this shape:

```json
{
  "outcome": "VERIFIED",
  "verification": {
    "requestId": "01b52c00-3308-4ea1-97bc-a4c45fa0ee9a",
    "subjectId": "user-123",
    "platform": "INSTAGRAM",
    "normalizedHandle": "your_username",
    "status": "VERIFIED",
    "failedAttempts": 0,
    "maxFailedAttempts": 10,
    "createdAt": "2030-01-01T12:00:00Z",
    "expiresAt": "2030-01-01T12:15:00Z",
    "verifiedAt": "2030-01-01T12:02:00Z",
    "canceledAt": null
  },
  "account": {
    "accountId": "22636c60-562c-411c-b1d5-2a4dab761f7b",
    "subjectId": "user-123",
    "platform": "INSTAGRAM",
    "canonicalHandle": "your_username",
    "providerAccountId": null,
    "evidenceProvider": "searchapi",
    "verifiedAt": "2030-01-01T12:02:00Z",
    "validUntil": "2031-01-01T12:02:00Z",
    "revokedAt": null,
    "active": true
  }
}
```

The default validity is one calendar year. Save `account.accountId` for account
lookup and revocation. The observation establishes control at verification time;
it does not continually monitor control or prove immutable identity when the
provider supplies no stable account ID. Unknown provider source timestamps remain
unknown; an HTTP fetch does not itself guarantee fresh upstream evidence.

A retry of a verified request returns `200 ALREADY_VERIFIED` and its original
association without another provider call or extension of validity. If that
association has since expired or been revoked, its returned `active` flag is false.
Create a new verification to establish a new association.

Only a usable matching profile without the code consumes a failed attempt.
Failures 1–9 return `422 CODE_NOT_FOUND` and leave the request `PENDING`.
The tenth returns `409 LOCKED`. Provider errors and timeouts do not consume these
attempts. An overlapping attempt returns `409 IN_PROGRESS`; read status before
retrying. An old attempt that loses its lease returns `409 STALE_ATTEMPT`.

## 4. Cancel or revoke

Send `DELETE /api/v1/verifications/<requestId>` with no body to cancel a pending
or in-progress request. It returns the updated request. Repeating it preserves the
original cancellation time. Terminal requests are returned unchanged. Canceling
a verified request does **not** revoke its account association.

Use `GET /api/v1/social-identities/<accountId>` to read an association and its
current `active` flag. Expired and revoked associations remain readable.
Use `DELETE /api/v1/social-identities/<accountId>` with no body to revoke it.
Revocation returns the association with `active: false`; repeated revocation
preserves its original timestamp. Historical rows and audit events are retained.

## Error responses

Use `code` for application decisions. `detail` is a safe explanation, not an
exception message. Optional fields appear only when relevant:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more request fields are invalid.",
  "errors": [{ "field": "subjectId", "message": "must not be blank" }]
}
```

| HTTP status | Codes / meaning |
| --- | --- |
| `400` | `VALIDATION_FAILED`, `INVALID_HANDLE`, `INVALID_REQUEST`, `UNEXPECTED_BODY`, `IDEMPOTENCY_NOT_SUPPORTED` |
| `401` | `UNAUTHORIZED`; check the tenant credential |
| `403` | `FORBIDDEN`; path not permitted by security rules |
| `404` | `REQUEST_NOT_FOUND`, `ACCOUNT_NOT_FOUND`, or an unmapped API path |
| `405` / `406` / `415` | Unsupported HTTP method, response media type, or request media type |
| `409` | `DUPLICATE_ACTIVE_REQUEST`, `IN_PROGRESS`, `STALE_ATTEMPT`, `ACCOUNT_ALREADY_LINKED`, `CANCELED`, `LOCKED` |
| `410` | `EXPIRED` when attempting verification |
| `422` | `UNSUPPORTED_PLATFORM`, `CODE_NOT_FOUND`, or provider profile unavailable/not found |
| `429` | `RATE_LIMIT_EXCEEDED` or `ACTIVE_REQUEST_LIMIT_REACHED` |
| `502` | Provider authentication failure or invalid provider response |
| `503` | Provider rate limiting/error, storage unavailable, or authentication storage unavailable |
| `504` | Provider timeout |
| `500` | Unexpected internal failure; raw SQL, exception text, and credentials are omitted |

Unsuccessful verify outcomes include `verification` with the resulting request
status. A provider failure uses `code: PROVIDER_FAILURE` and adds
`providerFailure: { "reason": "PROVIDER_TIMEOUT", "retryable": true }`.
When the provider supplies a retry delay, `retryAfterSeconds` and the `Retry-After`
header contain ceiling seconds bounded to one day. Retryable means another attempt
may work; the server does not schedule it. Respect delays and avoid automatic polling.

## Limits and request parsing

V4 stores up to four rate-limit buckets per tenant, shared across credentials and
server instances. PostgreSQL atomically admits requests using its own clock. Each
bucket's one-minute window starts on its first request and resets after expiry.

| Operation | Default requests per minute | Property under `mst.api.rate-limits` |
| --- | --- | --- |
| Create | 10 | `create-per-minute` |
| Verify | 10 | `verify-per-minute` |
| Read verification/account | 120, shared | `read-per-minute` |
| Cancel/revoke | 30, shared | `mutation-per-minute` |

Configure positive values in YAML or Spring environment variables such as
`MST_API_RATE_LIMITS_VERIFY_PER_MINUTE`. Rate-limit denial returns `429` with
`Retry-After`. Calls admitted to the service count even if the resource is missing
or the domain rejects the operation. Requests rejected during authentication,
JSON binding, bean validation, or empty-body/header checks do not use these buckets.
The identity and health endpoints do not use them.

Fixed windows can allow bursts near a boundary. Deployments should also enforce
request/body/connection limits and IP throttling at their reverse proxy, including
unauthenticated traffic. The rate limiter is not a strict billing quota.

Separately, the core allows three active requests per subject and three per
platform/handle within a tenant. Reaching an active quota returns
`ACTIVE_REQUEST_LIMIT_REACHED` without a guessed retry time: cancel an existing
request or wait for expiry. Ten failed bio matches is a **per-verification** limit,
distinct from the ten HTTP verify calls per tenant per minute.

JSON input rejects unknown or duplicate fields, trailing JSON values, numeric enum
ordinals, and scalar-to-string coercion. Parsing is bounded to depth 8, 128 tokens,
1,024 characters per string, 128 per field name, 32 per number, and a document length
limit of 16,384 input units. These parser constraints complement deployment body
limits. Validation errors do not echo submitted values.

## How the implementation connects

```text
Bearer credential -> Spring Security -> TenantPrincipal
  -> controller -> validated request class / UUID
  -> ApiRateLimiter -> ApiRateLimitRepository -> PostgreSQL
  -> core VerificationService
       -> claim transaction -> SearchAPI outside transaction -> completion transaction
  -> response DTO record / VerificationHttpMapper / VerificationExceptionHandler
```

Controllers translate HTTP input and choose HTTP output. Business rules stay in the
core, and SQL stays in persistence adapters. Controllers are not transactional;
holding a database transaction during the provider call would violate the storage
contract. DTOs are mapped explicitly so storage digests, leases, and biographies
cannot accidentally become HTTP fields.

Only HTTP response DTOs are records. Request DTOs use ordinary classes with Lombok
and Jakarta validation. Core values and snapshots are immutable Java classes with
explicit constructors and accessors, without Spring or Lombok dependencies.

## Local checks without a provider call

1. Start PostgreSQL and the server; confirm Flyway reaches V4 and health is up.
2. Call the tenant endpoint with your key.
3. Create a request, read it, and submit the same creation again to observe `409`.
4. Try a missing subject or an extra `tenantId` field to observe `400`.
5. Cancel the request, then cancel it again. Both return its canceled state.
6. Verify that **canceled** request to observe `409 CANCELED` without a provider call.
7. Read an unknown UUID to observe `404` and omit credentials to observe `401`.

An actual successful bio verification requires an explicitly initiated SearchAPI
lookup against a profile with the returned code. That consumes provider usage.
The build command skips test compilation and execution; no automated tests were
added for this branch.
