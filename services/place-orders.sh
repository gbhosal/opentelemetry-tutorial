#!/usr/bin/env bash
set -euo pipefail

count="${1:-20}"
for i in $(seq 1 "$count"); do
  body_file="$(mktemp)"
  code="$(curl -sS --max-time 10 -o "$body_file" -w "%{http_code}" \
    -X POST "http://localhost:8081/orders" \
    -H "Content-Type: application/json" \
    -d "{\"customerId\":\"c-${i}\",\"sku\":\"book\",\"amount\":42.50}")"
  echo "${i} ${code} $(cat "$body_file")"
  rm -f "$body_file"
done
