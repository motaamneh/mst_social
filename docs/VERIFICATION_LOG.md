# Verification log

This file records what was actually checked during implementation, when, and what
was **not** checked. It is kept separate from the setup guides so those guides stay
focused on instructions, and so this record stays easy to audit in one place.

Entries are append-only. Do not edit a past entry to make it look better; add a
new entry instead.

## Current testing status

| Area | Status |
| --- | --- |
| Automated unit tests | None. No `src/test` directory exists in any module. |
| Automated integration tests | None. |
| Provider contract tests | None. Failure mapping is reviewed in code, not asserted. |
| Concurrency tests | None. Concurrent verification is enforced by database constraints that have not been exercised under contention. |
| Continuous integration | Not configured. |
| Manual checks | Recorded per feature below. |

All builds to date use `-Dmaven.test.skip=true`, which skips both test compilation
and test execution. Automated tests, provider contract tests, and a concurrency
test are planned; see the roadmap in the [README](../README.md#status).

## 2026-10-19 — Request logging

Maven packaging and the Docker build passed with `-Dmaven.test.skip=true`.

Manual requests against a disposable isolated database covered missing credentials
(401), valid credentials (200), creation (201), invalid input (400), status reads
(200), cancellation (200), and attempting an already canceled verification (409).
The last operation made no provider call.

Inspected JSON/DEBUG and text/INFO output, response/log request-ID correlation,
ignored caller-supplied correlation IDs, and absence of tenant context on a later
unauthenticated request. The checked log did not contain the temporary credential,
returned verification code, submitted handle, or subject ID.

**Not checked:** no automated test files were created and no automated tests ran.
No live SearchAPI call or forced database outage was used, so provider and outage
log paths were reviewed in code rather than exercised against those external services.

## 2026-10-18 — Swagger UI and OpenAPI

Checked using Java 21 and an isolated Docker Compose database:

- Maven packaging and the Docker image build succeeded with `-Dmaven.test.skip=true`.
- Swagger's entry route redirected to its UI; the UI, JavaScript assets, configuration,
  and OpenAPI JSON/YAML endpoints responded successfully.
- The generated contract contained seven operations and nine DTO schemas, with all
  schema references resolved and no principal or tenant-selector request parameters.
- Swagger configuration reported authorization persistence disabled and no remote validator.
- API calls without a tenant credential returned `401`.
- With docs disabled, all checked documentation routes/assets returned `401`, while
  health still returned `200`.

**Not checked:** no automated tests were written or run, and no SearchAPI requests
were made. These were HTTP and schema checks; browser rendering and an authenticated
Swagger Execute flow were not exercised. The temporary Compose stack and its
disposable volume were removed.

## 2026-10-18 — Docker packaging and Compose

Using Docker Desktop on Apple Silicon (Linux ARM64 containers):

- Built `mst-social:local` from source; all Maven modules packaged successfully,
  with test compilation and execution skipped.
- Validated the Compose configuration without printing environment values.
- Started an isolated project with temporary internal secrets and a dummy provider
  key; both services became healthy.
- Confirmed the health endpoint was reachable through its localhost port mapping.
- Confirmed PostgreSQL applied migrations V1–V4 successfully.
- Confirmed the application process used non-root UID/GID 10001.
- Created a tenant in the isolated database and generated a credential through an
  interactive `docker compose exec app` session.
- Removed and recreated the containers while retaining the volume; the tenant and
  credential metadata survived.
- Removed the isolated check containers, network, and temporary database volume.
  The built application image remains available locally.

**Not checked:** no automated test files were added or executed, and no live
SearchAPI requests were made. These checks cover container build/startup,
provisioning, and persistence; they do not establish successful live bio
verification or AMD64 runtime behavior.

The optional backup and restore instructions in the Docker guide were reviewed
against PostgreSQL documentation but were not executed during these checks.

## 2026-10-17 — Verification API startup

The verification API branch builds with
`./mvnw -B -o -Dmaven.test.skip=true package`. Its first local startup attempt
stopped because PostgreSQL was not listening on `localhost:5432`; runtime API
checks require that database to be started.

## 2026-10-13 — Tenant API-key authentication

Local runtime checks confirmed successful Flyway migrations through V3, aggregate
health returning HTTP 200, and the identity endpoint returning the correct tenant
and credential IDs with HTTP 200. Missing credentials, an altered secret, and a
revoked credential returned HTTP 401. Both local credential creation and revocation
succeeded, and revocation took effect without restarting the server.

**Not checked:** no automated tests were added or run, and no live provider calls
were made.

## Provider adapter

No tests or live API calls were run against SearchAPI during implementation of the
provider adapter. Provider error-payload semantics and runtime behavior still need
validation before release. The failure-to-result mapping documented in the
[provider README](../mst-social-provider-searchapi/README.md#failure-mapping)
reflects intended behavior reviewed in code, not observed provider responses.

## Handle normalization policy

The Instagram and TikTok handle length limits and the Instagram period rules
implemented in `HandleNormalizer` could not be independently confirmed in
accessible official platform documentation. They are application acceptance
policies, not verified platform registration rules. Review them against provider
integration evidence before relying on them. See the
[core README](../mst-social-core/README.md#handle-normalization).
