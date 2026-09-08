#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command aws

queue_url="$(aws_local sqs get-queue-url --queue-name order-events --query QueueUrl --output text)"
poison='{"eventId":"00000000-0000-0000-0000-000000000001","eventVersion":999,"orderId":"00000000-0000-0000-0000-000000000002","correlationId":"poison-demo","occurredAt":"2026-01-01T00:00:00Z","customerId":"poison","items":[{"sku":"BAD","quantity":1,"unitPrice":1.00}],"currency":"INR","totalAmount":1.00}'
aws_local sqs send-message --queue-url "$queue_url" --message-body "$poison" >/dev/null
echo "Poison message sent; maxReceiveCount=2. Wait at least 25 seconds before inspecting the DLQ."
