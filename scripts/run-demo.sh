#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command curl
require_command jq
require_command aws

readonly API_URL="${API_URL:-http://localhost:8080}"
readonly IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-webinar-order-001}"
payload='{"customerId":"customer-101","items":[{"sku":"KEYBOARD-01","quantity":1,"unitPrice":2499.00},{"sku":"MOUSE-02","quantity":2,"unitPrice":799.00}]}'
response="$(curl -fsS -X POST "$API_URL/api/v1/orders" -H 'Content-Type: application/json' -H "Idempotency-Key: $IDEMPOTENCY_KEY" -H 'X-Correlation-Id: webinar-demo-001' -d "$payload")"
echo "$response" | jq .
order_id="$(jq -r .orderId <<<"$response")"

for _ in {1..30}; do
  order="$(curl -fsS "$API_URL/api/v1/orders/$order_id")"
  [[ "$(jq -r .status <<<"$order")" == "COMPLETED" ]] && break
  sleep 1
done
[[ "$(jq -r .status <<<"$order")" == "COMPLETED" ]] || { echo "Order did not complete" >&2; exit 1; }
echo "$order" | jq .
aws_local s3 cp "s3://order-receipts/receipts/$order_id.json" - | jq .

duplicate="$(curl -fsS -X POST "$API_URL/api/v1/orders" -H 'Content-Type: application/json' -H "Idempotency-Key: $IDEMPOTENCY_KEY" -d "$payload")"
[[ "$(jq -r .orderId <<<"$duplicate")" == "$order_id" ]] || { echo "API idempotency failed" >&2; exit 1; }
count="$(aws_local s3api list-objects-v2 --bucket order-receipts --prefix "receipts/$order_id.json" --query KeyCount --output text)"
[[ "$count" == "1" ]] || { echo "Expected exactly one receipt" >&2; exit 1; }
echo "Demo passed for order $order_id; duplicate returned the same order and one receipt exists."
