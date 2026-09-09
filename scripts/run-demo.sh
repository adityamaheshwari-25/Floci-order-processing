#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command curl
require_command jq
require_command aws

readonly API_URL="${API_URL:-http://localhost:8080}"
readonly IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-webinar-order-001}"
payload='{"customerId":"customer-101","items":[{"sku":"KEYBOARD-01","quantity":1,"unitPrice":2499.00},{"sku":"MOUSE-02","quantity":2,"unitPrice":799.00}]}'
response="$(curl -fsS -X POST "$API_URL/api/v1/orders" -H 'Content-Type: application/json' -H "Idempotency-Key: $IDEMPOTENCY_KEY" -H 'X-Correlation-Id: webinar-demo-001' -d "$payload")"
echo "1. API response: order created or existing order returned"
echo "$response" | jq .
order_id="$(jq -r .orderId <<<"$response")"

for _ in {1..30}; do
  order="$(curl -fsS "$API_URL/api/v1/orders/$order_id")"
  [[ "$(jq -r .status <<<"$order")" == "COMPLETED" ]] && break
  sleep 1
done
[[ "$(jq -r .status <<<"$order")" == "COMPLETED" ]] || { echo "Order did not complete" >&2; exit 1; }
echo "2. API response: processing completed"
echo "$order" | jq .
echo "3. Receipt downloaded from S3"
aws_local s3 cp "s3://order-receipts/receipts/$order_id.json" - | jq .

duplicate="$(curl -fsS -X POST "$API_URL/api/v1/orders" -H 'Content-Type: application/json' -H "Idempotency-Key: $IDEMPOTENCY_KEY" -d "$payload")"
[[ "$(jq -r .orderId <<<"$duplicate")" == "$order_id" ]] || { echo "API idempotency failed" >&2; exit 1; }
# Count returned objects; KeyCount is per-page metadata and may be absent from
# the AWS CLI's aggregated paginated output.
listing="$(aws_local s3api list-objects-v2 --bucket order-receipts --prefix "receipts/$order_id.json" --output json)"
jq -e --arg key "receipts/$order_id.json" \
  '(.Contents // []) | length == 1 and .[0].Key == $key' \
  <<<"$listing" >/dev/null || {
  echo "Expected exactly one receipt with the matching object key. S3 listing:" >&2
  echo "$listing" >&2
  exit 1
}
echo "Demo passed for order $order_id."
