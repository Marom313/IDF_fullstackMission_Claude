# syntax=docker/dockerfile:1.7
# One multi-stage Dockerfile for every Spring service (compose passes --build-arg SERVICE=<name>).
# The build stage does NOT depend on SERVICE, so BuildKit builds the Maven reactor ONCE and every
# service image reuses that cached layer (saves CPU/RAM on the dev machine).
# Cache mount keeps ~/.m2 between builds; MAVEN_MIRROR_URL points to Nexus in the closed network.
FROM maven:3.9.11-eclipse-temurin-21 AS build
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
WORKDIR /ws
COPY services/pom.xml services/pom.xml
COPY libs libs
COPY services services
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    printf '<settings><mirrors><mirror><id>ipp</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /tmp/settings.xml && \
    MAVEN_OPTS="-Xmx768m -XX:+UseSerialGC" mvn -B -q -s /tmp/settings.xml -f services/pom.xml \
      package -DskipTests -Dcyclonedx.skip=true && \
    for jar in services/*/target/*.jar; do \
      svc=$(basename "$(dirname "$(dirname "$jar")")"); \
      case "$jar" in *-plain.jar) continue;; esac; \
      java -Djarmode=tools -jar "$jar" extract --layers --launcher --destination /out/$svc; \
    done

FROM eclipse-temurin:21.0.8_9-jre-alpine
ARG SERVICE
# font-dejavu: Hebrew-capable font for the SPECIMEN PDF (no runtime download – closed network)
RUN apk add --no-cache font-dejavu && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /out/${SERVICE}/dependencies/ ./
COPY --from=build /out/${SERVICE}/spring-boot-loader/ ./
COPY --from=build /out/${SERVICE}/snapshot-dependencies/ ./
COPY --from=build /out/${SERVICE}/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
