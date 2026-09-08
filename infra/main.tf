provider "aws" {
  region                      = var.aws_region
  access_key                  = var.local_mode ? "test" : null
  secret_key                  = var.local_mode ? "test" : null
  skip_credentials_validation = var.local_mode
  skip_metadata_api_check     = var.local_mode
  skip_requesting_account_id  = var.local_mode
  s3_use_path_style           = var.local_mode

  endpoints {
    dynamodb = var.local_mode ? var.aws_endpoint_url : null
    iam      = var.local_mode ? var.aws_endpoint_url : null
    lambda   = var.local_mode ? var.aws_endpoint_url : null
    s3       = var.local_mode ? var.aws_endpoint_url : null
    sqs      = var.local_mode ? var.aws_endpoint_url : null
    sts      = var.local_mode ? var.aws_endpoint_url : null
  }
}

resource "aws_dynamodb_table" "orders" {
  name         = var.orders_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "orderId"

  attribute {
    name = "orderId"
    type = "S"
  }
}

resource "aws_s3_bucket" "receipts" {
  bucket        = var.receipts_bucket_name
  force_destroy = var.local_mode
}

resource "aws_s3_bucket_public_access_block" "receipts" {
  bucket                  = aws_s3_bucket.receipts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_sqs_queue" "dlq" {
  name                      = var.orders_dlq_name
  message_retention_seconds = 1209600
}

# How is the attempt count tracked?
# SQS tracks it automatically using the message’s approximate receive count.


# The approximate sequence is:
# Receive count = 1
# Lambda reports failure
# Message hidden for 10 seconds
# Message becomes visible again
#
# Receive count = 2
# Lambda reports failure
# SQS redrive policy moves it to the DLQ

# The application does not maintain this counter in DynamoDB. SQS maintains it as the system attribute:
# ApproximateReceiveCount

resource "aws_sqs_queue" "orders" {
  name                       = var.orders_queue_name
  visibility_timeout_seconds = 10 # The failed message is temporarily hidden for the queue’s visibility timeout.

  # maxReceiveCount = 2 means the source queue permits two receives before sending the repeatedly failing message to the DLQ.
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 2
  })
}

data "aws_iam_policy_document" "assume_lambda" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "processor" {
  name               = var.processor_role_name
  assume_role_policy = data.aws_iam_policy_document.assume_lambda.json
}

data "aws_iam_policy_document" "processor" {
  statement {
    actions   = ["dynamodb:GetItem", "dynamodb:UpdateItem"]
    resources = [aws_dynamodb_table.orders.arn]
  }
  statement {
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.receipts.arn}/receipts/*"]
  }
  statement {
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.orders.arn]
  }
  statement {
    actions   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["arn:aws:logs:${var.aws_region}:*:*"]
  }
}

# The Lambda integration requires permission to receive and delete SQS messages.
resource "aws_iam_role_policy" "processor" {
  name   = "${var.processor_role_name}-policy"
  role   = aws_iam_role.processor.id
  policy = data.aws_iam_policy_document.processor.json
}

resource "aws_lambda_function" "processor" {
  function_name    = var.processor_function_name
  role             = aws_iam_role.processor.arn
  runtime          = "java21"
  handler          = "com.endava.floci.orders.processor.OrderProcessorHandler::handleRequest"
  filename         = "${path.module}/../order-processor/target/order-processor.jar"
  source_code_hash = filebase64sha256("${path.module}/../order-processor/target/order-processor.jar")
  timeout          = 20
  memory_size      = 512

  environment {
    variables = merge({
      AWS_REGION      = var.aws_region
      ORDERS_TABLE    = aws_dynamodb_table.orders.name
      RECEIPTS_BUCKET = aws_s3_bucket.receipts.bucket
    }, var.local_mode ? { AWS_ENDPOINT_URL = var.lambda_endpoint_url } : {})
  }
}

# aws_lambda_event_source_mapping is a Terraform resource that creates the connection between an event source—in this case, an SQS queue—and an AWS Lambda function.
# Without this mapping, messages could enter SQS, but the Lambda would not automatically receive them.
# ARN means Amazon Resource Name. It is AWS’s unique identifier for a resource.
resource "aws_lambda_event_source_mapping" "orders" {

  # The source is the order-events SQS queue. Lambda’s managed poller watches this queue for messages.
  event_source_arn = aws_sqs_queue.orders.arn

  function_name           = aws_lambda_function.processor.arn
  batch_size              = 1 # Send a maximum of one SQS message in each Lambda invocation.
  function_response_types = ["ReportBatchItemFailures"]

  # The Lambda integration requires permission to receive and delete SQS messages.
  depends_on = [aws_iam_role_policy.processor]
}
