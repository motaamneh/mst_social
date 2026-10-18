# mst_social

An open-source Java project for verifying control of social accounts through temporary profile-bio verification codes.

The repository contains the core verification workflows, a SearchAPI provider, and a Spring Boot verification API with PostgreSQL storage and tenant API-key authentication. Run it locally with Docker Compose or build the Java artifacts directly.

## Run locally with Docker

With Docker Desktop running, create your local configuration from the template:

```sh
cp -n docker.env.example .env.docker
chmod 600 .env.docker
```

Fill the blank secrets in `.env.docker`, then start the API and PostgreSQL:

```sh
docker compose --env-file .env.docker up --build -d --wait
```

The API is available at `http://localhost:8080`; PostgreSQL stays on the internal
Compose network and stores its data in a named volume. Flyway creates the tables.
No local Java or Maven installation is needed for this path. The build skips tests.

Follow the [Docker beginner guide](docs/DOCKER_GUIDE.md) for a step-by-step explanation
of the Dockerfile, Compose, secrets, tenant creation, API keys, and Postman. The image
is built locally as `mst-social:local`; a published registry image is not yet provided.

## Modules

| Module | Responsibility | Artifact |
| --- | --- | --- |
| `mst-social-core` | Plain Java domain, use cases, and provider/storage interfaces | Library JAR |
| `mst-social-provider-searchapi` | SearchAPI adapter depending on the core | Library JAR |
| `mst-social-server` | Spring Boot HTTP service, configuration, and PostgreSQL persistence | Executable JAR |

The root `pom.xml` is the parent and reactor aggregator. It centralizes Java 21 and dependency versions using the existing Spring Boot parent. Library modules do not inherit Spring runtime dependencies; the Boot packaging plugin is enabled only in the server.

Dependency direction:

```text
server -> core
server -> provider-searchapi -> core
```

Maven coordinates use `com.motaamneh`, and Java packages begin with `com.motaamneh.mstsocial`.

Request DTOs, domain values, configuration, and internal data holders are ordinary
Java classes. Only HTTP response DTOs under the server's `web/dto/response` package
use records. The core remains independent of Spring, Jackson, and Lombok.

## Build

For a complete first run, follow the [tenant setup and Postman guide](mst-social-server/POSTMAN_GUIDE.md).
It includes tenant ID creation, API-key provisioning, an importable collection and
environment, and the full Instagram/TikTok verification flow.

Install JDK 21, then run from the repository root:

```sh
./mvnw -Dmaven.test.skip=true clean package
```

This builds all modules in dependency order with test compilation and execution disabled.

Artifacts are written under each module's `target/` directory:

```text
mst-social-core/target/mst-social-core-0.0.1-SNAPSHOT.jar
mst-social-provider-searchapi/target/mst-social-provider-searchapi-0.0.1-SNAPSHOT.jar
mst-social-server/target/mst-social-server-0.0.1-SNAPSHOT.jar
```

The core contains [verification models, restoration, and workflow rules](mst-social-core/README.md). The [SearchAPI provider](mst-social-provider-searchapi/README.md) retrieves Instagram and TikTok biographies. Follow the [server setup and credential guide](mst-social-server/README.md) to configure the database and secrets and provision a tenant key. Then use the [verification API guide](mst-social-server/VERIFICATION_API.md) to create, check, verify, and cancel requests or read and revoke verified associations. The [OpenAPI contract](mst-social-server/openapi.yaml) can be imported into Postman.

To build only the server and the modules it needs:

```sh
./mvnw -Dmaven.test.skip=true -pl mst-social-server -am package
```

In IntelliJ IDEA, open the root `pom.xml` as a Maven project, or select **Reload All Maven Projects** if it is already open. Maven will discover all three modules.

## License

[Apache License 2.0](LICENSE).
