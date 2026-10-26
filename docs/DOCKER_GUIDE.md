# Docker, explained through MST Social

This guide explains the setup from the beginning: what Docker does, why each file
exists, how to start the project, and how to create your first tenant and API key.
All commands run from the repository root unless stated otherwise.

## Choose your starting point

- New to Docker: read sections 1–3, then follow section 4 onward.
- Ready to run: start at [section 4](#4-start-the-project-yourself).
- API running: create your
  [tenant and API key](#5-create-a-tenant-and-its-first-api-key),
  then use [Postman](#7-use-postman).
- Returning later: see [daily commands](#8-daily-commands-and-what-they-mean),
  including backups and updates.
- Startup failed: see [troubleshooting](#10-common-mistakes-and-how-to-diagnose-them).

The shell examples use macOS/Linux bash or zsh syntax. On Windows, use a WSL2 shell
with Docker Desktop integration; PowerShell has different quoting, line continuation,
and redirection behavior. You need Git to clone the project, Docker with Compose v2,
and a SearchAPI key for live verification. Local Java, Maven, and PostgreSQL installs
are not required for the Compose path.

## 1. The problem we are solving

Previously, a developer needed Java 21, a Maven build, PostgreSQL, database
configuration, and an IDE or terminal setup before using the API. We now package
the Java application and describe its database alongside it. A developer needs
Docker with Compose, this repository, and the required credentials.

The HTTP API and Java domain logic are the same. Docker starts the application in
a predictable environment. Flyway still owns database tables and migrations.

## 2. Six words you need to know

| Word | Meaning in this project |
| --- | --- |
| **Image** | A packaged filesystem plus startup instructions: Java, `/app/app.jar`, and its launch command. Our local image is named `mst-social:local`. |
| **Container** | A running instance of an image. It has its own processes, network identity, and filesystem view. |
| **Dockerfile** | Instructions Docker follows to build our application image. |
| **Compose** | A tool that reads `compose.yaml` and manages the application and database together. |
| **Volume** | Storage managed by Docker that survives removal of the database container. |
| **Network** | The connection between containers. Compose gives the database the hostname `db`. |

An image is the reusable package. A container is an instance created from that
package. You can remove and recreate a container while retaining its volume.
On macOS, Docker Desktop runs the Linux containers inside a managed Linux VM.

Our layout is:

```text
Your laptop
  Postman / your backend
          |
          | http://localhost:8080
          v
  app container: Java 21 + Spring Boot server
          |                         |
          | db:5432                 | HTTPS
          v                         v
  db container: PostgreSQL 17     SearchAPI
          |
          v
  postgres_data volume
```

## 3. The implementation decisions, in order

### Step A: use the Docker branch

This work belongs on `feature/docker-local-setup`, starting from the merged
verification API. The branch already existed when this setup was implemented.

For future work, the usual sequence after merging the previous PR is:

```sh
git switch main
git pull --ff-only origin main
git switch -c feature/your-next-change
```

You do not need to run these commands again to use the current Docker setup.

### Step B: decide what goes into the image

The server JAR already contains the core and provider dependencies. We therefore
build one application image, with PostgreSQL provided by its own official image.

We use a **multi-stage Dockerfile**:

1. The `build` stage has the Java Development Kit, which includes the compiler.
2. Maven Wrapper builds all three modules in dependency order.
3. The `runtime` stage receives only the executable server JAR and has a Java runtime.

The runtime image does not contain the builder's source tree or Maven cache.
Docker documents this pattern in [Multi-stage builds](https://docs.docker.com/build/building/multi-stage/).

Read [Dockerfile](../Dockerfile) with this table:

| Instruction | Why it is there |
| --- | --- |
| `FROM eclipse-temurin:21-jdk-jammy AS build` | Start a build stage using the official Temurin Java 21 JDK image on Ubuntu Jammy. |
| `WORKDIR /workspace` | Set the directory for later build commands inside the image. It is not a directory on your laptop. |
| `COPY --chmod=0755 mvnw ...` | Copy the Maven Wrapper script and make it executable. |
| `COPY` for wrapper properties, POMs, and `src/main` | Bring in the files needed to compile the reactor. The build context is the repository root so sibling modules are available. |
| `RUN --mount=type=cache,target=/root/.m2 ...` | Run Maven while reusing downloaded dependencies between builds. |
| `-Dmaven.test.skip=true package` | Build the JARs while skipping both test compilation and test execution. |
| `FROM eclipse-temurin:21-jre-jammy AS runtime` | Begin a separate final image with a Java runtime. |
| Install `curl` | The container's health check calls the existing health endpoint. |
| Create user/group `mstsocial` | Give the application its own non-root identity, UID/GID 10001. |
| `COPY --from=build ... /app/app.jar` | Copy the packaged server from the earlier stage. The filename inside the container is stable across project version changes. |
| `USER 10001:10001` | Run subsequent container commands as that user. |
| `EXPOSE 8080` | Document the application's port. This alone does not publish a host port. |
| `HEALTHCHECK ...` | Periodically ask the local application whether it is healthy. |
| `ENTRYPOINT ["java", "-jar", "/app/app.jar"]` | Start the JVM directly and pass additional arguments to the existing main method. |

The build uses the repository's Maven version; you do not need Maven or Java
installed on your laptop for this Docker path. Internet access is needed for the
first image build and dependency downloads. Later builds reuse cached layers and
Maven dependencies where possible. Source changes still trigger compilation.

The official image tags keep the Java major version and Linux distribution
explicit. They can receive maintenance updates; they are not immutable digest
pins. See the [Temurin image documentation](https://hub.docker.com/_/eclipse-temurin).

### Step C: keep the build context small

The final `.` in `docker build -t mst-social:local .` means “use this directory as
the build context.” Docker can only copy files from that context.

[.dockerignore](../.dockerignore) starts by excluding everything, then allows the
POMs, Maven Wrapper, and each module's production source/resources. This keeps
Git history, IDE files, local environment files, existing `target` directories,
and documentation out of the build context. Common secret/key file extensions
are also excluded under the source directories.

`.gitignore` controls what Git tracks. `.dockerignore` controls what Docker receives
for a build. They solve different problems. If you later add another Maven module
or required build-time file, update the Dockerfile and this allowlist together.

### Step D: describe the two services in Compose

[compose.yaml](../compose.yaml) defines:

**`db`:** PostgreSQL 17, a database called `mst_social_db`, a local development role
called `mst_social`, a password from configuration, and a persistent volume.

**`app`:** our locally built image, the database connection, three application
secrets, and the host port mapping.

Important details:

- `depends_on` with `condition: service_healthy` waits for the database readiness
  check before starting the app. Plain start order is insufficient: a running
  PostgreSQL container may still be initializing. See [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/).
- The database check uses `pg_isready`. The doubled `$$` lets the variable reach
  the container's shell instead of being expanded by Compose on the laptop.
- The JDBC URL uses **`db`**, not `localhost`. From inside `app`, `localhost` refers
  to the app container itself, not PostgreSQL or your laptop.
- PostgreSQL has no published host port. Your existing PostgreSQL service on the
  laptop can continue using port 5432. This Compose database is separate and starts
  with its own data; it does not import the database you used in IntelliJ.
- `127.0.0.1:8080:8080` means host address, host port, then container port. Only
  programs on your laptop can use that published address. `MST_HTTP_PORT` changes
  the host port while the server continues listening on container port 8080.
- `postgres_data` stores PostgreSQL 17's data directory at `/var/lib/postgresql/data`.
  Do not change the PostgreSQL major version without a planned database upgrade.
- `read_only: true` protects the app container's root filesystem. A temporary
  writable `/tmp` remains available to Java. Application state lives in PostgreSQL.
- `cap_drop` and `no-new-privileges` reduce the app's OS privileges.
- `init: true` provides process cleanup and signal forwarding. The exec-form
  entrypoint lets shutdown signals reach Java.
- `restart: unless-stopped` restarts a container if its process exits unexpectedly.
  A health check marking a still-running process unhealthy does not itself restart it.

This Compose file is for local development. The PostgreSQL image's initial role
has database administrator privileges, which lets Flyway install `btree_gist` and
apply migrations. A production deployment needs its own access, TLS, backup, and
migration-role design. The [official PostgreSQL image documentation](https://hub.docker.com/_/postgres)
describes its initialization and storage behavior.

### Step E: supply configuration at runtime

[docker.env.example](../docker.env.example) is a tracked template containing blank
secret fields. Its local copy, `.env.docker`, is ignored by Git and excluded from
the image. This filename avoids overwriting an existing `.env` or your IDE settings.

`--env-file .env.docker` tells **Compose** where to read variable values. Compose
then passes the explicitly listed environment entries into the containers. Spring
reads those process environment variables through the existing configuration.
Spring does not need to load this file itself.

The `${NAME:?message}` syntax fails immediately if a required variable is missing
or empty. Credentials are not Docker build arguments and are not baked into the JAR
or image. Administrators with access to the Docker daemon can inspect container
configuration, so this local setup is not a secret-management system.

## 4. Start the project yourself

### 4.0 Open the repository root

Use your existing checkout. The correct directory contains `pom.xml`, `Dockerfile`,
`compose.yaml`, and `docker.env.example`; do not run these commands from inside
`mst-social-server`.

For a new checkout, once the Docker changes are available on the remote branch:

```sh
git clone https://github.com/motaamneh/mst_social.git
cd mst_social
```

If the Docker work has not yet merged into `main`, use the published Docker branch
instead. If the files are missing, check the branch before continuing. Existing
users should skip cloning and open Terminal in their current repository directory.

### 4.1 Open Docker Desktop

Wait until Docker Desktop says its engine is running. Then check:

```sh
docker version
docker compose version
```

The first command should show both Client and Server sections. If only the Client
is available, start Docker Desktop. The modern command is `docker compose` with a
space. Install instructions are available from [Docker Desktop](https://docs.docker.com/desktop/).

For this walkthrough, start the project with the Compose command in step 4.4.
Clicking **Run** next to `mst-social:local` in Docker Desktop's Images screen starts
only the application image. It does not load `compose.yaml`, create PostgreSQL,
or supply the required environment variables. An unconfigured standalone container
falls back to `localhost:5432` and fails because there is no database inside it.
After Compose starts the project, you can view its services in Docker Desktop.

### 4.2 Prepare the local configuration

From the repository root:

```sh
cp -n docker.env.example .env.docker
chmod 600 .env.docker
```

`cp -n` avoids overwriting an existing local configuration. Open `.env.docker` in
your editor and fill in:

| Variable | How to choose it |
| --- | --- |
| `MST_HTTP_PORT` | Leave `8080`, or choose `8081` if your IDE already uses 8080. |
| `MST_DB_PASSWORD` | Generate a password for this new Docker database. |
| `SEARCHAPI_API_KEY` | Enter your real SearchAPI key for actual bio verification. |
| `MST_VERIFICATION_KEY_V1` | Generate a new 64-character hexadecimal secret for this installation. |
| `MST_TENANT_API_KEY_HASH_KEY` | Generate another independent 64-character hexadecimal secret. |

If `.env.docker` was already prepared for you, keep its generated values and fill
only missing fields. A blank SearchAPI field prevents Compose startup even though
startup itself makes no provider call. Keep actual secrets in `.env.docker`, never
in `docker.env.example`.

Generate a value using:

```sh
openssl rand -hex 32
```

Run it separately for the database password and each internal key. Copy each value
to the matching field. For an existing Docker installation, retain the saved values;
do not generate fresh keys on every start. Save the secrets together with your
database backup strategy. A lost tenant hash key makes existing tenant credentials
unusable.

IntelliJ run-configuration variables are not automatically available to Compose.
Exported variables in your terminal take precedence over the env file, so remove
conflicting shell exports if values appear to be ignored. If a value contains `$`
or `#`, use Compose's env-file quoting rules; single quotes preserve literal values.
See [Compose variable interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

### 4.3 Check configuration without printing secrets

```sh
docker compose --env-file .env.docker config --quiet
```

No output and a successful exit means the Compose configuration is valid. It does
not prove the database or provider credentials work. Omitting `--quiet` prints the
expanded configuration, including environment values, so do not paste that output
into public issues.

### 4.4 Build and start

```sh
docker compose --env-file .env.docker up --build -d --wait
```

Read the command as:

- `--env-file`: load the local configuration.
- `up`: create/start the services, network, and volume.
- `--build`: build the application image from this checkout.
- `-d`: run the containers in the background.
- `--wait`: wait for the services to become running/healthy before reporting success.

The first run downloads images and Maven dependencies, initializes PostgreSQL, and
applies Flyway V1–V4. Subsequent starts reuse the volume and apply only new migrations.
The database comes from PostgreSQL's initialization; the tables come from Flyway.
You do not manually execute the migration files.

Inspect the result:

```sh
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs --tail=100 app
curl --fail http://localhost:8080/actuator/health
```

Use your configured host port if it is not 8080. Health should return a body with
`"status":"UP"` (it may also list health groups). It checks application/database
health; it does not fetch a social profile or prove the SearchAPI key is valid.

### 4.5 Recognize a successful startup

In `ps`, both `app` and `db` should be running and healthy. Typical names are
`mst-social-app-1` and `mst-social-db-1`. Application logs should show successful
Flyway migration/validation, Tomcat starting on port 8080 inside the container,
and `Started MstSocialApplication`.

These messages alone are normal:

- `No active profile set`: the application is using its default configuration.
- `Found 0 JPA repository interfaces`: this project's repositories use JDBC.
- `Schema ... is up to date`: previously applied migrations do not run again.

The Spring Boot banner or `Tomcat initialized` alone does not mean startup finished.
`Exception encountered during context initialization`, `Connection refused`, or a
container repeatedly showing `Restarting` means you should inspect the error before
provisioning tenants. Logs ending in `Connection to localhost:5432 refused` from a
standalone image are not a successful Compose startup.

## 5. Create a tenant and its first API key

The database is new, so tenant IDs from your laptop's separate PostgreSQL database
do not automatically exist here. After the app has migrated the schema, run:

```sh
docker compose --env-file .env.docker exec app \
  java -jar /app/app.jar tenant create --name="Local Postman"
```

This starts an additional Java process inside `app`, using its existing database
configuration and hash key. The `tenant` argument selects the local administration
mode; it does not start a second web server or call SearchAPI. It inserts the tenant
row and issues that tenant's first credential in one step, printing:

```text
Tenant ID:     <generated tenant UUID>
Credential ID: <generated credential UUID>
Expires at:    never

Store this credential securely; it cannot be retrieved again:
mst_api_<credential UUID>.<64 hexadecimal characters>
```

Compose `exec` supplies an interactive terminal by default. **Do not add `-T`, pipe
the command, or redirect its output.** The command requires `System.console()` so the
one-time credential only ever appears in an interactive session; it refuses to run
otherwise, before touching the database.

Save the entire `mst_api_...` credential shown once. For Postman, store it in Local
Vault as `mst-social-api-key`. It is different from the SearchAPI key and the two
server hash keys. Create one tenant for your consuming application, not one for
every verification request.

To see existing tenants:

```sh
docker compose --env-file .env.docker exec db \
  psql -U mst_social -d mst_social_db \
  -c 'SELECT id, created_at FROM tenants ORDER BY created_at DESC;'
```

`exec db` means “run this command inside the already running database container.”
`psql` is already installed there; you do not need to install it on your laptop.
You can still create a tenant row on its own with
`INSERT INTO tenants DEFAULT VALUES RETURNING id;` and then issue its key with
`tenant-key create`; the single command above does both.

The usual project name is `mst-social`, so the volume is normally named
`mst-social_postgres_data`. If you choose another Compose project name with `-p`,
it gets a separate volume and network. Use the same project name on every command.

## 6. Add more keys, expiry, rotation, and revocation

One tenant can have several credentials, so staging, deployment, and rotation do not
require sharing one key. Step 5 issued the first key. To issue an additional key for
an existing tenant:

```sh
docker compose --env-file .env.docker exec app \
  java -jar /app/app.jar tenant-key create \
  --tenant-id="REPLACE_WITH_TENANT_UUID" \
  --name="Second key"
```

The same interactive-terminal rule applies. For command usage:

```sh
docker compose --env-file .env.docker exec app \
  java -jar /app/app.jar tenant-key --help
docker compose --env-file .env.docker exec app \
  java -jar /app/app.jar tenant --help
```

### Credential expiry, rotation, and revocation

Creation prints the credential UUID as well as the one-time secret. Save the ID for
administration. The key has no scheduled expiry unless you add
`--expires-at="REPLACE_WITH_FUTURE_ISO_8601_INSTANT"` to the create command; replace
that placeholder with a future UTC timestamp such as `2030-01-01T00:00:00Z`.

To rotate a key, create a second key for the same tenant, update the backend or
Postman Vault, and confirm `GET /api/v1/tenant` works with the new key. Then revoke
the old credential:

```sh
docker compose --env-file .env.docker exec app \
  java -jar /app/app.jar tenant-key revoke \
  --tenant-id="REPLACE_WITH_TENANT_UUID" \
  --credential-id="REPLACE_WITH_OLD_CREDENTIAL_UUID"
```

Use the **old credential UUID**, not the full secret, as `--credential-id`. A lost
plaintext key cannot be recovered from the database. Create a replacement and revoke
the lost key. Routine API-key rotation does not change `MST_TENANT_API_KEY_HASH_KEY`
and does not require an application restart.

## 7. Use Postman

Import the existing [collection](../mst-social-server/postman/MST-Social.postman_collection.json)
and [environment](../mst-social-server/postman/MST-Social-Local.postman_environment.json).
Set `baseUrl` to your published URL, usually `http://localhost:8080`.

Then follow [step 5 onward in the Postman guide](../mst-social-server/POSTMAN_GUIDE.md#5-import-the-postman-files-and-configure-variables):

1. Configure the Vault credential and environment.
2. Send Health and Who am I.
3. Create a verification and save its `requestId`.
4. Put the exact returned code in the social bio.
5. Send Verify with no body.
6. Save `account.accountId` and read the active association.

An eligible Verify call uses SearchAPI usage. Docker startup, health checks, tenant
creation, key provisioning, request creation, and status reads do not fetch profiles.
The collection's cancellation and revocation requests are optional state changes.

### Use Swagger UI instead of Postman

Compose enables interactive docs by default at
<http://localhost:8080/swagger-ui.html> (adjust the port if needed).
Click **Authorize** and paste your tenant key without the `Bearer ` prefix.
Follow the [Swagger walkthrough](SWAGGER_GUIDE.md) to create, inspect, verify,
and revoke an association. Execute sends real API requests; eligible verification
calls consume provider usage.

Set `MST_API_DOCS_ENABLED=false` in `.env.docker` to disable the docs, then apply it:

```sh
docker compose --env-file .env.docker up -d --wait app
```

The switch controls the UI and OpenAPI JSON/YAML routes. It does not remove
authentication from the API. Use `up` to apply environment changes; `restart` alone
keeps the old container environment.

### Detailed application logs

Set `MST_LOG_LEVEL=DEBUG` in `.env.docker` for additional detail, and optionally
`MST_LOG_FORMAT=logstash` for JSON. Apply changes with
`docker compose --env-file .env.docker up -d --wait app`, then follow
`docker compose --env-file .env.docker logs -f app`.
See the [logging guide](LOGGING_GUIDE.md) for request IDs and event meanings.

## 8. Daily commands and what they mean

| Task | Command |
| --- | --- |
| Show services and health | `docker compose --env-file .env.docker ps` |
| Follow application logs | `docker compose --env-file .env.docker logs -f app` |
| Follow database logs | `docker compose --env-file .env.docker logs -f db` |
| Stop services, keep their containers | `docker compose --env-file .env.docker stop` |
| Start existing stopped containers | `docker compose --env-file .env.docker start` |
| Restart the app process | `docker compose --env-file .env.docker restart app` |
| Rebuild after Java changes | `docker compose --env-file .env.docker up --build -d --wait` |
| Apply changed environment values | `docker compose --env-file .env.docker up -d --wait` |
| Remove containers/network, retain database volume | `docker compose --env-file .env.docker down` |
| Recreate services using the retained database | `docker compose --env-file .env.docker up -d --wait` |

Pressing Ctrl+C while following `logs -f` stops the log viewer; it does not stop
the background services. `restart` does not rebuild your JAR or reload changed
container environment configuration. Use `up` for those changes.

`docker compose ... down --volumes` also deletes this project's database volume.
That erases its tenants, credentials, verification history, and migration history.
It is not part of the normal stop/start workflow. Back up data before deliberate
resets. A named volume provides persistence, not an independent backup.

Changing `MST_DB_PASSWORD` in the env file does not change a password already stored
inside an initialized PostgreSQL volume. The image's initialization variables apply
when the data directory is empty. Keep the current password for ordinary restarts;
rotate it through PostgreSQL administration and update application configuration
together when needed.

### Back up the database and retain the corresponding secrets

This optional step creates a backup outside the Git checkout. Keep the database
running, run each command in the same Terminal session, and inspect any reported
errors before relying on the backup:

```sh
umask 077
mst_backup_dir="$HOME/mst-social-backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$mst_backup_dir"
docker compose --env-file .env.docker exec -T db \
  pg_dump -U mst_social -d mst_social_db --format=custom \
  > "$mst_backup_dir/mst-social.dump"
```

If `pg_dump` fails, the redirected file may exist but be incomplete. Resolve the
error and take another backup. Keep a protected copy of the matching environment
configuration too:

```sh
cp .env.docker "$mst_backup_dir/.env.docker"
chmod 600 "$mst_backup_dir/.env.docker"
```

These files contain private data and secrets; keep them in private storage outside
version control. If you intentionally use shell-exported secrets instead of the env
file's values, preserve those actual deployed values in your secret manager too.
The database dump does not include the server's HMAC secrets or PostgreSQL role
password configuration. [PostgreSQL documents the custom dump format here](https://www.postgresql.org/docs/17/app-pgdump.html).

### Restore a backup into a separate database

For a first recovery exercise, restore into a new database named
`mst_social_restore`, keeping the app's `mst_social_db` untouched. Use your own
backup directory below; `mst_backup_dir` still refers to it if you are in the same
Terminal session as the backup:

```sh
docker compose --env-file .env.docker exec -T db \
  createdb -U mst_social mst_social_restore
docker compose --env-file .env.docker exec -T db \
  pg_restore -U mst_social -d mst_social_restore \
  --exit-on-error --no-owner --no-privileges \
  < "$mst_backup_dir/mst-social.dump"
```

Continue to the restore only if database creation succeeds. If that name already
exists, choose a different new name in both commands. `-T` disables the terminal for
binary dump streaming; this differs from the interactive API-key creation command.
Restore only a trusted backup. See [PostgreSQL's restore documentation](https://www.postgresql.org/docs/17/app-pgrestore.html).

Inspect the restored schema history and tenants:

```sh
docker compose --env-file .env.docker exec -T db \
  psql -U mst_social -d mst_social_restore \
  -c 'SELECT version, success FROM flyway_schema_history ORDER BY installed_rank; SELECT id FROM tenants;'
```

The app still uses `mst_social_db`; this exercise does not switch it to the restored
database. Actual recovery requires coordinating the target database, connection
configuration, and matching secrets, with application writes stopped. These optional
backup/restore instructions were reviewed against PostgreSQL documentation; they
were not executed during the container checks recorded in section 12.

### Update application code or base images

After pulling an application change, rebuild and recreate the app with
`up --build -d --wait`. New migrations run automatically, so back up valuable data
before upgrading. Starting an older application image does not reverse a database
migration.

To refresh the configured base-image tags deliberately:

```sh
docker compose --env-file .env.docker build --pull app
docker compose --env-file .env.docker pull db
docker compose --env-file .env.docker up -d --wait
```

This keeps PostgreSQL on the configured major version 17. Review updates before
changing that major version. A database container can be briefly unavailable while
it is recreated; wait for healthy services before using the API again.

## 9. Use only the application image

Compose is the easiest local setup because it includes PostgreSQL. The same image
can also connect to an existing database:

```sh
docker build --tag mst-social:local .
```

Create a private, ignored `.env.server` file containing these entries:

```dotenv
MST_DB_URL=jdbc:postgresql://YOUR_DATABASE_HOST:5432/mst_social_db
MST_DB_USERNAME=YOUR_DATABASE_ROLE
MST_DB_PASSWORD=YOUR_DATABASE_PASSWORD
SEARCHAPI_API_KEY=YOUR_SEARCHAPI_KEY
MST_VERIFICATION_KEY_V1=YOUR_64_HEX_SECRET
MST_TENANT_API_KEY_HASH_KEY=YOUR_DIFFERENT_64_HEX_SECRET
```

Replace every placeholder before using it, then run:

```sh
docker run --name mst-social-api \
  --env-file .env.server \
  --publish 127.0.0.1:8080:8080 \
  --init --read-only --tmpfs /tmp:rw,nosuid,nodev,size=64m,mode=1777 \
  --cap-drop ALL --security-opt no-new-privileges:true \
  --detach mst-social:local
```

The database must already exist and be reachable from the container. On Docker
Desktop, `host.docker.internal` can reach a database on the laptop, if PostgreSQL
is configured to accept that connection. Linux host access requires its own network
configuration. `localhost` inside the container still refers to the container.

The plain `docker run --env-file` syntax does not have all Compose interpolation
features. Use literal values in that file. Stop this standalone container with
`docker stop mst-social-api`. It is independent of the Compose project.

## 10. Common mistakes and how to diagnose them

| Symptom | Explanation / next action |
| --- | --- |
| Cannot connect to Docker daemon | Start Docker Desktop and wait for its engine. |
| `no configuration file provided` | Open Terminal in the repository root containing `compose.yaml`. |
| `unknown flag: --wait` | Update Docker Desktop/Compose to a version supporting `up --wait`. This setup was checked with Compose 2.39.4. |
| App logs say `Connection to localhost:5432 refused` after clicking Run on the image | Start with `docker compose --env-file .env.docker up --build -d --wait`; the image's Run button does not apply the Compose services or environment. |
| Required variable error | Fill the blank field in `.env.docker` and pass `--env-file .env.docker`. |
| Env file changes seem ignored | Check exported terminal variables, which override Compose env-file values; recreate the service with `up`. |
| Port already allocated | Set `MST_HTTP_PORT=8081` and use that port in Postman, or stop the other server. |
| Database connection goes to localhost | In Compose the app uses the hostname `db`; localhost is the wrong container. |
| Database authentication fails after editing the password | An existing volume retains the original database password. Editing initialization variables does not rotate it. |
| App fails during startup | Read `logs --tail=100 app`. Check secret lengths, PostgreSQL health, and Flyway errors. |
| API key gives 401 | Create/use a key from this Docker database and the same server hash key. Your IDE database's key is not automatically copied here. |
| Cannot reach PostgreSQL from pgAdmin on localhost:5432 | Compose deliberately does not publish its database port. Use `exec db psql`, or configure a deliberate local-only port mapping if needed. |
| `tenant` or `tenant-key` says an interactive console is required | Run the documented `exec app` command in Terminal without `-T`, redirection, or pipes. |
| Changed Java code has no effect | Rebuild and recreate the app with `up --build`; restarting the old container uses its old image. |
| Health works but verification fails | Health does not verify provider credentials, provider availability, or the social bio. Inspect the API error response. |
| An image download or Maven dependency download fails | Check network/registry connectivity, resolve the reported error, then rerun the build. |

If startup fails, use these scoped commands before changing configuration:

```sh
docker compose --env-file .env.docker ps -a
docker compose --env-file .env.docker logs --tail=100 db
docker compose --env-file .env.docker logs --tail=100 app
```

A missing-variable error must be resolved first because Compose parses the env file
for these commands too. Keep credentials out of shared log excerpts. Avoid deleting
volumes or using Docker's global prune commands as a generic startup fix.

## 11. What this does and does not publish

`mst-social:local` is an image stored on your machine. Building it does not upload
it to Docker Hub or GitHub Container Registry. Other developers can clone the repo
and build it using these files. Publishing versioned images and an automated image
release workflow is a later step.

There is no hard-coded CPU architecture in the Dockerfile or Compose file. A normal
local build uses your machine's architecture. Producing a published image for both
AMD64 and ARM64 requires a separate multi-platform build and verification process.

This setup adds packaging and documentation; it does not change the Java request
models, database schema, or verification rules. Container builds skip automated
tests.

## 12. What was checked

See the [verification log](VERIFICATION_LOG.md#2026-10-18--docker-packaging-and-compose)
for the container build, startup, provisioning, and persistence checks performed on
this setup, and for what was not exercised. The optional backup and restore
instructions above were reviewed against PostgreSQL documentation but not executed.
