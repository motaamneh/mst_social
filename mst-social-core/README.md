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

Default policy: a 15-minute request, five mismatches, one calendar year of account
validity, and a one-minute lease setting for the future verification workflow.
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
| `beginVerification(now)` | `PENDING` | `VERIFYING` |
| `markVerified(now)` | `VERIFYING` | `VERIFIED`, with `verifiedAt` recorded |
| `recordMismatch(now)` | `VERIFYING` | Increment failed attempts; return to `PENDING`, or become `LOCKED` at the limit |
| `recordProviderFailure(now)` | `VERIFYING` | Return to `PENDING` without consuming a mismatch attempt |
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
locking. Provider support, evidence retrieval, tenant
authorization, storage restoration, account renewal, leases, and atomic storage
completion belong to subsequent implementation steps. X and Facebook remain
identifiers awaiting a supported provider, while Instagram and TikTok are the
planned initial integrations.

## Compile

From the repository root:

```sh
./mvnw -pl mst-social-core -am -Dmaven.test.skip=true compile
```

This compiles production sources without compiling or running tests.
