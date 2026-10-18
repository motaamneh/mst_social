# Start to finish: tenant setup and Postman

This guide takes you from an empty `mst_social_db` database to a verified Instagram
or TikTok account. It matches the current server implementation.

Import these two files when you reach step 5:

- [Postman collection](postman/MST-Social.postman_collection.json)
- [Local environment template](postman/MST-Social-Local.postman_environment.json)

The collection contains manual requests, with no test or automation scripts.
Send each request individually. Do not run the entire collection: you must edit
your social bio between creation and verification, and the optional requests
cancel requests or revoke associations.

## 0. Understand the IDs and secrets

Imagine you run a Next.js application with users Alice and Bob. That application
is one **tenant**. Alice and Bob are **subjects** within that tenant.

| Name | Meaning | Where you get it |
| --- | --- | --- |
| `tenantId` | Your consuming application's identity | PostgreSQL creates it when you insert a tenant; `/api/v1/tenant` also returns it after authentication |
| `credentialId` | Identifies one API key belonging to that tenant | The local key command, or `/api/v1/tenant` |
| Tenant API key | Secret that authenticates your backend to MST Social | The local key command displays it once |
| `subjectId` | Your application's user ID, for example `user-123` | Your own application; choose a sample string for this walkthrough |
| `requestId` | One attempt to establish control of a social account | `POST /api/v1/verifications` |
| `code` | Temporary text the user places in their social bio | Creation response only |
| `accountId` | MST Social's saved verified association | Successful verify response, under `account.accountId` |

`accountId` is not the Instagram or TikTok account's internal ID. A tenant UUID
alone does not authenticate you. The UUID inside an API key is the **credential**
ID, not the tenant ID.

There are three different server secrets:

- `SEARCHAPI_API_KEY`: authenticates the server to SearchAPI.
- `MST_VERIFICATION_KEY_V1`: hashes verification codes inside the server.
- `MST_TENANT_API_KEY_HASH_KEY`: hashes tenant API-key secrets inside the server.

None of those three belongs in Postman's Bearer Token field. Postman needs the
separate `mst_api_...` credential generated in step 4.

## 1. Prepare Java, PostgreSQL, and configuration

You need JDK 21, PostgreSQL, and the Postman desktop app. Use a social account whose
bio you can edit for the successful verification flow.

Open Terminal in the repository root:

```sh
cd /Users/motaamneh/IdeaSnapshots/mst_social
java -version
```

On another machine, replace the path with your own checkout. Use Java 21 for both
the build and the JAR commands below.

### Database

Start your PostgreSQL service. If `mst_social_db` already exists, use it. Otherwise
create a database named `mst_social_db` through pgAdmin. Flyway creates tables, but
does not create the database itself.

The database role must be able to connect and apply migrations. V1 installs
`btree_gist`; if your role cannot install it, ask the database administrator to
install that extension in this database before startup.

### Server environment

If you already have these variables in IntelliJ's run configuration, keep them:

| Variable | Example or required value |
| --- | --- |
| `MST_DB_URL` | `jdbc:postgresql://localhost:5432/mst_social_db` |
| `MST_DB_USERNAME` | Your actual PostgreSQL role |
| `MST_DB_PASSWORD` | That role's password |
| `SEARCHAPI_API_KEY` | Your SearchAPI credential |
| `MST_VERIFICATION_KEY_V1` | A stable secret containing 64 hexadecimal characters |
| `MST_TENANT_API_KEY_HASH_KEY` | A different stable secret containing 64 hexadecimal characters |

For a new installation only, generate each internal secret separately:

```sh
openssl rand -hex 32
```

Run that command twice and save the two different values securely. Do not replace
existing configured secrets on every startup. Changing the tenant hash key makes
existing tenant API keys unusable; changing a verification key can prevent pending
codes from matching.

**IntelliJ environment variables are not automatically exported to Terminal.**
The local key command in step 4 needs its own environment, using the same database
and tenant hash key. Spring Boot also does not automatically load a `.env` file.

## 2. Build, start, and migrate

From the repository root:

```sh
./mvnw -Dmaven.test.skip=true package
```

This creates the executable JAR and skips test compilation and execution. It does
not start the server or require live provider calls.

Start `MstSocialApplication` using your configured IntelliJ run configuration.
Alternatively, with all six variables configured in Terminal:

```sh
java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar
```

Use one HTTP server instance for this walkthrough. Starting the JAR while IntelliJ
already serves port 8080 causes a port conflict. Keep the server running during the
Postman steps.

On startup, Flyway applies the migrations:

| Migration | What it adds |
| --- | --- |
| V1 | Tenants, requests, verified accounts, account links, and audit events |
| V2 | Automatic UUID generation for tenant IDs |
| V3 | Tenant API-key metadata and digests |
| V4 | Shared per-tenant API rate-limit counters |

In pgAdmin, select **mst_social_db → Query Tool** and confirm migration history:

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version IS NOT NULL
ORDER BY installed_rank;
```

Expect successful rows through version `4`. Do not edit applied migrations or
manually run their SQL files. If this query says the table does not exist, check
that the application started successfully and that pgAdmin uses the same database
and schema as the application.

## 3. Get a tenant ID

### First application: create a tenant

In the same pgAdmin Query Tool, run this **once**:

```sql
INSERT INTO tenants DEFAULT VALUES RETURNING id;
```

PostgreSQL generates the UUID. Copy the returned `id`; that is your `tenantId`.
Commit the transaction if you have disabled pgAdmin auto-commit.

You do not enter an ID manually and you do not need an auto-incrementing integer.
The database already generates a UUID through V2. Every execution of this INSERT
creates another tenant, so do not run it for every user or verification.

### Existing application: reuse its tenant

If you already have an API key, step 6's identity endpoint tells you its tenant.
If you are inspecting your local database, list existing tenants:

```sql
SELECT id, created_at
FROM tenants
ORDER BY created_at DESC;
```

The current tenant table has no application-name field. If several rows exist,
identify the intended row from your provisioning notes or existing credential;
do not assume the newest row is the right application.

There is currently **no HTTP endpoint to create or list tenants**. Provisioning
uses trusted database access. Your application's normal API requests must not
send a tenant selector in their body, URL query, or custom header.

## 4. Create a tenant API key

Use an interactive Terminal window, not the IntelliJ Run output console. The key
creation command refuses to run without an interactive console.

Configure these four variables in that Terminal session:

```sh
export MST_DB_URL='jdbc:postgresql://localhost:5432/mst_social_db'
export MST_DB_USERNAME='replace-with-your-database-role'
```

For macOS's default zsh, these prompts let you enter existing secret values without
putting the values directly in shell history or displaying them as you type:

```sh
read -rs 'MST_DB_PASSWORD?Database password: '
printf '\n'
export MST_DB_PASSWORD
read -rs 'MST_TENANT_API_KEY_HASH_KEY?Existing tenant hash key from server configuration: '
printf '\n'
export MST_TENANT_API_KEY_HASH_KEY
```

Use exactly the same hash key as IntelliJ's server configuration. Do not generate
a new one for this command. The local administration mode does not require the
SearchAPI key or verification-code key and does not open an HTTP port.

Replace the placeholder with the tenant UUID from step 3:

```sh
java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar tenant-key create \
  --tenant-id="REPLACE_WITH_TENANT_UUID" \
  --name="Local Postman"
```

The option syntax is `--name=value`. The command prints:

```text
Credential ID: <generated credential UUID>
Tenant ID: <your tenant UUID>
Expires at: never
Store this credential securely; it cannot be retrieved again:
mst_api_<credential UUID>.<64 hexadecimal characters>
```

Save the **entire final line**, including `mst_api_`, the UUID, the dot, and the
secret. The database stores only its digest. You cannot recover the key from SQL;
if lost, create another key and revoke the old credential.

Without `--expires-at`, the credential has no scheduled expiry. You can add
`--expires-at=<future ISO-8601 instant>` when creating one. Credential expiry is
separate from the 15-minute verification request and one-year association validity.

## 5. Import the Postman files and configure variables

1. Open Postman desktop and choose **Import**.
2. Import the collection and environment JSON files linked at the top.
3. Select **MST Social - Local** as the active environment.
4. Open Postman's **Local Vault** and add a secret named `mst-social-api-key`.
5. Set its value to the full `mst_api_...` credential from step 4.
6. If you configure allowed domains for that secret, allow your server hostname,
   initially `localhost`.

The collection's Authorization tab already uses **Bearer Token** with
`{{vault:mst-social-api-key}}`. API requests inherit that authorization. Do not add
another Authorization header and do not include the word `Bearer` in the secret
value. Health explicitly uses **No Auth**.

The committed files contain no API key. Vault references keep the real credential
out of collection and environment files. See Postman's official documentation for
[importing files](https://learning.postman.com/docs/getting-started/importing-and-exporting/importing-data/)
and [using Local Vault references](https://learning.postman.com/docs/use/postman-vault/use-vault-secrets).

Edit these environment values:

| Variable | Initial value / action |
| --- | --- |
| `baseUrl` | `http://localhost:8080`, with no trailing slash; change if using another port |
| `subjectId` | `user-123`, or a user ID from your application |
| `platform` | `INSTAGRAM`; change to `TIKTOK` for TikTok |
| `handle` | Replace `replace_with_your_handle` with the username whose bio you control |
| `tenantId` | Initially blank; copy from the identity response for reference |
| `credentialId` | Initially blank; copy from the identity response for reference |
| `verificationId` | Initially blank; later copy the creation response's `requestId` |
| `accountId` | Initially blank; later copy `account.accountId` from successful verification |
| `cancelVerificationId` | Used only for the optional cancellation example |

`tenantId` and `credentialId` are notes for you; the collection never sends them
as tenant selectors. Copy returned IDs manually into the environment as directed
below. There are no scripts that capture responses or send follow-up requests.

## 6. Confirm health and retrieve your authenticated tenant

In folder **01 - Connection and identity**, send:

### 01 - Health

```http
GET {{baseUrl}}/actuator/health
```

Expected: `200` with `{"status":"UP"}`. Health needs no credential. A `503`
means the application reports an unhealthy dependency, often PostgreSQL.

### 02 - Who am I

```http
GET {{baseUrl}}/api/v1/tenant
Authorization: Bearer <your full tenant API key>
```

Expected `200`:

```json
{
  "tenantId": "11111111-1111-4111-8111-111111111111",
  "credentialId": "22222222-2222-4222-8222-222222222222"
}
```

These UUIDs are illustrative. Copy your actual response values into the matching
environment variables. `tenantId` must match the row chosen in step 3. This endpoint
does not call SearchAPI and does not create a tenant.

## 7. Create a verification

In **02 - Verify a social account**, send **03 - Create verification**:

```http
POST {{baseUrl}}/api/v1/verifications
Content-Type: application/json
```

The collection supplies this raw JSON body:

```json
{
  "subjectId": "{{subjectId}}",
  "platform": "{{platform}}",
  "handle": "{{handle}}"
}
```

For example, with environment variables substituted:

```json
{
  "subjectId": "user-123",
  "platform": "INSTAGRAM",
  "handle": "my_username"
}
```

Expected `201 Created`:

```json
{
  "requestId": "33333333-3333-4333-8333-333333333333",
  "code": "mst_<32 generated hexadecimal characters>",
  "expiresAt": "2030-01-01T12:15:00Z",
  "instructions": "Add this exact code to your social profile bio, then submit a verification attempt before expiry."
}
```

The ID, code, and date above are illustrative. Use the actual response:

1. Copy `requestId` into the Postman environment's **`verificationId`** variable.
2. Copy the exact **`code`**, without JSON quotes, into your social profile bio.
3. Save the bio on Instagram or TikTok and confirm the change is visible.
4. Complete verification before the returned `expiresAt`; the default window is
   15 minutes from creation.

Creation makes no provider call. It returns the code once; status requests cannot
recover it. If you lose the code, cancel this pending request and create another.
Do not repeatedly click Create: a duplicate active request returns `409`.

## 8. Read status, then verify

### 04 - Read verification

```http
GET {{baseUrl}}/api/v1/verifications/{{verificationId}}
```

Expect `200`, `status: PENDING`, `failedAttempts: 0`, and
`maxFailedAttempts: 10` for a new request. This does not fetch a profile or consume
a failed attempt. The response also includes your subject, normalized handle,
platform, and timestamps.

### 05 - Verify bio (provider lookup)

After saving the bio, send:

```http
POST {{baseUrl}}/api/v1/verifications/{{verificationId}}/verify
```

**Body must be None.** Do not send `{}`, a code, or a biography. The server fetches
the profile itself. An eligible attempt consumes SearchAPI usage; the server does
not automatically retry. Private profiles are allowed if username and bio are
available from the provider.

On success, expect `200` and these fields within the response:

```json
{
  "outcome": "VERIFIED",
  "verification": {
    "requestId": "33333333-3333-4333-8333-333333333333",
    "status": "VERIFIED"
  },
  "account": {
    "accountId": "44444444-4444-4444-8444-444444444444",
    "active": true
  }
}
```

This is an abbreviated example; see the [API reference](VERIFICATION_API.md#3-verify-after-the-user-updates-their-bio)
for the full shape. Copy **`account.accountId`** into the environment's `accountId`.
The full account response includes `verifiedAt` and `validUntil`, normally one
calendar year apart.

Once verification succeeds, you may remove the code from the bio. The saved
association remains until expiry or revocation; the server does not continually
monitor the bio. Verification records evidence of control at that time, not
continuous ownership or an unchanging platform identity.

### If verification does not succeed

| Response | What to do |
| --- | --- |
| `422 CODE_NOT_FOUND` | Check the exact code, username, and saved bio. Provider evidence may lag; allow time before another attempt. Failures 1–9 stay pending. |
| `409 LOCKED` | The tenth bio mismatch locked this request. Create a new request and put its new code in the bio. |
| `410 EXPIRED` | Create a new request and use its new code. |
| `409 IN_PROGRESS` / `STALE_ATTEMPT` | Read current status before attempting again. |
| `409 ACCOUNT_ALREADY_LINKED` | Inspect the existing association within your application; do not change subject IDs just to bypass it. |
| `PROVIDER_FAILURE` | Inspect `providerFailure.reason` and `retryable`; honor `Retry-After` when present. Provider failures do not increment bio-mismatch attempts. |

Retrying an already verified request returns `200 ALREADY_VERIFIED` with its original
association, without another provider call or a new validity period.

## 9. Read the verified association

Send **06 - Read verified account**:

```http
GET {{baseUrl}}/api/v1/social-identities/{{accountId}}
```

Expect `200` with `accountId`, `subjectId`, `platform`, `canonicalHandle`,
`verifiedAt`, `validUntil`, `revokedAt`, and `active`, plus provider metadata.
`providerAccountId` can be null with the current provider.

Check **`active`**, not only whether a row exists. Expired and revoked associations
remain readable. There is currently no endpoint to list associations by subject;
your backend should save the returned request and account IDs with its user.

At this point the main walkthrough is complete.

## 10. Optional: cancel a pending request

Open **03 - Optional cancellation**. This example uses a separate subject
`{{subjectId}}-cancel-demo` and a separate ID variable so it does not overwrite your
successful walkthrough's `verificationId`.

1. Send **07 - Create request for cancellation**. It makes no provider call.
2. Copy its returned `requestId` into **`cancelVerificationId`**.
3. Send **08 - Cancel pending request**, which has no body:

   ```http
   DELETE {{baseUrl}}/api/v1/verifications/{{cancelVerificationId}}
   ```

4. Expect `200` with `status: CANCELED` if the request was still pending.
5. Send **09 - Read canceled request** to inspect it again.

Repeating cancellation preserves its original timestamp. Terminal requests are
returned unchanged; canceling a verified request does not revoke its association.
For a lost code from your main walkthrough, set `cancelVerificationId` to that
pending request's ID before sending the cancellation request.

## 11. Optional: revoke the verified association

Only do this when you want to remove the association's active verification. Open
**04 - Optional account revocation** and send **10 - Revoke verified account**:

```http
DELETE {{baseUrl}}/api/v1/social-identities/{{accountId}}
```

Send no body. Expect `200`, `active: false`, and a `revokedAt` timestamp. Read the
account again to see the saved state. This retains history; it does not delete the
Instagram/TikTok account or revoke the tenant API key.

To establish a new active association, create a new verification and use its new
code. Repeating verification of the old request does not reactivate a revoked
association.

## 12. Manage tenant credentials after the walkthrough

One tenant can have several API keys. For rotation, create a new key for the same
tenant, use it in your backend or Postman Vault, confirm `/api/v1/tenant` succeeds,
then revoke the old key with the local command:

```sh
java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar tenant-key revoke \
  --tenant-id="REPLACE_WITH_TENANT_UUID" \
  --credential-id="REPLACE_WITH_OLD_CREDENTIAL_UUID"
```

The database environment and tenant hash secret must still be configured in that
Terminal. Revocation takes effect for subsequent authentication without a server
restart. A request that already authenticated may still finish.

If you have forgotten a credential ID, this metadata query avoids selecting secret
digests. Replace the tenant UUID before running it:

```sql
SELECT id AS credential_id, tenant_id, name, created_at, expires_at, revoked_at
FROM tenant_api_keys
WHERE tenant_id = 'REPLACE_WITH_TENANT_UUID'::uuid
ORDER BY created_at DESC;
```

Revoking a tenant credential does not revoke saved social associations. It stops
that credential from authenticating API requests. Other keys for the tenant keep
working.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Connection refused on port 8080 | Start the normal HTTP application, not only `tenant-key`; check its startup logs and `baseUrl`. |
| PostgreSQL connection refused | Start PostgreSQL; check host, port, and database URL. |
| Port 8080 already in use | Stop your duplicate local HTTP server or configure another `SERVER_PORT` and update `baseUrl`. |
| Missing configuration when running the JAR | IntelliJ values do not carry into Terminal. Configure the required variables in the process you launch. |
| Key creation asks for an interactive console | Use Terminal without output redirection or piping; do not run it in the IDE Run console. |
| Key creation fails with a database error | Confirm the tenant row is committed, the correct database is selected, and migrations through V3 succeeded. |
| Vault placeholder stays unresolved | Unlock Local Vault, check the exact secret name `mst-social-api-key`, and check allowed domains. |
| `401 UNAUTHORIZED` | Use the complete tenant key; check revocation/expiry and that CLI/server use the same database and tenant hash key. SearchAPI credentials will not work here. |
| `400 INVALID_REQUEST` with an ID in the URL | Populate the correct UUID environment variable; do not leave a blank ID or literal placeholder. |
| `400 VALIDATION_FAILED` | Use a nonblank subject (max 128 Java characters), a nonblank handle (max 256), and a supported platform. The cancellation example adds a suffix to the subject. |
| `400` with an extra `tenantId` field | Remove it. Unknown JSON fields are rejected; tenant identity comes from the credential. |
| `400 UNEXPECTED_BODY` | Set Body to None for verify, cancel, and revoke, including removing `{}`. |
| `400 IDEMPOTENCY_NOT_SUPPORTED` | Remove `Idempotency-Key`; creation-response replay is not implemented. |
| `404` for a resource | Check the saved ID and tenant credential. Another tenant's resources also return `404`. |
| `409 DUPLICATE_ACTIVE_REQUEST` | Reuse the saved request, or cancel it before creating another. A lost creation response cannot be replayed. |
| `422 UNSUPPORTED_PLATFORM` | Use uppercase `INSTAGRAM` or `TIKTOK`; X and Facebook are not implemented. |
| `429 RATE_LIMIT_EXCEEDED` | Honor `Retry-After`. Default per-tenant minute limits: 10 creates, 10 verifies, 120 reads, 30 cancellations/revocations. |
| `429 ACTIVE_REQUEST_LIMIT_REACHED` | Cancel pending requests or wait for expiry; default active quotas are 3 per subject and 3 per platform/handle. |
| `502` with `PROVIDER_AUTHENTICATION_FAILED` | Check the server's SearchAPI credential, not the Postman tenant credential. |
| `503` | Inspect the error code: provider throttling/error, unavailable storage, or authentication storage failure require different fixes. |
| `504` | Provider timed out; inspect current status and retry deliberately if still eligible. |

Ten failed bio matches is a limit on **one verification request**. Ten verify calls
per minute is a separate limit shared across **one tenant's credentials**. A provider
error does not use a failed bio-match attempt, but an admitted HTTP call still counts
toward the HTTP rate limit.

## How this maps to your real application

```text
Your user -> your backend (authenticate and authorize the user)
         -> MST Social using the backend's tenant API key
         -> create request and return code to your user
Your user -> edits their own social profile bio
Your backend -> asks MST Social to verify the saved request ID
MST Social -> SearchAPI -> matching evidence -> saved account association
Your backend -> stores accountId for its user and checks active status when needed
```

Keep the tenant credential on your backend. In Next.js, do not use a
`NEXT_PUBLIC_...` variable for it. Your backend chooses `subjectId` from its
authenticated user and authorizes access to saved request/account IDs. MST Social
isolates tenants; it does not implement your application's end-user login system.

Use HTTPS outside localhost. Keep API keys, server secrets, and real creation
responses out of commits, shared examples, and screenshots. The import templates
are safe to commit as provided; do not replace them with exports containing your
real credentials or user data.

For the complete HTTP contract, see [VERIFICATION_API.md](VERIFICATION_API.md) and
[openapi.yaml](openapi.yaml). For wiring and database details, see the
[server README](README.md).
