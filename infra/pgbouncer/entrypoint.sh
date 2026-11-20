#!/bin/sh
# Generate userlist from env at start (secrets never baked into the image).
set -eu
cat > /etc/pgbouncer/userlist.txt <<U
"registry_svc" "${REGISTRY_DB_PASSWORD}"
"permits_svc" "${PERMITS_DB_PASSWORD}"
"eligibility_svc" "${ELIGIBILITY_DB_PASSWORD}"
"enforcement_svc" "${ENFORCEMENT_DB_PASSWORD}"
U
exec pgbouncer /etc/pgbouncer/pgbouncer.ini
