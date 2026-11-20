#!/bin/sh
# Runs Liquibase for every service schema, directly against Postgres (NOT through PgBouncer:
# Liquibase needs session features like advisory locks). Runs once per "up", before any service starts.
set -eu
run() { # service schema user password
  echo "== migrating $1 (schema $2)"
  liquibase --log-level=warning \
    --search-path="/changelogs/$1,/changelogs/common" \
    --changelog-file=db/changelog/db.changelog-master.yaml \
    --url="jdbc:postgresql://postgres:5432/ipp?currentSchema=$2" \
    --username="$3" --password="$4" \
    --default-schema-name="$2" --liquibase-schema-name="$2" \
    update
}
run registry-service    registry    registry_svc    "$REGISTRY_DB_PASSWORD"
run permit-service      permits     permits_svc     "$PERMITS_DB_PASSWORD"
run eligibility-service eligibility eligibility_svc "$ELIGIBILITY_DB_PASSWORD"
run enforcement-service enforcement enforcement_svc "$ENFORCEMENT_DB_PASSWORD"
echo "== migrations done"
