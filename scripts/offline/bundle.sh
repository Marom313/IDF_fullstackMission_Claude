#!/usr/bin/env bash
# Builds a transferable bundle for the closed network (run on the CONNECTED side).
#   .offline-bundle/images.tar      all images used by compose (pinned tags) + built ipp/* images
#   .offline-bundle/m2.tar          Maven repository (dependency:go-offline)
#   .offline-bundle/npm-cache.tar   npm cache for apps/web (npm ci --cache)
#   .offline-bundle/SHA256SUMS      integrity list (sign it at the organisational gateway)
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=.offline-bundle
mkdir -p "$OUT"
export MSYS_NO_PATHCONV=1

echo "▶ building images"
docker compose --profile core --profile ingest build
IMAGES=$(docker compose --profile core --profile ingest config --images | sort -u)
EXTRA="maven:3.9.11-eclipse-temurin-21 eclipse-temurin:21.0.8_9-jre-alpine node:24.11.1-alpine alpine:3.22 zricethezav/gitleaks:v8.28.0 grafana/k6:0.57.0"
echo "▶ docker save ($(echo $IMAGES $EXTRA | wc -w) images)"
for i in $EXTRA; do docker pull -q "$i" >/dev/null; done
docker save -o "$OUT/images.tar" $IMAGES $EXTRA

echo "▶ maven go-offline"
docker run --rm -v "$PWD:/ws" -v "$PWD/$OUT/m2:/root/.m2" -w /ws/services --memory 2g \
  maven:3.9.11-eclipse-temurin-21 mvn -B -q dependency:go-offline -Dcyclonedx.skip=true
tar -C "$OUT" -cf "$OUT/m2.tar" m2 && rm -rf "$OUT/m2"

echo "▶ npm cache"
docker run --rm -v "$PWD/apps/web:/web" -v "$PWD/$OUT/npm-cache:/cache" -w /web node:24.11.1-alpine \
  npm ci --cache /cache --prefer-offline --no-audit --no-fund
tar -C "$OUT" -cf "$OUT/npm-cache.tar" npm-cache && rm -rf "$OUT/npm-cache"

( cd "$OUT" && sha256sum images.tar m2.tar npm-cache.tar > SHA256SUMS )
echo "✔ bundle ready in $OUT ($(du -sh "$OUT" | cut -f1))"
