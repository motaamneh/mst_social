# Security policy

MST Social verifies control of social accounts and handles tenant credentials and
HMAC secrets. Security reports are welcome and taken seriously.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:

1. Go to the repository's **Security** tab.
2. Click **Report a vulnerability**.
3. Describe the issue, the impact, and how to reproduce it.

This creates a private advisory visible only to the maintainers.

Helpful details to include:

- The affected component (core, provider adapter, server, migration, or documentation).
- A minimal reproduction, including request shape and configuration.
- The impact you believe it has, such as cross-tenant access or credential disclosure.
- The commit or release you tested.

Please do not include real credentials, real API keys, or another person's personal
data in a report. Redact them, or describe their format instead.

## What to expect

This is a pre-1.0 project maintained in spare time. Realistic expectations:

| Stage | Target |
| --- | --- |
| Acknowledgement | Within 7 days |
| Initial assessment | Within 14 days |
| Fix or documented mitigation | Depends on severity and complexity |

Severe issues such as cross-tenant data access, authentication bypass, or
credential disclosure are prioritized over everything else. If a report is valid
and you would like credit in the advisory, say so and it will be included.

## Supported versions

No stable release has been published yet. Only the latest commit on `main` is
supported. There are no backported security fixes for older commits.

## Scope

### In scope

- Cross-tenant data access or tenant isolation bypass.
- Tenant API-key authentication bypass, forgery, or credential disclosure.
- Verification-code disclosure through any response, log, metric, or error path.
- Accepting caller-supplied evidence (a biography, code, or provider account ID)
  as proof of control.
- Server-side request forgery through handles or provider configuration.
- SQL injection or unsafe dynamic query construction.
- Concurrency defects that allow one verification to be consumed twice, or allow
  one social account to be actively linked to two subjects within a tenant.
- Secrets leaking into logs, error responses, exception messages, or the built image.
- Migration defects that weaken a uniqueness or isolation constraint.

### Out of scope

These are either known design boundaries or outside this project's control:

- **A user voluntarily sharing their verification code with someone else.** The
  method proves that whoever completed the challenge could edit the profile at
  that moment. It cannot distinguish the account owner from a person they chose
  to hand the code to. This is inherent to bio-challenge verification.
- **Handle reuse after a rename.** With the current provider, no stable platform
  account ID is available, so associations are keyed on a mutable handle. See
  [Limitations](README.md#limitations).
- **Verification not being permanent.** An association records control at a point
  in time. It is not continuously re-checked.
- Vulnerabilities in SearchAPI, PostgreSQL, Spring Boot, or other dependencies.
  Report those upstream; report to us only if this project uses them unsafely.
- Social platform rate limits, availability, or terms changes.
- Missing hardening that is already documented as deferred, such as absent
  metrics, circuit breakers, or IP-level throttling. These are tracked in the
  [roadmap](README.md#status), not treated as vulnerabilities.
- Findings that require administrative database access, which is already the
  trust level required to provision a tenant.

## Security model

Properties the implementation intends to hold. A deviation from any of these is
worth reporting:

- Verification codes are generated with `SecureRandom`, and only an HMAC-SHA-256
  digest is persisted. Plaintext is returned exactly once, at creation.
- Digest comparison uses `MessageDigest.isEqual`.
- Tenant API keys are stored only as HMAC-SHA-256 digests with a dedicated
  server-side key. The plaintext credential cannot be recovered from the database.
- `tenantId` is resolved from the authenticated credential and never read from a
  request body, query string, or header.
- Resources belonging to another tenant return `404`, the same as missing resources.
- The server obtains evidence itself. No endpoint accepts a caller-supplied
  biography, code, or provider account ID.
- Full biographies are never persisted, and codes, credentials, handles, subject
  IDs, and raw provider payloads are excluded from logs. See the
  [logging guide](docs/LOGGING_GUIDE.md).
- Outbound provider requests go only to a fixed, allowlisted HTTPS host with
  redirects disabled, bounded response size, and bounded timeouts.
- Concurrency invariants are enforced by PostgreSQL constraints in addition to
  application logic.

Note that these properties are currently supported by code review and manual
checks rather than an automated test suite. See the
[verification log](docs/VERIFICATION_LOG.md) for exactly what has and has not
been exercised.

## Operator responsibilities

Deployments are responsible for their own security posture, including TLS,
secret storage and rotation, database access control, backups, reverse-proxy
request and connection limits, IP-level throttling, and log retention and access.
Review current platform terms and applicable privacy law for your use.
