output "orders_table_name" {
  value = aws_dynamodb_table.orders.name
}

output "receipts_bucket_name" {
  value = aws_s3_bucket.receipts.bucket
}

output "orders_queue_url" {
  value = aws_sqs_queue.orders.url
}

output "orders_dlq_url" {
  value = aws_sqs_queue.dlq.url
}

output "processor_function_name" {
  value = aws_lambda_function.processor.function_name
}
