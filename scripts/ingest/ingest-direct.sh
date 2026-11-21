#!/usr/bin/env bash
# Fallback without NiFi: the same verifier + the same Kafka messages, run by hand. For the demo when the
# 1.5GB NiFi container is not up. Requires the core stack (kafka) and the nifi image to be BUILT (not running):
#   docker compose --profile nifi build nifi
#   bash scripts/ingest/ingest-direct.sh ingest/inbox/crossings-...-ok-xxxx.zip
# Exit code and archive/quarantine handling match the NiFi flow.
set -euo pipefail
cd "$(dirname "$0")/../.."

zip="${1:?usage: ingest-direct.sh <batch.zip>}"
name="$(basename "$zip")"
out="$(mktemp)"; err="$(mktemp)"
trap 'rm -f "$out" "$err"' EXIT

set +e
docker run --rm -i --network none \
  -v "$PWD/infra/ingest/keys:/opt/ipp/keys:ro" -v "$PWD/ingest:/ingest" \
  --entrypoint java ipp/nifi:0.1.0 -jar /opt/ipp/ingest-verifier.jar \
  --key /opt/ipp/keys/ingest.pub.pem --receipts /ingest/.receipts --name "$name" --format console \
  < "$zip" > "$out" 2> "$err"
code=$?
set -e
echo "verifier exit $code: $(cat "$err")"
[ "$code" -eq 2 ] && exit 2

# one producer per topic; lines are "topic<TAB>key<TAB>json"
for topic in $(cut -f1 "$out" | sort -u); do
  n=$(awk -F'\t' -v t="$topic" '$1==t' "$out" | wc -l)
  awk -F'\t' -v t="$topic" 'BEGIN{OFS="\t"} $1==t {print $2, $3}' "$out" \
    | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 \
        --topic "$topic" --property parse.key=true --property "key.separator=$(printf '\t')" >/dev/null
  echo "published $n message(s) to $topic"
done

if [ "$code" -eq 0 ]; then
  mv "$zip" "ingest/archive/$name"; echo "archived ingest/archive/$name"
else
  mv "$zip" "ingest/quarantine/$name"; cp "$err" "ingest/quarantine/$name.reason"; echo "quarantined ingest/quarantine/$name"
fi
exit "$code"
