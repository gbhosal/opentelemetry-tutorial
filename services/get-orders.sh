#!/usr/bin/env bash
set -euo pipefail

count="${1:-20}"
for i in $(seq 1 "$count"); do
  post_file="$(mktemp)"
  post_code="$(curl -sS --max-time 10 -o "$post_file" -w "%{http_code}" \
    -X POST "http://localhost:8081/orders" \
    -H "Content-Type: application/json" \
    -d "{\"customerId\":\"c-${i}\",\"sku\":\"book\",\"amount\":42.50}")"
  id="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$post_file")"
  rm -f "$post_file"
  if [[ -z "$id" ]]; then
    echo "${i} POST ${post_code} no order id" >&2
    continue
  fi

  get_file="$(mktemp)"
  get_code="$(curl -sS --max-time 10 -o "$get_file" -w "%{http_code}" \
    "http://localhost:8081/orders/${id}")"
  echo "${i} ${get_code} $(cat "$get_file")"
  rm -f "$get_file"
done
