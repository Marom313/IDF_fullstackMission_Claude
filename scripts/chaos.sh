#!/usr/bin/env bash
# Live-demo chaos switch: take mock-state-api "down" (true) or bring it back (false).
set -euo pipefail
ON=${1:-true}
T=$(curl -s -X POST http://localhost/auth/realms/ipp/protocol/openid-connect/token -d grant_type=password \
  -d client_id=ipp-web -d username=admin1 -d 'password=Passw0rd!' | node -pe 'JSON.parse(require("fs").readFileSync(0)).access_token')
curl -s -X PUT -H "Authorization: Bearer $T" -H "Content-Type: application/json" http://localhost/api/chaos \
  -d "{\"outage\":$ON}"; echo
