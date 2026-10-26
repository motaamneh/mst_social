# MST Social server

The server wires the plain Java verification service to SearchAPI and PostgreSQL.
It authenticates application backends with tenant API keys and exposes verification
and verified-account endpoints. See the [API walkthrough](VERIFICATION_API.md) and
[OpenAPI contract](openapi.yaml) for request bodies, responses, and error codes.

**Running locally in IntelliJ?** Follow the [local run guide](../docs/LOCAL_RUN_GUIDE.md)
for database setup, tenant creation, Terminal environment settings, and Postman Bearer authentication.

**Want the full API walkthrough?** Follow [Start to finish: tenant setup and Postman](POSTMAN_GUIDE.md).
It explains how to get your tenant ID and API key, and includes a ready-to-import
[collection](postman/MST-Social.postman_collection.json) and
[environment template](postman/MST-Social-Local.postman_environment.json).

**Running with Docker?** Use the [Docker beginner guide](../docs/DOCKER_GUIDE.md).
It starts the API and a separate PostgreSQL database, applies the same migrations,
and shows how to run tenant provisioning commands inside the containers. The setup
below is for a local JVM/IDE connected to an existing database.

## Swagger UI

Set `MST_API_DOCS_ENABLED=true` and restart the application, then open
<http://localhost:8080/swagger-ui.html>. Compose enables it by default for local use.
API operations still require a tenant Bearer key. See the
[complete Swagger guide](../docs/SWAGGER_GUIDE.md) for the walkthrough and code wiring.
Live OpenAPI: `/v3/api-docs` (JSON) and `/v3/api-docs.yaml` (YAML).
The checked-in `openapi.yaml` is an export of the generated definition.

## Request logging

See the [logging guide](../docs/LOGGING_GUIDE.md) for request correlation, authentication
failure reasons, verification/provider events, log levels, and JSON output. Set
`MST_LOG_LEVEL=DEBUG` in IntelliJ and restart for detailed troubleshooting.

## Connect your existing database

The application defaults to `jdbc:postgresql://localhost:5432/mst_social_db`.
Set these environment variables in your IntelliJ run configuration or shell:

Use the committed [`.env.example`](../.env.example) as the configuration template.
Copy it to `.env` for local values or copy its variable names into IntelliJ.
For Docker Compose, use [`docker.env.example`](../docker.env.example) instead.

| Variable | Value |
| --- | --- |
| `MST_DB_URL` | JDBC URL for your actual host, port, and database |
| `MST_DB_USERNAME` | PostgreSQL role that can connect and migrate the schema |
| `MST_DB_PASSWORD` | Password supplied locally, never committed |

Spring Boot does not load `.env` by itself. Use your IDE's environment settings or
export variables before starting the app.
An empty password is allowed only if explicitly configured and your database permits it.
For a hosted database, use its required TLS JDBC connection settings.

The HTTP application also requires:

| Variable | Purpose |
| --- | --- |
| `SEARCHAPI_API_KEY` | Provider credential for Instagram and TikTok lookups |
| `MST_VERIFICATION_KEY_V1` | 32 random bytes encoded as 64 hexadecimal characters, used for verification codes |
| `MST_TENANT_API_KEY_HASH_KEY` | A separate 32-byte random secret encoded as 64 hexadecimal characters, used for tenant API-key digests |

Generate each internal secret separately with `openssl rand -hex 32`, then store
it in your local environment or deployment secret manager. Keep these secrets
stable across restarts and consistent across instances. Do not reuse a SearchAPI
key or a verification-code key as the tenant-key hash secret.

Changing `MST_TENANT_API_KEY_HASH_KEY` invalidates existing tenant credentials.
Routine credential rotation uses the create/revoke commands below and leaves this
server secret unchanged. Retain it securely when backing up the database.

Start `MstSocialApplication` from the IDE after setting the environment, or build
and run from the repository root:

```sh
./mvnw -Dmaven.test.skip=true package
java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar
```

This build skips both test compilation and execution. Application startup connects
to the database and applies Flyway migrations. It does not create the database itself.
Flyway clean is disabled; Hibernate and Spring SQL initialization do not create tables.
Do not manually apply the SQL file, enable Hibernate schema updates, or enable Flyway
baseline to conceal an unexpected existing schema.

The first migration installs `btree_gist`. If your role cannot install it, have your
database administrator install that extension in `mst_social_db` before startup.
The role must also be allowed to create tables, indexes, functions, and triggers in
the target schema. Production deployments may use separate migration/runtime roles.

## Tables created by V1

| Table | Purpose |
| --- | --- |
| `tenants` | Application identities and transaction lock rows |
| `verification_requests` | HMAC digest, state, attempts, saved deadlines, and leases |
| `verified_accounts` | Verified association, validity, revocation, and version |
| `verification_account_links` | Immutable request-to-account association |
| `verification_audit_events` | Minimal event metadata; no code or biography |
| `flyway_schema_history` | Migration history managed by Flyway |

Tenants are not PostgreSQL usernames or end users. Provision a tenant explicitly
after migration. The `tenant create` command below inserts the row and issues its
first credential in one step. V2 also generates the UUID automatically if you insert
the row yourself:

```sql
INSERT INTO tenants DEFAULT VALUES RETURNING id;
```

Keep the returned ID for that application's tenant; do not create a new tenant for
each verification. Request IDs, account IDs, and lease IDs are generated by the
core service. Audit-event IDs are database-generated incrementing integers.
Subject IDs come from the consuming application's users; foreign keys reference
existing IDs and must not generate new ones.

The store rejects unknown tenants; an arbitrary service call does not create one.
HTTP tenant identity comes from the validated credential. The consuming application
must still authenticate its own users and decide which subject IDs they may use.

## Tenant API-key authentication

`GET /api/v1/tenant` ("Who am I" in Postman) returns the application tenant and
credential IDs for the key you send. It does not log in a social user or issue a key.

| Value | Purpose | Use as the API Bearer token? |
| --- | --- | --- |
| Full `mst_api_...` credential from `tenant create` or `tenant-key create` | Authenticate your application backend | Yes |
| `SEARCHAPI_API_KEY` | Let this server call SearchAPI | No |
| `MST_TENANT_API_KEY_HASH_KEY` | Server secret for hashing tenant credentials | No |
| `MST_VERIFICATION_KEY_V1` | Server secret for verification codes | No |
| Tenant UUID / credential UUID | Identify database records | No |

Follow steps 1–4 below to generate the Bearer credential. The CLI must connect to
the **same database** and use the **same tenant hash key** as the running server.
IntelliJ Run Configuration environment variables do not automatically carry over
to Terminal. A key created in the Docker database will not exist in a separate
PostgreSQL database used by IntelliJ.

V3 creates `tenant_api_keys`. Each row contains a generated credential UUID,
the owning tenant UUID, a name, a 32-byte HMAC-SHA256 digest, creation time,
optional expiry, and optional revocation time. The secret itself is never stored.

A credential has this format:

```text
mst_api_<credential-uuid>.<64-lowercase-hex-characters>
```

The part after the dot is a cryptographically random 32-byte secret. The UUID
selects the database row; knowing it alone does not authenticate a caller. The
codec calculates HMAC-SHA256 over the decoded secret bytes using the dedicated
server hash key. Authentication compares the stored and presented digests with
`MessageDigest.isEqual` and checks the credential's lifetime.

### 1. Build and migrate

Set the environment variables above, build the JAR, and start the server with the
commands in the setup section. Flyway applies all pending migrations automatically,
including V3 for credentials and V4 for API rate limits. Keep previously applied
migrations unchanged. The local administration command also initializes
Flyway before performing its operation.

### 2. Choose a tenant

The tenant represents a consuming application, such as your Next.js backend.
One tenant can have several credentials, so staging, deployment, and rotation
do not require sharing one key.

For a new application, skip ahead: step 3 creates the tenant and its first
credential together. To reuse an existing tenant, take its UUID from your database
console or from `GET /api/v1/tenant` with a working key. You can also insert the
row on its own:

```sql
INSERT INTO tenants DEFAULT VALUES RETURNING id;
```

### 3. Create a credential locally

In an **interactive terminal**, set the three database variables and the same
`MST_TENANT_API_KEY_HASH_KEY` used by your server. Then, for a new application:

```sh
java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar tenant create \
  --name="Development backend"
```

This prints the tenant ID, the credential ID, and the one-time credential. For an
existing tenant, issue a credential for it instead:

```sh
java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar tenant-key create \
  --tenant-id="<your-tenant-uuid>" \
  --name="Development backend"
```

Replace the angle-bracket placeholder before running that command. Add
`--expires-at=2030-01-01T00:00:00Z` to schedule expiry; choose a date in the future.
Without it, the credential stays active until revoked. PostgreSQL timestamp
precision is one microsecond; provisioning normalizes timestamps to that precision.

The command prints the credential ID and the full credential **once**, after its
database insert succeeds. Save it immediately in your backend's secret storage.
It cannot be retrieved from the database later. Creation refuses to run without
an interactive console, which prevents accidentally redirecting the secret into
ordinary command logs. Run this command in Terminal, rather than an IDE Run console.

The command boots a separate Spring context with database and provisioning beans.
It opens no HTTP port, makes no provider request, and does not require
`SEARCHAPI_API_KEY` or `MST_VERIFICATION_KEY_V1`. It uses your local database access
as administrator authority. There is no public HTTP endpoint for creating keys.

### 4. Check your key in Postman

With the normal server running:

1. Create a `GET http://localhost:8080/api/v1/tenant` request.
2. Open **Authorization**, select **Bearer Token**, and paste the full credential.
3. If using the imported collection's inherited authorization, first store that
   credential in Postman Local Vault as `mst-social-api-key`. Alternatively,
   override this request's authorization with **Bearer Token** directly. Do not
   include `Bearer ` in Postman's token field or add a second Authorization header.
4. Send the request. A valid key returns HTTP `200` with:

```json
{
  "tenantId": "<the tenant UUID from the stored credential>",
  "credentialId": "<the credential UUID>"
}
```

Do not send `tenantId` in a header, query string, or body to select a tenant. This
endpoint obtains both IDs from the authenticated principal. It does not call
SearchAPI or create a verification request.

| Request condition | Result |
| --- | --- |
| Active credential with the correct secret | `200` from the identity endpoint |
| Missing, malformed, unknown, expired, revoked, or incorrect key | `401`, code `UNAUTHORIZED` |
| Duplicate Authorization headers or a different authentication scheme | `401`, code `UNAUTHORIZED` |
| Database failure during a correctly formatted credential lookup | `503`, code `AUTHENTICATION_UNAVAILABLE` |
| Anonymous `GET /actuator/health` | Aggregate health only: normally `200`, or `503` if unhealthy |

Authentication errors use `application/problem+json`, for example:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "code": "UNAUTHORIZED"
}
```

The `401` response includes `WWW-Authenticate: Bearer realm="mst-social"`.
No error response returns a credential, digest, SQL statement, or exception text.
Authentication is stateless: there is no session, generated development password,
form login, or Basic authentication. The filter reads exactly one Authorization
header, and only `Bearer <credential>` is accepted. Credentials are not accepted
from cookies or query parameters. CSRF protection is disabled for this explicit
header authentication model. Other application paths are denied; actuator exposes
only health, without component details.

Keep the tenant credential on your application **backend**. For Next.js, use a
server-only environment variable, never `NEXT_PUBLIC_...`. Browser and mobile
clients should call your own backend, which makes authenticated requests to MST
Social. Use HTTPS when sending credentials outside localhost.

### 5. Revoke or rotate a credential

To revoke a key using the IDs printed during creation:

```sh
java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar tenant-key revoke \
  --tenant-id="<your-tenant-uuid>" \
  --credential-id="<credential-uuid>"
```

The update includes both IDs, so it cannot revoke a key belonging to another
tenant. Repeating it preserves the original revocation timestamp. Authentication
reloads the row on every request, so later requests fail after revocation commits.
A request that already authenticated can still finish.

For rotation, create another key for the same tenant, update your backend to use
it, check the identity endpoint, then revoke the old key. No server restart is
needed. Multiple keys can be active during the switch.

Run `java -jar <server.jar> tenant-key --help` for usage. Exit codes are `0` for
success, `1` for an operation/startup failure, `2` for invalid input or a missing
interactive console, and `3` when revocation changed no row.

### How the classes connect

```text
HTTP request with Authorization: Bearer ...
  -> SecurityConfiguration's filter chain
  -> TenantApiKeyAuthenticationFilter reads the credential
  -> TenantAuthenticationService validates its format and secret
       -> TenantApiKeyCodec computes the HMAC digest
       -> TenantApiKeyRepository loads credential metadata using JDBC
  -> TenantPrincipal is placed in Spring Security's request context
  -> Controller reads the authenticated TenantPrincipal
       -> TenantController returns that tenant's identity, or
       -> VerificationController / VerifiedAccountController calls the core service
```

`TenantAuthenticationService` implements Spring Security's `AuthenticationManager`,
so Spring Boot backs off from creating its default username/password user.
`SecurityConfiguration` registers the filter inside the security chain exactly
once; the filter is deliberately not a Spring component or servlet-filter bean.
The authenticated token contains the principal and tenant role, with no secret.
The security context is cleared when the request finishes.

For local creation, `TenantKeyCommand` calls `TenantApiKeyProvisioningService`,
which generates metadata and a secret through the codec, then inserts the metadata
through the repository. The repository remains JDBC, and no core model becomes a
JPA entity. Verification controllers use
`@AuthenticationPrincipal TenantPrincipal` and pass `principal.tenantId()` to the
core service; their request bodies cannot choose the tenant.

## Transaction behavior

`PostgresVerificationStore` is discovered as a Spring repository and implements the
core port using JDBC. It uses the configured `DataSource` and its own explicit JDBC
transaction manager. It does not map the domain classes as JPA entities.

Each callback runs in one transaction, locks its tenant row first, and executes all
reads/writes scoped to that tenant. This serializes quota checks and account linking
across server instances. Different tenants can proceed independently. Callback or
commit failures roll back writes and audit events together. There is no automatic
callback replay.

The adapter rejects invocation inside an existing transaction: do not annotate the
entire verification workflow with `@Transactional`. The core deliberately releases
its claim transaction before making a provider call.

Loaded entities are detached copies. Explicit saves require the same instance loaded
or inserted in that callback, with a matching stored version. A callback's transaction
object cannot be reused after completion or from another thread.

Database constraints prevent overlapping validity intervals for either a platform
handle or a stable platform account ID within a tenant. Revocation shortens the
reserved interval; natural expiration allows a later association without a cleanup
job. Time intervals include their start and exclude their end. These are exclusion
constraints over PostgreSQL timestamp ranges, backed by `btree_gist`.
See [PostgreSQL range constraints](https://www.postgresql.org/docs/17/rangetypes.html#RANGETYPES-CONSTRAINT).

Deferred constraint triggers require every VERIFIED request to have a compatible
account link by commit. Composite foreign keys prevent cross-tenant links.
The adapter preserves historical account links after revocation or expiry.

PostgreSQL stores timestamp precision to microseconds. Java snapshots preserve the
exact Instants supplied to them; database round trips use PostgreSQL precision.

## HTTP DTO conventions

Only HTTP response DTOs in `web/dto/response` may be records. Request DTOs,
domain values, configuration, persistence snapshots, and internal data holders
must be ordinary Java classes. Immutable classes retain their existing accessor
names, such as `tenantId()` and `requestId()`, to preserve callers.
Server request classes can use Lombok getters, setters, and a no-argument
constructor, with Jakarta validation annotations on their fields. Controllers
must activate validation with `@Valid` and read request fields through getters
such as `getSubjectId()`.

## Scope

The core workflows, JDBC storage, SearchAPI provider, service wiring, and tenant
authentication are implemented. The HTTP API supports creating, reading, verifying,
and canceling requests and reading/revoking verified social accounts. It includes
validated request classes, safe error responses, strict JSON parsing, and shared
PostgreSQL rate limits through V4. The identity endpoint remains available for
checking authentication.

Docker packaging and local Compose startup are available through the root Dockerfile
and Compose file. Published registry images, durable creation-response replay, and
edge/IP throttling remain future work. Do not send `Idempotency-Key`: mutations explicitly reject it until
replay semantics are implemented. See the API guide for retry behavior and limits.

There is no automated test suite yet. The
[verification log](../docs/VERIFICATION_LOG.md) records the manual runtime checks
performed on authentication, migrations, health, and credential provisioning,
along with what was not exercised.
