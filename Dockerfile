# syntax=docker/dockerfile:1

# Build the complete Maven reactor with the repository's Maven Wrapper.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY --chmod=0755 mvnw ./mvnw
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
COPY pom.xml ./pom.xml
COPY mst-social-core/pom.xml mst-social-core/pom.xml
COPY mst-social-provider-searchapi/pom.xml mst-social-provider-searchapi/pom.xml
COPY mst-social-server/pom.xml mst-social-server/pom.xml
COPY mst-social-core/src/main/ mst-social-core/src/main/
COPY mst-social-provider-searchapi/src/main/ mst-social-provider-searchapi/src/main/
COPY mst-social-server/src/main/ mst-social-server/src/main/

# The cache speeds up later builds; it is not copied into the runtime image.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

# The runtime needs Java, the executable JAR, and curl for the health check.
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 mstsocial \
    && useradd --uid 10001 --gid mstsocial --no-create-home \
        --home-dir /app --shell /usr/sbin/nologin mstsocial

WORKDIR /app
COPY --from=build /workspace/mst-social-server/target/mst-social-server-*.jar /app/app.jar
USER 10001:10001

# EXPOSE documents the container port; Compose publishes it on the host.
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent --show-error --max-time 3 http://127.0.0.1:8080/actuator/health > /dev/null || exit 1

# Additional arguments, such as tenant-key create, reach the existing main method.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
