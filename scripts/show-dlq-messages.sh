#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
require_command aws

dlq_url="$(aws_local sqs get-queue-url --queue-name order-events-dlq --query QueueUrl --output text)"
aws_local sqs receive-message --queue-url "$dlq_url" --max-number-of-messages 10 --wait-time-seconds 5 --attribute-names All
