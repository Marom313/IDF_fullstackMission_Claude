#!/usr/bin/env bash
# ipp verify – lint, tests, build, SBOM. Must be green before every commit.
# Runs the same on Windows (Git Bash), WSL, CI and offline (all tools run in pinned containers).
set -euo pipefail
cd "$(dirname "$0")/.."
step() { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m✖ %s\033[0m\n' "$*"; exit 1; }
MAVEN_IMAGE="maven:3.9.11-eclipse-temurin-21"
export MSYS_NO_PATHCONV=1

step "compose config (all profiles)"
[ -f .env ] || cp .env.example .env
docker compose --profile core --profile ingest --profile observability config -q

step "config comment rule ([MANUAL CHANGE – Marom] above every changed parameter)"
bash scripts/check-config-comments.sh

step "secrets scan (gitleaks)"
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.28.0 dir /repo --no-banner --redact \
  --config /repo/.gitleaks.toml || fail "gitleaks found secrets"

if [ -f services/pom.xml ]; then
  step "backend: mvn verify (tests + ArchUnit + SBOM)"
  # docker socket → Testcontainers starts real Mongo/Postgres/Kafka for integration tests
  docker run --rm -v "$PWD:/ws" -v ipp-m2:/root/.m2 -w /ws/services \
    -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    -e TESTCONTAINERS_RYUK_DISABLED=true --add-host host.docker.internal:host-gateway \
    -e MAVEN_OPTS="-Xmx1g -XX:+UseSerialGC" --memory 2g --cpus 2 \
    "$MAVEN_IMAGE" mvn -q -B -T 2 verify ${MVN_ARGS:-}
fi

if [ -f apps/web/package.json ]; then
  step "frontend: lint, i18n, typecheck, tests, build"
  ( cd apps/web && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npm run verify )
fi

printf '\n\033[1;32m✔ verify green\033[0m\n'
