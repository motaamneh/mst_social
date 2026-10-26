# MST Social

**Verify that someone controls an Instagram or TikTok account — without OAuth, app review, or asking for their password.**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791.svg)](https://www.postgresql.org/)

An open-source, self-hostable engine for social-account control verification.
It issues a short-lived code, the user places it in their profile bio, and the
server independently fetches the public profile to confirm the match.

## The problem

You are building an app and you want users to link their social accounts.

Your options today are bad:

- **Ask for their username only.** Anyone can type anyone's handle. This proves nothing.
- **Ask for their password.** Nobody should do this, and users rightly refuse.
- **Use the platform's OAuth.** The correct answer when you can get it — but Instagram
  and TikTok require app registration and app review, often a business or creator
  account, and weeks of waiting. Instagram's Basic Display API was deprecated in
  late 2024. Many small teams and solo developers simply do not qualify, or cannot
  wait.

MST Social is for the case where OAuth is unavailable to you. It needs **no platform
approval, no app review, and no social password** — only a username the user can
prove they control.

## How it works

```
 1. Your backend                            2. MST Social
    POST /api/v1/verifications  ──────────▶  generates a one-time code
    { subjectId, platform, handle }          stores only its HMAC-SHA-256 digest
                                ◀──────────  returns the code exactly once

 3. Your user
    pastes the code into their
    Instagram or TikTok bio

 4. Your backend                            5. MST Social
    POST /verifications/{id}/verify ──────▶  fetches the public profile
    (no body — you cannot submit              through a provider adapter
     the bio yourself)                        compares digests in constant time
                                ◀──────────  VERIFIED + a linked account record

 6. Your user removes the code. The association remains until it expires or is revoked.
```

The default code lifetime is 15 minutes. Associations are valid for one calendar
year by default. The plaintext code is returned once at creation and is never
recoverable afterwards — not from a status read, not from the database, not from logs.

<!-- TODO: add demo GIF here before launch -->

## What this proves — and what it does not

Being precise about this matters more than the feature list.

**It proves:** whoever completed the challenge could edit that profile's bio at
the moment of verification.

**It does not prove:**

- Legal or real-world identity.
- Permanent ownership, or that the same person still controls the account later.
- That the account owner did it personally rather than handing the code to someone else.

The user never gives MST Social a social-media password, session cookie, or OAuth
token. There is no scraping engine, no CAPTCHA solving, no proxy rotation, and no
browser automation in this repository.

## Quickstart

With Docker Desktop running:

```sh
cp -n docker.env.example .env.docker
chmod 600 .env.docker
```

Fill in the blank secrets in `.env.docker` (generate each with `openssl rand -hex 32`),
then start the API and PostgreSQL:

```sh
docker compose --env-file .env.docker up --build -d --wait
```

Create a tenant and issue its first API key in one command. Run it interactively —
the one-time credential is printed only to a real console:

```sh
docker compose --env-file .env.docker exec app \
  java -jar /app/app.jar tenant create --name="My app"
```

It prints the tenant ID, the credential ID, and the credential itself. Save the whole
`mst_api_...` line: it is shown once, and the database stores only a digest. Create
one tenant per consuming application, not one per user. To add further keys to that
tenant later, use `tenant-key create --tenant-id=<uuid> --name=<name>`.

Then open **Swagger UI** at <http://localhost:8080/swagger-ui.html>, click
**Authorize**, paste the key, and run the flow. No local Java, Maven, or PostgreSQL
installation is needed for this path. Flyway creates the tables.

SearchAPI's free tier includes 100 lookups, which is enough to evaluate the project.
Creating a verification makes **no** provider call; only `verify` does.

Full walkthroughs: [Docker](docs/DOCKER_GUIDE.md) ·
[IntelliJ](docs/LOCAL_RUN_GUIDE.md) · [Swagger](docs/SWAGGER_GUIDE.md) ·
[Postman](mst-social-server/POSTMAN_GUIDE.md)

## The API

Seven operations under `/api/v1`, all requiring `Authorization: Bearer <tenant-api-key>`.

| Method and path | Purpose |
| --- | --- |
| `GET /tenant` | Inspect the authenticated tenant and credential |
| `POST /verifications` | Create a request; returns the code once |
| `GET /verifications/{id}` | Read status — never the code |
| `POST /verifications/{id}/verify` | Fetch evidence and check it |
| `DELETE /verifications/{id}` | Cancel an unfinished request |
| `GET /social-identities/{id}` | Read a verified association |
| `DELETE /social-identities/{id}` | Revoke an association, retaining history |

Errors use RFC 9457 `application/problem+json` with stable machine-readable `code`
values. Every response carries an `X-Request-Id` for log correlation.

See the [HTTP API guide](mst-social-server/VERIFICATION_API.md) for request shapes,
every error code, retry semantics, and configurable limits, or import the
[OpenAPI contract](mst-social-server/openapi.yaml).

## Architecture

The design assumption is that **the challenge protocol is the stable product and
social-platform retrieval is a replaceable, failure-prone, policy-sensitive adapter.**
Everything below follows from that.

**Hexagonal, with a framework-free core.** `mst-social-core` depends on the Java
standard library and nothing else — no Spring, no JPA, no Jackson, no HTTP client.
Providers and persistence are ports implemented by outer modules, so a new evidence
source is a new adapter rather than a change to the domain.

**No network calls inside database transactions.** Verification runs in three phases:
a claim transaction that checks state and takes a lease, the provider fetch with the
transaction closed, then a completion transaction that re-reads, re-validates expiry
and lease ownership, and commits the outcome. Holding a transaction open across an
external HTTP call is explicitly rejected by the storage adapter.

**Lease-based attempt claiming.** Each attempt takes a random lease ID with a deadline
capped at request expiry. Completion must present a live lease, so a slow response that
lost its lease returns `STALE_ATTEMPT` instead of corrupting state. An expired lease is
reclaimed by the next verify call, so no background worker is required.

**Concurrency enforced by the database, not just by Java.** PostgreSQL exclusion
constraints over timestamp ranges (`btree_gist`) make overlapping active validity
intervals for a handle or a stable account ID structurally impossible within a tenant.
Deferred constraint triggers require every `VERIFIED` request to have a compatible
account link by commit time. Composite foreign keys prevent cross-tenant links.
Revocation shortens the reserved interval; natural expiry frees it with no cleanup job.

**Secrets are digests.** Verification codes are stored as HMAC-SHA-256 digests with a
versioned key, never in plaintext. Tenant API keys are stored the same way under a
separate server secret. Comparison is constant-time.

**Tenant isolation by construction.** `tenantId` comes from the authenticated
credential and is never read from a request body. It appears in every repository
predicate and uniqueness constraint. Another tenant's resource returns `404`,
indistinguishable from one that does not exist.

Implementation detail lives in the [core README](mst-social-core/README.md) and the
[server README](mst-social-server/README.md).

## Limitations

Known and deliberate. Read these before depending on the project.

**Handle reuse after a rename.** SearchAPI does not supply a stable platform account
ID, so `providerAccountId` is `null` and associations are keyed on the handle, which
is mutable. If a user verifies `@alice`, renames to `@alice2`, and a different person
registers `@alice`, the stored association still reads as active for a handle someone
else now controls. Treat an association as evidence from its `verifiedAt` timestamp,
not as a current claim, and re-verify when your risk model requires it.

**Verification is point-in-time.** Nothing is re-checked automatically. Continuous
monitoring would mean repeated paid provider calls.

**A shared code is a valid code.** If a user gives their code to someone else, that
person can complete the challenge. This is inherent to the method.

**One provider today.** SearchAPI is a third-party data provider, not an official
Instagram or TikTok API. Its availability, pricing, and terms can change. The
provider SPI exists so it can be replaced; validate current terms for your deployment.

**X and Facebook are not supported.** Both are recognized platform values that return
`UNSUPPORTED_PLATFORM`. No engine is invented for them.

**No automated test suite yet.** Correctness currently rests on code review and
documented manual checks. See the [verification log](docs/VERIFICATION_LOG.md) for
precisely what has and has not been exercised.

## Status

Working and manually verified: the core challenge workflow, PostgreSQL persistence
with Flyway migrations V1–V4, tenant API-key authentication, all seven endpoints,
rate limiting, strict JSON parsing, structured redacted logging, Swagger UI and
OpenAPI, and Docker Compose packaging.

Deliberately deferred:

| Area | Notes |
| --- | --- |
| Automated tests | Planned next: code-matching boundaries, handle normalization, state machine, provider failure mapping |
| Continuous integration | Not configured |
| Metrics | No Micrometer instrumentation yet; health is aggregate only |
| Resilience | No circuit breaker or retry policy; one verify is one lookup |
| Idempotency replay | `Idempotency-Key` is explicitly rejected with `400` |
| Webhooks | Not implemented; an outbox would come first |
| Published images | Build locally as `mst-social:local` |
| Additional providers | Manual-review and official-API adapters are designed for, not built |

## Modules

| Module | Responsibility | Artifact |
| --- | --- | --- |
| [`mst-social-core`](mst-social-core/README.md) | Plain Java domain, use cases, and provider/storage ports | Library JAR |
| [`mst-social-provider-searchapi`](mst-social-provider-searchapi/README.md) | SearchAPI adapter for Instagram and TikTok | Library JAR |
| [`mst-social-server`](mst-social-server/README.md) | Spring Boot HTTP service, auth, and PostgreSQL persistence | Executable JAR |

```text
server -> core
server -> provider-searchapi -> core
core   -> Java standard library only
```

The root `pom.xml` is the parent and reactor aggregator, centralizing Java 21 and
dependency versions. Library modules do not inherit Spring runtime dependencies;
Boot packaging is enabled only in the server. Maven coordinates use `com.motaamneh`,
and Java packages begin with `com.motaamneh.mstsocial`.

Request DTOs, domain values, configuration, and internal data holders are ordinary
Java classes. Only HTTP response DTOs under the server's `web/dto/response` package
use records. The core remains independent of Spring, Jackson, and Lombok.

## Build

Install JDK 21, then from the repository root:

```sh
./mvnw -Dmaven.test.skip=true clean package
```

Artifacts land in each module's `target/` directory. To build only the server and
what it needs:

```sh
./mvnw -Dmaven.test.skip=true -pl mst-social-server -am package
```

In IntelliJ IDEA, open the root `pom.xml` as a Maven project, or choose
**Reload All Maven Projects** if it is already open.

## Documentation

| Guide | Covers |
| --- | --- |
| [Docker guide](docs/DOCKER_GUIDE.md) | Containers from first principles, Compose, secrets, backups |
| [Local run guide](docs/LOCAL_RUN_GUIDE.md) | IntelliJ, PostgreSQL, tenant creation, Bearer auth |
| [Swagger guide](docs/SWAGGER_GUIDE.md) | Interactive docs, authorization, keeping the contract current |
| [Logging guide](docs/LOGGING_GUIDE.md) | Request correlation, event names, 401 reasons, redaction |
| [Postman guide](mst-social-server/POSTMAN_GUIDE.md) | End-to-end walkthrough with an importable collection |
| [HTTP API guide](mst-social-server/VERIFICATION_API.md) | Full contract, error codes, limits, retry semantics |
| [Verification log](docs/VERIFICATION_LOG.md) | What was manually checked, when, and what was not |

## Security

Please report vulnerabilities privately through GitHub's **Report a vulnerability**
button rather than a public issue. See [SECURITY.md](SECURITY.md) for scope, the
intended security model, and known design boundaries.

Keep the tenant API key on your backend. In Next.js, use a server-only environment
variable, never `NEXT_PUBLIC_*`. Browser and mobile clients should call your own
backend, which calls MST Social.

## License

[Apache License 2.0](LICENSE).
