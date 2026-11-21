#!/usr/bin/env bash
# Imports infra/nifi/flow/ipp-ingest.flow.json into a running NiFi through its REST API, enables the
# Kafka controller service and starts the group. Same result as the manual UI steps in docs/NIFI.md.
# Usage (Git Bash): bash scripts/ingest/nifi-import.sh      (NiFi up: docker compose --profile core --profile nifi up -d nifi)
set -euo pipefail
cd "$(dirname "$0")/../.."
[ -f .env ] && { set -a; . ./.env; set +a; }
N=https://localhost:8443/nifi-api
USER_="${NIFI_USERNAME:-ipp-admin}"; PASS="${NIFI_PASSWORD:?NIFI_PASSWORD missing in .env}"
NAME="IPP ingest (whitening)"
json() { node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{const j=JSON.parse(s);console.log($1)})"; }

echo "waiting for NiFi (first start takes ~1-2 minutes)..."
curl -sk -o /dev/null --retry 60 --retry-delay 5 --retry-all-errors "$N/access/config" || true
T=$(curl -skf -X POST "$N/access/token" --data-urlencode "username=$USER_" --data-urlencode "password=$PASS")
H=(-H "Authorization: Bearer $T")
ROOT=$(curl -skf "${H[@]}" "$N/flow/process-groups/root" | json 'j.processGroupFlow.id')

PG=$(curl -skf "${H[@]}" "$N/process-groups/$ROOT/process-groups" \
  | json "(j.processGroups.find(g=>g.component.name==='$NAME')||{}).id||''")
if [ -z "$PG" ]; then
  PG=$(curl -skf "${H[@]}" -X POST "$N/process-groups/$ROOT/process-groups/upload" \
    --form-string "groupName=$NAME" --form-string positionX=0 --form-string positionY=0 \
    --form-string clientId=ipp-import -F file=@infra/nifi/flow/ipp-ingest.flow.json | json 'j.id')
  echo "imported group $PG"
else
  echo "group already imported ($PG), only (re)starting it"
fi

curl -skf "${H[@]}" -H 'Content-Type: application/json' -X PUT "$N/flow/process-groups/$PG/controller-services" \
  -d "{\"id\":\"$PG\",\"state\":\"ENABLED\"}" -o /dev/null
curl -skf "${H[@]}" -H 'Content-Type: application/json' -X PUT "$N/flow/process-groups/$PG" \
  -d "{\"id\":\"$PG\",\"state\":\"RUNNING\"}" -o /dev/null
curl -skf "${H[@]}" "$N/process-groups/$PG/processors" \
  | json "j.processors.map(p=>p.component.state.padEnd(9)+p.component.validationStatus.padEnd(9)+p.component.name).join('\n')"
echo "done: https://localhost:8443/nifi  (try: node scripts/ingest/make-nifi-batch.mjs ok)"
