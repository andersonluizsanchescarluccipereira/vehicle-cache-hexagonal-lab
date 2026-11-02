#!/usr/bin/env bash
set -Eeuo pipefail
BASE="${BASE_URL:-http://localhost:8080/api/v1/veiculos}"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

echo '[1/7] health'
curl -fsS "${BASE%/api/v1/veiculos}/actuator/health"
echo

echo '[2/7] POST create'
RESPONSE="$(curl -fsS -X POST "$BASE" -H 'Content-Type: application/json' -d '{"marca":"Toyota","modelo":"Corolla","ano":2024,"valor":125000.00}')"
ID="$(printf '%s' "$RESPONSE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
echo "Criado id=$ID; body=$RESPONSE"

echo '[3/7] DEL cache to force MISS'
docker compose exec -T redis redis-cli DEL "vehicle:$ID" >/dev/null
curl -fsS -D "$TMP" "$BASE/$ID" >/dev/null
grep -iq '^X-Cache: MISS' "$TMP" || { echo 'FAIL: era esperado MISS'; cat "$TMP"; exit 1; }
echo 'MISS OK'

echo '[4/7] GET HIT'
curl -fsS -D "$TMP" "$BASE/$ID" >/dev/null
grep -iq '^X-Cache: HIT' "$TMP" || { echo 'FAIL: era esperado HIT'; cat "$TMP"; exit 1; }
echo 'HIT OK'

echo '[5/7] PUT value update'
curl -fsS -X PUT "$BASE/$ID" -H 'Content-Type: application/json' -d '{"marca":"Toyota","modelo":"Corolla","ano":2024,"valor":130000.00}' >/dev/null
BODY="$(curl -fsS "$BASE/$ID")"
printf '%s' "$BODY" | python3 -c 'import json,sys; assert float(json.load(sys.stdin)["valor"])==130000.00'
echo 'UPDATE OK'

echo '[6/7] DELETE'
curl -fsS -X DELETE "$BASE/$ID" >/dev/null
STATUS="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/$ID")"
[[ "$STATUS" == '404' ]] || { echo "FAIL: esperado 404, recebido $STATUS"; exit 1; }
echo 'DELETE OK'

echo '[7/7] Stream and outbox'
docker compose exec -T redis redis-cli XLEN stream:veiculos || true
echo 'PASS: fluxo HTTP/cache validado. O relay publica eventos a cada ~5s.'
