variable "local_mode" {
  description = "Use Floci and fake credentials. Set false only for an explicitly reviewed AWS deployment."
  type        = bool
  default     = true
}

variable "aws_endpoint_url" {
  description = "Host-visible Floci endpoint used only in local mode."
  type        = string
  default     = "http://localhost:4566"
  validation {
    condition     = !var.local_mode || can(regex("^http://(localhost|127\\.0\\.0\\.1):4566$", var.aws_endpoint_url))
    error_message = "Local mode is deliberately restricted to the loopback Floci endpoint on port 4566."
  }
}

variable "lambda_endpoint_url" {
  description = "Floci endpoint visible from the Lambda runtime container."
  type        = string
  default     = "http://floci:4566"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}
variable "orders_table_name" {
  type    = string
  default = "orders"
}
variable "orders_queue_name" {
  type    = string
  default = "order-events"
}
variable "orders_dlq_name" {
  type    = string
  default = "order-events-dlq"
}
variable "receipts_bucket_name" {
  type    = string
  default = "order-receipts"
}
variable "processor_function_name" {
  type    = string
  default = "order-processor"
}
variable "processor_role_name" {
  type    = string
  default = "order-processor-role"
}
