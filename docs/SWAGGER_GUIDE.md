# Swagger UI and OpenAPI: start to finish

OpenAPI is a machine-readable description of the HTTP API: routes, inputs,
responses, and authentication. Swagger UI turns that description into a web page
where you can inspect operations and send real requests. Springdoc builds the
description from our Spring controllers and DTOs while the server is running.

## 1. Start the server with documentation enabled

### Docker Compose

Complete the [Docker setup](DOCKER_GUIDE.md) first. Compose enables docs by default.
You can also set this explicitly in your existing `.env.docker`:

```dotenv
MST_API_DOCS_ENABLED=true
```

From the repository root, rebuild and start:

```sh
docker compose --env-file .env.docker up --build -d --wait
```

Keep your existing database password and hash keys. Do not replace `.env.docker`
just to add this setting. A valid database and the normal server configuration are
still required; Swagger is served by the application, not a separate container.

### IntelliJ or the executable JAR

Add `MST_API_DOCS_ENABLED=true` to the application's environment variables alongside
your existing database and provider configuration, then restart it. For a shell
where those existing variables are already exported:

```sh
./mvnw -Dmaven.test.skip=true package
MST_API_DOCS_ENABLED=true java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar
```

Outside Compose, docs default to disabled. This switch controls both OpenAPI
endpoints and the UI's security allowlist. Use it instead of overriding individual
`springdoc.*.enabled` properties.

## 2. Open the documentation

| URL at the default port | Purpose |
| --- | --- |
| <http://localhost:8080/swagger-ui.html> | Interactive UI (redirects to its index page) |
| <http://localhost:8080/v3/api-docs> | OpenAPI JSON |
| <http://localhost:8080/v3/api-docs.yaml> | OpenAPI YAML for Postman or other tools |
| <http://localhost:8080/actuator/health> | Application health |

If `MST_HTTP_PORT=8081`, use port 8081. If you set a servlet context path, prefix
these routes with it. The OpenAPI server URL uses this server's origin and context
path, so the UI does not force requests to port 8080.

The enabled documentation is readable without a key. Every `/api/v1/**` operation
still requires a valid tenant API key. Health is not included in the generated API
contract; it remains a separate operational endpoint.

## 3. Get a tenant ID and API key

A tenant represents **your consuming application**. `subjectId` represents one
user within that application. Swagger does not create tenants or issue keys.

For Docker, create a tenant in Terminal:

```sh
docker compose --env-file .env.docker exec db \
  psql -U mst_social -d mst_social_db \
  -c 'INSERT INTO tenants DEFAULT VALUES RETURNING id;'
```

Copy the returned UUID and create a key:

```sh
docker compose --env-file .env.docker exec app \
  java -jar /app/app.jar tenant-key create \
  --tenant-id="REPLACE_WITH_TENANT_UUID" \
  --name="Local Swagger"
```

Run this interactively without `-T`, a pipe, or output redirection. Save the complete
one-time `mst_api_...` credential. For a non-Docker server, follow the
[credential guide](../mst-social-server/README.md#tenant-api-key-authentication).

## 4. Authorize Swagger

1. Open Swagger UI on your own trusted server.
2. Click **Authorize**.
3. Paste the complete tenant API key into `tenantKey`.
4. Do **not** type `Bearer ` yourself: the UI supplies that prefix.
5. Click **Authorize**, then **Close**.
6. Expand **Tenant → GET /api/v1/tenant**.
7. Click **Try it out → Execute**.

A `200` response contains `tenantId` and `credentialId`. This confirms the key
works. The credential is not a JWT, a SearchAPI key, a database password, or either
server hash key. You do not put the tenant ID in verification request bodies.

Authorization persistence is disabled, so reloading the page requires authorization
again. The UI still holds the key in memory while you use it; use **Logout** in the
Authorize dialog when finished. Do not share screenshots or generated curl commands
containing credentials. In an actual Next.js application, keep the tenant key on
the backend; Swagger is an operator/developer tool.

## 5. Perform the verification flow

### Create

Open **POST /api/v1/verifications**, click **Try it out**, and edit the JSON:

```json
{
  "subjectId": "user-123",
  "platform": "INSTAGRAM",
  "handle": "@your_username"
}
```

Use your own account's username, or `TIKTOK` for TikTok. Execute once. The `201`
response returns `requestId`, `code`, `expiresAt`, and instructions. Copy the
**actual returned code** into that social account's bio, preserving it exactly.
Example values shown in the schema are illustrative and cannot verify an account.
Creation itself does not call SearchAPI. The default request lifetime is 15 minutes.

Save the code immediately. A later GET cannot recover it. Creating the same active
request again gives `409`; creation-response replay is not implemented.

### Inspect

Use **GET /api/v1/verifications/{id}**, replacing `id` with `requestId`.
This reads status without fetching the social profile. `PENDING` means the request
is waiting for successful verification. The code is not part of this response.

### Verify

After saving the bio, use **POST /api/v1/verifications/{id}/verify** with the same
request UUID. It has **no request body**, including no empty `{}`.

This sends a real request to SearchAPI when eligible and consumes provider usage.
Swagger does not simulate the operation. A private profile may verify if the
provider supplies its username and bio. On `200`, save `account.accountId` and
check `account.active`. Validity defaults to one calendar year.

Mismatches 1–9 leave the request pending; mismatch 10 locks it. Provider failures
do not consume mismatch attempts. An already verified request returns its existing
association without fetching again or extending validity.

### Read, cancel, or revoke

| Operation | ID to use | Effect |
| --- | --- | --- |
| GET `/api/v1/social-identities/{id}` | `account.accountId` | Read the association; inspect `active` |
| DELETE `/api/v1/verifications/{id}` | `requestId` | Cancel unfinished verification; terminal requests stay unchanged |
| DELETE `/api/v1/social-identities/{id}` | `account.accountId` | Revoke the association while retaining history |

Both DELETE operations have no body. Canceling a request does not revoke an account.
Mutations reject `Idempotency-Key` headers. The consuming backend must authorize
which users can access each subject and resource inside its tenant.

## 6. Understand errors

Expand an operation's **Responses** section for its documented status codes and
schemas. Runtime errors use `application/problem+json`. Handle the stable `code`
field, not the wording of `detail`.

| Status | Common meaning / next action |
| --- | --- |
| 400 | Correct invalid fields, unknown JSON properties, UUIDs, or an unexpected body |
| 401 | Authorize with a valid, unexpired, unrevoked tenant key |
| 404 | Resource is missing or belongs to another tenant |
| 409 | Inspect `code`: duplicate request, running attempt, locked/canceled request, or conflicting account link |
| 410 | Verification expired; create a new request |
| 422 | Bio code absent, unsupported platform, or unusable/unavailable profile |
| 429 | Rate or active-request limit; honor `Retry-After` when supplied |
| 502 | Provider credentials or returned data need attention |
| 503 | Provider, authentication storage, or application storage temporarily unavailable |
| 504 | Provider timed out |
| 500 | Unexpected server failure; raw exception details are deliberately omitted |

Provider errors contain `providerFailure.reason`, `retryable`, and sometimes
`retryAfterSeconds`. Read current status before retrying a mutation after an
uncertain response. See the [HTTP API guide](../mst-social-server/VERIFICATION_API.md)
for the full retry semantics and configurable limits.

## 7. Disable docs

Set `MST_API_DOCS_ENABLED=false`. In Compose, apply the changed environment with:

```sh
docker compose --env-file .env.docker up -d --wait app
```

A plain `docker compose restart` does not apply changed environment variables.
For IntelliJ/JAR, restart the application with the new value. Docs routes are
then denied by security and their dynamic handlers are disabled. An unauthenticated
request may receive `401`, rather than `404`. API authentication remains required.
Use HTTPS when sending keys outside localhost. Forwarded headers and proxy path
rewrites require deployment-specific trusted proxy configuration; they are not
automatically enabled by this feature.

## 8. How the code is wired

1. **Server `pom.xml`** adds `springdoc-openapi-starter-webmvc-ui`. Springdoc inspects
   Spring MVC mappings and serves the bundled Swagger UI assets.
2. **`application.yaml`** connects `MST_API_DOCS_ENABLED` to both docs endpoints,
   limits scanning to our API controllers, and disables credential persistence and
   remote validation. No external validator receives the specification.
3. **`SecurityConfiguration`** permits GET requests to the exact documentation
   routes only when enabled. `/api/v1/**` still passes through tenant authentication.
4. **Controller annotations** supply tags, operation IDs, behavior explanations,
   and explicit success response types. Hidden principal parameters cannot be
   supplied by API callers.
5. **DTO `@Schema` annotations** explain fields, examples, required properties,
   and nullable timestamps. Requests stay classes; response DTOs remain records.
   These annotations document validation; they do not replace existing validators.
6. **`OpenApiConfiguration`** provides API metadata, the Bearer security scheme,
   and shared error responses/headers. It documents errors built by the existing
   HTTP mapper, exception advice, and authentication filter.

Swagger dependencies stay in the server module. No migrations, persistence changes,
or changes to the framework-independent core are needed for this feature.

## 9. Keep the importable contract current

The Java annotations and OpenAPI configuration generate the live contract.
`mst-social-server/openapi.yaml` is its checked-in export for offline consumers.
After changing routes, DTOs, or response mappings, update their documentation,
build with tests skipped, and restart the docs-enabled server. From the repository root:

```sh
curl --fail --silent --show-error http://localhost:8080/v3/api-docs.yaml \
  -o mst-social-server/openapi.yaml
git diff -- mst-social-server/openapi.yaml
```

Export from the default context path (`/`) so the committed contract remains portable.
The export contains schemas and illustrative examples, not database records or keys.
Do not hand-edit the generated YAML as the sole way to document a code change.
Import the YAML into Postman, or use the existing
[Postman collection and walkthrough](../mst-social-server/POSTMAN_GUIDE.md).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Docs return 401 | Enable `MST_API_DOCS_ENABLED` and recreate/restart the application |
| UI opens but cannot load definition | Check `/v3/api-docs` and `/v3/api-docs/swagger-config`; inspect server logs and proxy routing |
| Execute returns 401 | Paste the full tenant key without adding `Bearer `; check key expiry/revocation |
| UI still shows old endpoints | Rebuild, recreate the app container, and reload the browser |
| Browser connection refused | Wait for healthy startup, then verify the published port in `docker compose ... ps` |
| 409 during repeated creation | An active request exists; use its ID, cancel it, or wait for expiry |
| 422 after editing the bio | Inspect `code` and provider details; the provider may not yet supply the updated bio |

Dependency and configuration reference: [official springdoc documentation](https://springdoc.org/).

## What was checked

See the [verification log](VERIFICATION_LOG.md#2026-10-18--swagger-ui-and-openapi)
for the HTTP and schema checks performed on the documentation endpoints, and for
what was not exercised.
