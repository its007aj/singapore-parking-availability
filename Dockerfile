# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies before copying source, so source-only changes don't bust this cache layer.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
# SKIP_TESTS defaults to true so a plain `docker compose up --build` behaves exactly as
# before; pass --build-arg SKIP_TESTS=false (or set it in docker-compose.yml's build.args)
# to have the image build fail if the test suite fails, for a stricter CI-style build.
#
# Even with SKIP_TESTS=false, SingaporeApplicationTests is excluded here: it's a
# @SpringBootTest needing a live Postgres connection, and `docker build` runs in an
# isolated context with no database reachable (containers only get networked together at
# `docker compose up`, not during an image build) — it would fail every time regardless
# of whether the rest of the suite passes. The docker-compose.yml `test` service runs the
# full suite (including this one) as a container command after Postgres is actually up.
ARG SKIP_TESTS=true
RUN if [ "$SKIP_TESTS" = "true" ]; then \
      ./mvnw -q -B -DskipTests package; \
    else \
      ./mvnw -q -B "-Dtest=!SingaporeApplicationTests" package; \
    fi

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
