# Core verification models

This module uses Java 21 and the Java standard library. Its package is
`com.motaamneh.mstsocial.core.model`.

## Types

| Type | Responsibility |
| --- | --- |
| `Platform` | Known platform identifiers; enum membership does not enable a provider |
| `VerificationStatus` | Request lifecycle and terminal-state classification |
| `VerificationPolicy` | Positive request lifetime, attempt limit, account validity in years, and lease duration |
| `VerificationRequest` | Temporary request, digest, attempt accounting, and controlled state transitions |
| `VerifiedSocialAccount` | Verified account association, validity deadline, and revocation |

Default policy: a 15-minute request, ten mismatches, one calendar year of account
validity, and a one-minute verification lease.
The policy accessors are `requestLifetime()`, `maxFailedAttempts()`,
`verifiedAccountLifetimeYears()`, and `verificationLeaseDuration()`.

## Verification request

Construct a request with caller-generated IDs, a trusted tenant and subject,
an already-normalized handle, a 32-byte digest, its key version, a creation
timestamp, and a policy. The request starts as `PENDING` with zero failed
attempts and version zero. It captures its deadline and attempt limit at creation.
The digest is defensively copied on input and output; no plaintext code is stored.

| Operation | Required state | Result |
| --- | --- | --- |
| `beginVerification(leaseId, duration, now)` | `PENDING`, or `VERIFYING` with expired lease | `VERIFYING` with a fresh lease |
| `markVerified(leaseId, now)` | Own a live lease | `VERIFIED`, with `verifiedAt` recorded |
| `recordMismatch(leaseId, now)` | Own a live lease | Increment failed attempts; return to `PENDING`, or become `LOCKED` at the limit |
| `recordProviderFailure(leaseId, now)` | Own a live lease | Return to `PENDING` without consuming a mismatch attempt |
| `cancel(now)` | `PENDING` or `VERIFYING` | `CANCELED`, with `canceledAt` recorded |
| `expireIfDue(now)` | Any nonterminal state at or beyond the deadline | `EXPIRED`; returns whether state changed |

Each state change increments the object's version. Terminal states never reopen.
Timestamps must not precede creation. The expiry instant is excluded from the
valid request interval, and all attempt operations reject an expired request.
Rejected operations leave the object unchanged.

`isExpired(now)` checks effective expiration without changing state. The service
must call and persist `expireIfDue(now)` when needed; an exception from another
operation does not automatically persist expiration. A successfully verified
request remains `VERIFIED` after its original request deadline.

`getVerifiedAt()` and `getCanceledAt()` return null until the corresponding event.

## Verified account

Construct an account only after successful verification. Identity fields,
evidence provider, verification time, and validity deadline are fixed. A missing
provider account ID is represented by null; blank IDs are rejected.

Validity is calculated by adding the policy's number of calendar years to
`verifiedAt` in UTC. A February 29 anniversary becomes February 28 when the target
year is not a leap year. Reading validity never extends this deadline.

`isActive(now)` is true from `verifiedAt` inclusive to `validUntil` exclusive,
provided the association has not been revoked. `revoke(now)` records revocation
and increments the version once. Repeated revocation preserves the original
timestamp and version. Revocation may also be recorded after natural expiry.

## Handle normalization

`com.motaamneh.mstsocial.core.validation.HandleNormalizer` exposes
`normalize(Platform platform, String input)`.

| Platform | Accepted normalized length | Period rules |
| --- | --- | --- |
| Instagram | 1–30 ASCII characters | No leading, trailing, or consecutive periods |
| TikTok | 1–24 ASCII characters | No trailing period |
| X / Facebook | Unsupported | Throws `UnsupportedOperationException("UNSUPPORTED_PLATFORM")` |

These are the initial application acceptance policies, not a guarantee that a
handle exists or a complete specification of platform registration rules.
The precise length limits and Instagram period rules could not be independently
confirmed in accessible official documentation during implementation; review
these policies against provider integration evidence before release.
[TikTok's username guidance](https://support.tiktok.com/en/getting-started/setting-up-your-profile/changing-your-username)
describes letters, numbers, underscores, and periods as username characters.

The normalizer rejects raw input over 256 UTF-16 code units, rejects control
characters before trimming, strips surrounding whitespace using `String.strip()`,
and removes one optional leading `@`. It validates ASCII letters, digits,
underscores, and periods before lowercasing with `Locale.ROOT`. URLs, path
separators, repeated `@`, internal whitespace, Unicode lookalikes, query strings,
and fragments fail validation. Nothing is URL-decoded or silently repaired.

For example, `normalize(Platform.INSTAGRAM, "  @Example.User  ")` returns
`"example.user"`. `"@@example"`, `"@ example"`, and
`"https://instagram.com/example"` are rejected. A domain-shaped string that also
fits the handle grammar is treated only as a handle, never as an outbound host.

Invalid handles throw `IllegalArgumentException`; null arguments throw
`NullPointerException` (unsupported platforms are rejected before input validation).
Error messages do not include the submitted handle. Apply the same policy to
the requested and provider-returned usernames before comparing them. Providers
must still construct URLs from fixed, allowlisted hosts and encode parameters.

## Integration boundary

Callers supply timestamps from an injected application `Clock`. These mutable
objects are not thread-safe; their version counters do not implement database
locking. The service uses the storage transaction contract described below.
The server module provides a PostgreSQL storage adapter and Flyway migration.
Provider HTTP retrieval and server authentication remain future work. X and Facebook remain
identifiers awaiting support; Instagram and TikTok are the planned integrations.

## Step 6: storage contract and leases

`port.VerificationStore` is a persistence interface, not a database implementation.
`inTransaction(tenantId, callback)` gives the callback a tenant-scoped transaction.
The service explicitly inserts or saves entities. The adapter must commit all
writes, request-to-account associations, and audit events together, or roll them
all back. Callback results must only be returned after commit succeeds.

Transactions within a tenant must be serialized across all application instances.
For the first PostgreSQL adapter, locking a tenant row before accessing its
requests/accounts is a straightforward implementation. Database constraints must
also enforce identity and request-to-account uniqueness. A plain sequence of
independent repository saves does not satisfy this contract. Transaction callbacks
must not be automatically replayed or let mutable entities escape.

The same transaction checks creation quotas and inserts the new request.
`VerificationLimits.DEFAULT` allows three active requests per subject and three
per platform/handle within a tenant. These are configurable application defaults.
A duplicate active request for the same subject/platform/handle is rejected.
Active counts exclude expired deadlines and all terminal states.
These limits do not replace HTTP rate limiting for paid provider lookups.

Each attempt has a random lease ID and a lease deadline capped at request expiry.
An expired lease can be replaced by a fresh ID. Completion must present the current,
unexpired lease. Cancellation and terminal transitions clear lease ownership.
No background lease worker is required: the next verify call can reclaim an
expired lease. A late response returns `STALE_ATTEMPT` without changing the request.

## Step 7: service inputs and outputs

The `service` package contains ordinary Java records:

| Type | Purpose |
| --- | --- |
| `CreateVerificationCommand` | Subject, platform, and submitted handle |
| `VerificationCreated` | Request ID, plaintext code, expiry; code is redacted in `toString()` |
| `VerificationView` | Immutable request status, attempt counts, and timestamps |
| `VerifiedAccountView` | Immutable association details and activity at read time |
| `VerificationResult` | Outcome, request view, optional account or provider failure details |

Only creation returns the plaintext code. Do not persist or log that response.
No response contains the stored digest, key version, worker lease, or biography.
The tenant is a separate service argument supplied by the authenticated host.
The host must also authorize the subject and resource for its end user.

`VerificationException.code()` identifies missing resources, unsupported platforms,
duplicate active requests, and creation-limit failures. Malformed input uses normal
Java argument validation. Provider failures are typed results, not leaked exceptions.

## Step 8: verification service

`service.VerificationService` exposes:

| Method | Behavior |
| --- | --- |
| `createVerification(tenantId, command)` | Normalize, generate, hash, enforce quotas, insert, return code once |
| `getVerification(tenantId, requestId)` | Return status, persisting expiry when due |
| `verify(tenantId, requestId)` | Claim, fetch evidence, check it, atomically complete |
| `cancelVerification(tenantId, requestId)` | Cancel nonterminal requests; terminal requests stay unchanged |
| `getVerifiedAccount(tenantId, accountId)` | Return association and current activity |
| `revokeVerifiedAccount(tenantId, accountId)` | Revoke and append audit metadata in the same transaction |

Verification has three phases:

1. **Claim transaction:** check expiry/state and claim a lease. Existing live work
   returns `IN_PROGRESS`. Already verified requests return the original association
   without another provider call.
2. **Outside the transaction:** fetch the provider profile once; normalize and
   compare the returned handle; match the code against its HMAC digest.
3. **Completion transaction:** reread the request; check expiry, terminal state,
   and lease ownership; persist the outcome and audit event.

A provider failure, invalid returned handle, or invalid evidence timestamp consumes
no mismatch attempt. A successfully fetched matching profile without the code
consumes one attempt; the final allowed mismatch produces `LOCKED`.
Returned handles must normalize to the requested handle. Evidence explicitly dated
before request creation or after its fetch is rejected; unknown source timestamps
remain unknown. Adapters must use the application clock for fetch timestamps.
The service does not guarantee source freshness when the provider omits that time.

Expected provider failures preserve their retry metadata. A null provider result
becomes `INVALID_PROVIDER_RESPONSE`; an unexpected adapter runtime exception
becomes `PROVIDER_ERROR` with no automatic retry. Infrastructure/store failures
propagate, and programming/configuration errors outside the provider call are not
silently treated as code mismatches.

On success, all active links matching either the handle or stable platform ID are
checked. A different subject, conflicting stable ID, or multiple matching links
produces `ACCOUNT_ALREADY_LINKED` without consuming a mismatch attempt.
A single compatible active link for the same subject is reused without extending
its validity. Otherwise a new account receives one calendar year of validity by
default. Account creation, request verification, their association, and audit
metadata commit together. Full biographies are never passed into storage.

Revocation is idempotent. Re-verifying an already successful request cannot reactivate
a revoked or expired association; the returned account view has `active=false`.
A new verification request is required.

### Wiring the service

The constructor receives a store adapter, profile provider, code generator, hasher,
handle normalizer, verification policy, creation limits, clock, and active HMAC key
version. Keep older HMAC keys configured until their pending requests expire.

```java
var service = new VerificationService(
        store, provider, generator, hasher, new HandleNormalizer(),
        VerificationPolicy.DEFAULT, VerificationLimits.DEFAULT,
        Clock.systemUTC(), activeKeyVersion);

var created = service.createVerification(
        authenticatedTenantId,
        new CreateVerificationCommand(subjectId, Platform.INSTAGRAM, handle));

// Deliver created.code() privately to the user for placement in their bio.
// After the user explicitly asks to verify:
var result = service.verify(authenticatedTenantId, created.requestId());
```

The server's `PostgresVerificationStore` implements the storage port. Running this
example also requires a configured `ProfileProvider`; SearchAPI HTTP integration
and controllers remain future work. See the [database setup guide](../mst-social-server/README.md).

## Restoring saved models

Both domain models provide `snapshot()` and a static `restore(snapshot)` factory.
Each nested `Snapshot` record belongs to one model: requests contain attempts,
digests, and leases; accounts contain validity and revocation. Their distinct
fields and validation rules are not combined into a generic record.

Normal constructors create new state. Restoration preserves the saved ID, status,
version, and deadlines without replaying transitions or recalculating validity
from today's policy. Snapshots validate state consistency, including terminal
timestamps, attempt bounds, and lease presence. An expired lease is legitimate
saved state and can be reclaimed after restoration.

The request snapshot clones digest bytes both on construction and access.
Both snapshots redact their `toString()` output. They are persistence values,
not service response objects; do not expose them through HTTP endpoints.

## Compile

From the repository root:

```sh
./mvnw -pl mst-social-core -am -Dmaven.test.skip=true compile
```

This compiles production sources without compiling or running tests.
