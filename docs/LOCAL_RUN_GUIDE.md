# Local run: IntelliJ, tenant creation, and Bearer authentication

This guide runs MST Social in IntelliJ with your local PostgreSQL database. It
walks through creating a tenant, generating its API key, authenticating Postman,
and verifying a social account.

If your server already runs, start at **step 4**. If you already have a working
tenant API key, reuse it and start at **step 7**.

For containers, use the separate [Docker guide](DOCKER_GUIDE.md). Docker's database
and your local PostgreSQL database can contain different tenants and credentials.

## Understand the pieces first

| Item | What it means | Where it comes from |
| --- | --- | --- |
| Tenant | Your application using MST Social | You create one row in PostgreSQL |
| `tenantId` | That application's UUID | PostgreSQL generates it |
| Tenant API key | Credential your backend sends with API requests | The `tenant-key create` command generates it |
| `credentialId` | UUID identifying that particular API key | The same command; useful for revoking a key |
| `subjectId` | A user in your application | Your own user database; use `user-123` in this walkthrough |
| Verification `requestId` | One social-account verification request | The create-verification API |
| `code` | Temporary marker to place in the social bio | The create-verification response, once |

For example, your Next.js app is one tenant, and its users are subjects. Create a
tenant once for the application, not once per user or server restart.

**Bearer authentication** means sending a credential in this HTTP header:

```http
Authorization: Bearer mst_api_<credential-uuid>.<secret>
```

The line above illustrates the format; it is not a usable key. The tenant UUID
alone cannot authenticate a request. Keep the tenant API key in your application's
backend; the backend authenticates its user and supplies that user's `subjectId`.

## 1. Prepare Java and PostgreSQL

You need JDK 21, IntelliJ IDEA, a running PostgreSQL installation, and Postman.
The commands below use macOS's default **zsh** shell.

1. Open this repository's root `pom.xml` as a Maven project in IntelliJ.
2. Set the project SDK and Maven runner JDK to Java 21. Reload the Maven project.
3. In pgAdmin, connect to your local PostgreSQL server.
4. If `mst_social_db` does not exist, create it under **Databases**. If it exists,
   reuse it.

Flyway creates the tables when the application starts; it does not create the
database. Your database role needs permission to connect and apply the migrations.
The first migration installs `btree_gist`; if your role cannot install it, have
your database administrator install that extension in `mst_social_db` first.

## 2. Configure the IntelliJ application

Open **Run → Edit Configurations** and select or create a configuration for:

```text
Main class: com.motaamneh.mstsocial.server.MstSocialApplication
Module/classpath: mst-social-server
JDK: 21
Working directory: the repository root
```

In its **Environment variables** editor, set these values. If you already configured
them successfully, keep your existing values.

| Variable | Value |
| --- | --- |
| `MST_DB_URL` | `jdbc:postgresql://localhost:5432/mst_social_db` (adjust if your database differs) |
| `MST_DB_USERNAME` | Your real PostgreSQL role, such as `postgres` |
| `MST_DB_PASSWORD` | That role's password |
| `SEARCHAPI_API_KEY` | Your SearchAPI credential |
| `MST_VERIFICATION_KEY_V1` | A stable secret of 64 hexadecimal characters |
| `MST_TENANT_API_KEY_HASH_KEY` | A separate stable secret of 64 hexadecimal characters |
| `MST_API_DOCS_ENABLED` | `true` for local Swagger UI |
| `MST_LOG_LEVEL` | `DEBUG` for detailed local logs; `INFO` for everyday use |

For a **new installation only**, run this command twice to generate the two
different internal hash keys:

```sh
openssl rand -hex 32
```

Save one value as `MST_VERIFICATION_KEY_V1` and the other as
`MST_TENANT_API_KEY_HASH_KEY`. Keep them stable across restarts. Changing the tenant
hash key makes existing tenant API keys fail authentication. Changing the
verification key can prevent pending bio codes from matching.

The tracked [`.env.example`](../.env.example) lists configuration names and blank
secret values. Actual secrets belong in your local configuration. Spring Boot
does not automatically read a `.env` file.

**These three secrets are not Postman Bearer tokens:**

- `SEARCHAPI_API_KEY` authenticates MST Social's calls to SearchAPI.
- `MST_VERIFICATION_KEY_V1` protects stored verification-code digests.
- `MST_TENANT_API_KEY_HASH_KEY` protects stored tenant API-key digests.

Postman will use a separate credential generated in step 6.

## 3. Build, start, and check the server

Open Terminal in the repository root (the directory containing `mvnw`):

```sh
java -version
./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package
```

Use Java 21. The Maven command builds the modules and packages the administration
JAR while skipping test compilation and execution. Rebuild after pulling code
changes so later CLI commands use the current implementation.

Start `MstSocialApplication` using the IntelliJ configuration from step 2. Wait
for `Started MstSocialApplication` and Tomcat's port `8080` message.

Keep this process running. In Postman, send:

```http
GET http://localhost:8080/actuator/health
```

Use **No Auth**. Expect `200 OK` with `"status": "UP"`. You can also open
<http://localhost:8080/swagger-ui.html> when API docs are enabled. The root URL
`http://localhost:8080/` is not a homepage.

Only one HTTP server can use port 8080. The administration command in step 6 runs
without an HTTP server, so it can run while IntelliJ serves your API.

## 4. Create or find your tenant in pgAdmin

Open **mst_social_db → Query Tool**. Check that startup applied the migrations:

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version IS NOT NULL
ORDER BY installed_rank;
```

The current schema has migrations through version `4`. Expect successful rows.
Do not manually execute the migration files.

### If this application already has a tenant

List existing tenants:

```sql
SELECT id, created_at
FROM tenants
ORDER BY created_at DESC;
```

Reuse the correct application's ID. The table has no application-name field, so
keep a local note of which tenant belongs to which application. If you already
have a working API key, `/api/v1/tenant` also tells you its tenant ID.

### If this is a new application

Run this once:

```sql
INSERT INTO tenants DEFAULT VALUES RETURNING id;
```

Copy the returned UUID. PostgreSQL generates it automatically. Commit the
transaction if pgAdmin auto-commit is disabled; otherwise the CLI cannot see it.

There is no HTTP tenant-creation endpoint. This initial step requires trusted
database access. Each execution of the INSERT creates another tenant.

## 5. Give Terminal the same database settings and tenant hash key

**IntelliJ's Run Configuration does not export its variables to Terminal.** This
also applies to IntelliJ's Terminal tab. Your running server and the CLI are
separate processes, each needing its own environment settings.

Open an interactive Terminal at the repository root. Paste this zsh block:

```sh
export MST_DB_URL='jdbc:postgresql://localhost:5432/mst_social_db'

read -r 'MST_DB_USERNAME?Database username: '
read -rs 'MST_DB_PASSWORD?Database password: '
printf '\n'
read -rs 'MST_TENANT_API_KEY_HASH_KEY?Paste the existing tenant hash key from IntelliJ: '
printf '\n'

export MST_DB_USERNAME MST_DB_PASSWORD MST_TENANT_API_KEY_HASH_KEY
```

Enter each requested value and press Enter. Password and hash-key input is hidden;
seeing no characters while typing or pasting is normal. The prompt approach keeps
these secret values out of the commands saved in shell history.

Use the **same database URL, database credentials, and tenant hash key as IntelliJ**.
Adjust `MST_DB_URL` above if needed. Copy the existing hash key; do not generate a
new one for the CLI. An empty database password works only if explicitly configured
and permitted by your PostgreSQL setup.

These exports apply to this Terminal session. A new session needs them again.
Administration mode does not need `SEARCHAPI_API_KEY` or `MST_VERIFICATION_KEY_V1`.

## 6. Generate the tenant's Bearer credential

In that same Terminal, enter the tenant UUID from step 4:

```sh
read -r 'MST_LOCAL_TENANT_ID?Tenant UUID from pgAdmin: '

java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar \
  tenant-key create \
  --tenant-id="$MST_LOCAL_TENANT_ID" \
  --name="Local Postman"
```

`MST_LOCAL_TENANT_ID` is just a shell variable used by this example. `Local Postman`
is a label for the new credential. Options use `--option=value` syntax.

Use an interactive Terminal, not IntelliJ's Run output console. Do not pipe or
redirect this command: key creation requires an interactive console.

Expected output includes:

```text
Credential ID: <generated credential UUID>
Tenant ID: <your tenant UUID>
Expires at: never
Store this credential securely; it cannot be retrieved again:
mst_api_<credential UUID>.<64 hexadecimal characters>
```

Save the **entire final line**, including `mst_api_`, the UUID, the dot, and the
secret. Save the credential ID as well. The database stores a digest, so the full
credential cannot be retrieved later.

By default this API key has no scheduled expiry. For an expiring key, add
`--expires-at=<future ISO-8601 instant>` when creating it. API-key expiry is separate
from verification request expiry and verified-account validity.

## 7. Configure Postman and confirm authentication

Import both files:

- [MST Social collection](../mst-social-server/postman/MST-Social.postman_collection.json)
- [Local environment](../mst-social-server/postman/MST-Social-Local.postman_environment.json)

Then:

1. Select **MST Social - Local** as the active environment.
2. Check `baseUrl` is `http://localhost:8080`.
3. Open Postman's Local Vault and create a secret named `mst-social-api-key`.
4. Set its value to the complete credential from step 6. If you restrict its
   allowed domains, allow `localhost` for this setup.
5. The collection's Authorization tab already uses **Bearer Token** with
   `{{vault:mst-social-api-key}}`. Leave API requests inheriting collection auth.
6. Send **02 - Who am I (authenticated tenant)**:

```http
GET http://localhost:8080/api/v1/tenant
```

Expect `200 OK` and a response containing your `tenantId` and `credentialId`.
This confirms which application's credential you supplied. It does not create a
key or sign you in.

For a quick manual request, you can instead select **Authorization → Bearer Token**
on that request and paste the full credential directly. Paste only `mst_api_...`,
without the word `Bearer`; Postman adds that prefix. Avoid a second manually added
Authorization header. A request-level override applies only to that request;
configure collection auth to authenticate the other requests too.

## 8. Run your first social verification

Send requests individually. The collection has no scripts to copy response values
automatically, and the full flow requires you to edit the social bio between calls.

### A. Set the environment values

| Variable | Value |
| --- | --- |
| `subjectId` | `user-123`, or an actual user ID from your own app |
| `platform` | `INSTAGRAM` or `TIKTOK` |
| `handle` | The username of a social account whose bio you can edit |

In a real integration, your backend derives `subjectId` from its authenticated
user and authorizes the operation. Postman is manually simulating that backend.
The tenant is determined by your Bearer credential; do not add `tenantId` to the body.

### B. Create a verification

Send **03 - Create verification**, which makes this request:

```http
POST {{baseUrl}}/api/v1/verifications
Content-Type: application/json
```

```json
{
  "subjectId": "{{subjectId}}",
  "platform": "{{platform}}",
  "handle": "{{handle}}"
}
```

Expect `201 Created`. Copy the response's **`requestId` into the Postman environment
variable `verificationId`**. These names differ intentionally in the current
collection. Copy the exact `code` into the social profile's bio and save the profile.
The code is returned only during creation; reading the request will not return it.

### C. Verify the biography

Send **05 - Verify bio (provider lookup)**:

```http
POST {{baseUrl}}/api/v1/verifications/{{verificationId}}/verify
```

Leave the body empty. This operation can call SearchAPI and consume provider usage.
It checks the bio on the social platform; you do not send the bio or code in this
request body. A private profile can pass when the provider returns its username
and biography.

Success returns `200 OK` with `outcome: VERIFIED`. Copy `account.accountId` from
the response into the Postman environment variable `accountId`.

### D. Read the saved association

Send **06 - Read verified account**:

```http
GET {{baseUrl}}/api/v1/social-identities/{{accountId}}
```

Check `active` and `validUntil`. By default, a verification request lasts 15 minutes,
the tenth counted failed attempt locks it, and the verified association lasts one
calendar year. Provider failures do not increment the mismatch counter.

For cancellation, revocation, failure responses, and all collection requests,
continue with the [full Postman guide](../mst-social-server/POSTMAN_GUIDE.md).

## 9. Read logs in IntelliJ

With `MST_LOG_LEVEL=DEBUG`, restart the application and open its **Run** console.
API calls show request completion, authentication details, verification events,
and provider outcomes where applicable.

Each API response includes `X-Request-Id`. Copy that response header from Postman
and search for its value in the IntelliJ console. The corresponding logs use
`httpRequestId`. This ID identifies one HTTP call; it is different from the
verification's JSON `requestId`.

Logs omit Bearer credentials, verification codes, and raw biographies. See the
[logging guide](LOGGING_GUIDE.md) for event names and authentication failure reasons.

## 10. What to do on the next local run

1. Start PostgreSQL.
2. Start the IntelliJ application with the same environment values.
3. Select the same Postman environment and reuse the saved tenant API key.
4. Create a new verification only when you need one.

You do not need to recreate the tenant, regenerate internal hash keys, or run
`tenant-key create` on every restart. The database keeps tenant and credential data.

If you lose an API key, create a replacement for the same tenant using steps 5–6.
After confirming the replacement works, revoke the lost credential using its ID:

```sh
read -r 'MST_LOCAL_OLD_CREDENTIAL_ID?Credential UUID to revoke: '

java -jar mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar \
  tenant-key revoke \
  --tenant-id="$MST_LOCAL_TENANT_ID" \
  --credential-id="$MST_LOCAL_OLD_CREDENTIAL_ID"
```

Use the same Terminal environment from steps 5–6. Revocation disables that key;
make sure you select the old credential ID, not your replacement's ID.

## Troubleshooting

| Symptom | What to check or do |
| --- | --- |
| `role "${MST_DB_USERNAME}" does not exist` | The CLI did not receive the environment variable. Repeat step 5 in the same Terminal that runs Java. Do not create a database role literally named `${MST_DB_USERNAME}`. |
| PostgreSQL connection refused | Start PostgreSQL and verify the host and port in `MST_DB_URL`. |
| `password authentication failed` | Match the database role/password to the intended PostgreSQL installation. |
| `tenants` or `flyway_schema_history` does not exist | Check application startup errors and that pgAdmin connects to the same database/schema. |
| Tenant not found during key creation | Check the copied UUID, database URL, and whether the tenant INSERT was committed. |
| Unable to access the JAR | Run the build in step 3 and execute Java from the repository root. |
| CLI asks for an interactive terminal | Run directly in Terminal, without pipes or output redirection. |
| `401 UNAUTHORIZED` | Use the full generated `mst_api_...` key, exactly one Authorization header, and the selected environment/Vault value. Check expiry/revocation and that CLI and IntelliJ use the same database and tenant hash key. |
| Who am I works but another request gets 401 | Check that request's Authorization tab for an override; use collection inheritance. |
| `404` for a verification or account | Check the saved ID and use a key for the tenant that owns it. |
| `429` | Wait for the response's `Retry-After` delay before retrying. |
| Port 8080 already in use | Stop the other HTTP server or choose a different port and update Postman's `baseUrl`. |
| Swagger URL is unauthorized | Set `MST_API_DOCS_ENABLED=true`, restart, and use `/swagger-ui.html`. |
| No API logs in IntelliJ | Check that Postman targets this IntelliJ process rather than Docker or another port. Confirm you rebuilt/restarted with the logging changes and set `MST_LOG_LEVEL=DEBUG` for more detail. |

For a 401, look for `authentication.failed` in the server logs using the response's
`X-Request-Id`. The public response stays generic; the server log provides a reason
such as `SECRET_MISMATCH` or `UNKNOWN_CREDENTIAL` without revealing the credential.
