# syntax=docker/dockerfile:1.7
# Multi-stage build. Base images are pinned; in the closed network DOCKER_REGISTRY points to the internal mirror.
ARG DOCKER_REGISTRY=docker.io
FROM ${DOCKER_REGISTRY}/library/maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package && cp target/policy-service-*.jar /app.jar

FROM ${DOCKER_REGISTRY}/library/eclipse-temurin:21.0.8_9-jre-alpine
RUN addgroup -S ipp && adduser -S ipp -G ipp
USER ipp
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8084
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD wget -qO- http://localhost:8084/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
