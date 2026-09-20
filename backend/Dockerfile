# Render has no native Java runtime, so the backend ships as a container.
# Build context is the `backend` directory.

# --- build -------------------------------------------------------------------
# Same Maven and JDK as the wrapper (.mvn/wrapper/maven-wrapper.properties) and pom.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first: this layer is reused while only sources change.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests are not run here: the unit tests belong in CI, and the integration tests
# need Docker, which is not available inside an image build.
RUN mvn -B -ntp -DskipTests package

# --- run ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Unprivileged user; the application never writes to the filesystem.
RUN useradd --system --create-home --shell /usr/sbin/nologin acme
COPY --from=build /build/target/salary-management.jar app.jar
USER acme

# Documentation only: the platform sets PORT (Render defaults to 10000) and
# application.yml reads it. Spring Boot already binds 0.0.0.0.
EXPOSE 8080

# Exec form, so the JVM is PID 1 and receives the platform's shutdown signal.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
