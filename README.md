# mst_social

An open-source Java project for verifying control of social accounts through temporary profile-bio verification codes.

The repository contains the Maven module structure, the core verification models, and a Spring Boot application scaffold. Code generation and matching, service workflows, the SearchAPI integration, database migrations, and Docker packaging are future implementation steps.

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

The core contains [verification models, restoration, and workflow rules](mst-social-core/README.md). The provider library is a scaffold. The server includes a JDBC PostgreSQL verification store and a Flyway migration. Configure `MST_DB_URL`, `MST_DB_USERNAME`, and `MST_DB_PASSWORD` as described in the [database setup guide](mst-social-server/README.md). Tenant authentication, SearchAPI integration, and verification endpoints are not implemented yet.

To build only the server and the modules it needs:

```sh
./mvnw -Dmaven.test.skip=true -pl mst-social-server -am package
```

In IntelliJ IDEA, open the root `pom.xml` as a Maven project, or select **Reload All Maven Projects** if it is already open. Maven will discover all three modules.

## License

[Apache License 2.0](LICENSE).
