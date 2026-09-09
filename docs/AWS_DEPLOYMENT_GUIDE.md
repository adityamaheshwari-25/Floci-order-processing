# Deploy the Floci demo infrastructure to AWS

The same Terraform resource definitions can target AWS by setting
`local_mode = false`. Terraform then uses real AWS credentials and service
endpoints instead of Floci.

This guide describes a future AWS deployment. It does not mean the current
repository has been validated against AWS. Complete the configuration changes
below before applying an AWS plan. Creating resources in AWS can incur charges.

## What gets deployed

Terraform manages nine resources:

| Resource | Purpose |
| --- | --- |
| DynamoDB table | Stores orders and idempotency records. |
| S3 bucket | Stores order receipts. |
| S3 public-access block | Blocks public access to the receipt bucket. |
| Main SQS queue | Receives order events. |
| SQS dead-letter queue | Holds repeatedly failing events. |
| IAM execution role | Provides the Lambda execution identity. |
| IAM inline policy | Grants the processor its service permissions. |
| Lambda function | Runs the Java order processor. |
| Lambda event-source mapping | Connects the SQS queue to the processor. |

The REST API can initially run on your laptop and call AWS. Terraform currently
does not provision hosting for `order-api`; hosting it on AWS requires an
additional deployment design. Floci and Podman are not required to run these
resources in AWS.

## 1. Adjust Terraform for AWS

These are required edits to [infra/main.tf](../infra/main.tf), not changes
already applied by this guide.

### SQS visibility timeout

The demo currently uses a 10-second queue visibility timeout and a 20-second
Lambda timeout. AWS requires the Lambda timeout not to exceed the queue's
visibility timeout and recommends a visibility timeout at least six times the
Lambda timeout. With the current 20-second function timeout and no batch window,
use 120 seconds for AWS. See [AWS SQS/Lambda configuration](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html).

In `aws_sqs_queue.orders`, replace the visibility timeout assignment with:

```hcl
visibility_timeout_seconds = var.local_mode ? 10 : 120
```

This preserves the short local demo retry cycle. AWS poison-message retries will
take longer; the local script's 30-second wait is not appropriate for AWS.

### Lambda environment variables

AWS Lambda provides `AWS_REGION` itself. It is reserved and must not be set
explicitly in the AWS function configuration. See [Lambda environment variables](https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html).

Replace the function's `environment` block with:

```hcl
environment {
  variables = merge({
    ORDERS_TABLE    = aws_dynamodb_table.orders.name
    RECEIPTS_BUCKET = aws_s3_bucket.receipts.bucket
  }, var.local_mode ? {
    AWS_REGION       = var.aws_region
    AWS_ENDPOINT_URL = var.lambda_endpoint_url
  } : {})
}
```

### Bucket name

Choose an available bucket name rather than assuming `order-receipts` is free.
For the default shared global namespace, the name must be unique across AWS
accounts and regions within the partition. A name including your account ID,
region, and a unique suffix is useful. See [S3 naming rules](https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html).

## 2. Authenticate to the intended AWS account

Use a fresh Git Bash terminal at the repository root. Clear the local demo's
fake credentials and endpoint overrides:

```bash
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
unset AWS_ENDPOINT_URL AWS_ENDPOINT_URL_S3 AWS_ENDPOINT_URL_SQS
unset AWS_ENDPOINT_URL_DYNAMODB AWS_ENDPOINT_URL_LAMBDA
unset AWS_ENDPOINT_URL_IAM AWS_ENDPOINT_URL_STS

export AWS_PROFILE=your-profile
export AWS_REGION=us-east-1
export AWS_DEFAULT_REGION="$AWS_REGION"
export AWS_PAGER=""
```

Replace `your-profile` with a configured AWS profile. Ensure that profile does
not contain Floci endpoint overrides. If using IAM Identity Center/SSO:

```bash
aws sso login --profile "$AWS_PROFILE"
aws sts get-caller-identity
```

Configure the SSO profile first if necessary. For another credential method,
follow your organization's authentication process instead of the SSO command.
Verify that `get-caller-identity` reports your intended account and identity.

The Terraform deployment identity needs permission to manage the listed
resources, including passing the Lambda execution role (`iam:PassRole`). The
Lambda execution role and the deployment identity are separate identities.

## 3. Keep AWS state separate from Floci state

Initialize Terraform, then create a dedicated workspace:

```bash
terraform -chdir=infra init
terraform -chdir=infra workspace new aws-demo
```

If `aws-demo` already exists, select it instead:

```bash
terraform -chdir=infra workspace select aws-demo
```

Confirm the active workspace:

```bash
terraform -chdir=infra workspace show
```

Expected: `aws-demo`. Workspaces separate state; they do not select an AWS
account or automatically set `local_mode=false`. For shared/team use, configure
an appropriate remote backend with locking and controlled access.

## 4. Build and check the configuration

First complete the normal local quality gates against Floci, as described in
the [script-based demo guide](SCRIPT_BASED_DEMO_GUIDE.md). Then use the AWS
terminal and workspace for these commands:

```bash
sh ./mvnw -pl order-processor -am package -DskipTests
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra validate
```

The packaging command creates the Lambda JAR; it skips tests and is not a
replacement for `clean verify`. Run each command only after the previous one
succeeds.

## 5. Plan and apply the AWS deployment

Replace the bucket placeholder before running:

```bash
terraform -chdir=infra plan \
  -var='local_mode=false' \
  -var='aws_region=us-east-1' \
  -var='receipts_bucket_name=YOUR-UNIQUE-BUCKET-NAME' \
  -out=aws.tfplan
```

Review the account, region, resource names, and planned changes. On a fresh AWS
workspace, expect nine resource additions. Existing resources in your account
are not automatically imported into this state.

Apply the reviewed saved plan:

```bash
terraform -chdir=infra apply aws.tfplan
terraform -chdir=infra output
```

The saved plan includes the selected variable values. Keep Terraform state and
plan files out of Git.

## 6. Run the API against AWS

The current `start-api.sh` deliberately selects the local profile and fake
credentials. Use a separate AWS-configured terminal for the API instead.

After building all modules and selecting the AWS workspace:

```bash
ORDERS_QUEUE_URL="$(terraform -chdir=infra output -raw orders_queue_url)" || exit 1
ORDERS_TABLE="$(terraform -chdir=infra output -raw orders_table_name)" || exit 1
export ORDERS_QUEUE_URL ORDERS_TABLE

mkdir -p target/java-tmp
socket_dir="$PWD/target/java-tmp"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) socket_dir="$(cygpath -m "$socket_dir")" ;;
esac

java "-Djdk.net.unixdomain.tmpdir=$socket_dir" \
  -jar order-api/target/order-api-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=aws
```

The API uses the AWS SDK's standard credential chain. Its runtime identity needs
permissions for the DynamoDB reads/writes used by the API and `sqs:SendMessage`.
The processor's Lambda execution role does not grant permissions to an API
running on your laptop. If using an SSO profile, ensure the application's AWS
SDK dependencies support that credential provider; a successful AWS CLI login
alone does not verify Java SDK authentication.

## 7. Verify the AWS flow

Do not use the existing demo scripts unchanged: `common.sh` configures fake
credentials and restricts requests to Floci's loopback endpoint.

Using the API and AWS console/CLI, verify:

1. The API accepts a new order.
2. The SQS event-source mapping invokes the Lambda processor.
3. DynamoDB shows the order as `COMPLETED`.
4. The configured AWS receipt bucket contains the receipt.
5. Repeated API requests and duplicate queue delivery preserve one receipt per
   order.
6. An unsupported-version message reaches the DLQ after the configured retries.

Use Lambda's CloudWatch logs to investigate failures. Allow for the longer AWS
visibility timeout when checking the poison-message scenario.

## Cleanup and returning to local mode

Destroy only when you intend to delete this AWS demo's resources and data.
Verify your AWS identity and `aws-demo` workspace, then use the same variable
values as deployment:

```bash
terraform -chdir=infra destroy \
  -var='local_mode=false' \
  -var='aws_region=us-east-1' \
  -var='receipts_bucket_name=YOUR-UNIQUE-BUCKET-NAME'
```

In AWS mode, the receipt bucket has `force_destroy=false`. Terraform will not
automatically empty a nonempty bucket; intentionally remove its contents or
retain the bucket as appropriate before completing cleanup. Independently
created resources, such as Lambda log groups, may remain outside Terraform's
managed resource list.

Before returning to Floci, select the original local workspace (normally
`default`) and use a separate terminal with the local environment:

```bash
terraform -chdir=infra workspace select default
```

Do not run `provision-local.sh` while `aws-demo` is selected. The selected
workspace persists across Terraform commands in this working directory.
