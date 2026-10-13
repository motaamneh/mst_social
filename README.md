# mst_social

An open-source Java project for verifying control of social accounts through temporary profile-bio verification codes.

The repository contains the core verification workflows, a SearchAPI provider, and a Spring Boot server with PostgreSQL storage and tenant API-key authentication. Verification HTTP endpoints and Docker packaging are the next implementation steps.

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

## Build

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

The core contains [verification models, restoration, and workflow rules](mst-social-core/README.md). The [SearchAPI provider](mst-social-provider-searchapi/README.md) retrieves Instagram and TikTok biographies. The server includes JDBC persistence, Flyway migrations, and stateless tenant API-key authentication. Follow the [server setup and credential guide](mst-social-server/README.md) to configure the database and secrets, provision a tenant key, and call `GET /api/v1/tenant`. Verification HTTP endpoints are not implemented yet.

To build only the server and the modules it needs:

```sh
./mvnw -Dmaven.test.skip=true -pl mst-social-server -am package
```

In IntelliJ IDEA, open the root `pom.xml` as a Maven project, or select **Reload All Maven Projects** if it is already open. Maven will discover all three modules.

## License

[Apache License 2.0](LICENSE).
