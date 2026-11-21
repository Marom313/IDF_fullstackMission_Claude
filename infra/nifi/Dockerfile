# NiFi + the IPP ingest verifier. Build context = repo root:
#   docker compose --profile core --profile nifi build nifi
# Stage 1 compiles the verifier with the plain JDK (no Maven downloads: it has zero dependencies),
# so the image also builds on the closed network from the offline bundle.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY libs/ingest-verifier/src/main/java ./java
RUN mkdir -p classes \
 && javac --release 21 -d classes $(find java -name '*.java') \
 && jar --create --file ingest-verifier.jar --main-class il.ipp.ingest.verifier.IngestVerifier -C classes .

FROM apache/nifi:2.9.0
COPY --from=build --chown=nifi:nifi /src/ingest-verifier.jar /opt/ipp/ingest-verifier.jar
COPY --chown=nifi:nifi infra/nifi/flow/ipp-ingest.flow.json /opt/ipp/ipp-ingest.flow.json
