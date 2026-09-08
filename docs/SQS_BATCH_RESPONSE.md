# SQS Batch Responses and Message Deletion

The AWS Lambda handler returns an `SQSBatchResponse` containing the identifiers
of messages that failed during processing:

```java
return new SQSBatchResponse(failures);
```

The response is returned to the AWS Lambda runtime. Because the event-source
mapping enables partial batch failure reporting:

```hcl
function_response_types = ["ReportBatchItemFailures"]
```

Lambda uses the response to determine which SQS messages were processed
successfully.

For example, if Lambda receives three messages and only `message-2` fails, the
response is:

```json
{
  "batchItemFailures": [
    {
      "itemIdentifier": "message-2"
    }
  ]
}
```

The Lambda-to-SQS integration handles the messages as follows:

- `message-1` is considered successful and is deleted from the source queue.
- `message-2` is considered failed and remains in the queue for another
  attempt.
- `message-3` is considered successful and is deleted from the source queue.

The failed message is temporarily hidden for the queue's visibility timeout.
If it is not successfully processed, it becomes visible and can be received
again. After it reaches the configured `maxReceiveCount`, SQS moves it to the
dead-letter queue.

## What an empty failure list means

If the handler returns an empty failure list:

```java
return new SQSBatchResponse(List.of());
```

all messages in that batch are considered successfully processed and are
deleted from the source queue.

Strictly speaking, SQS does not inspect the Java list and delete messages by
itself. The AWS Lambda event-source mapping interprets the handler response and
calls SQS to delete the successfully processed messages. Therefore, with an
empty failure list, the Lambda-SQS integration deletes every message in that
batch.

In this project, Terraform configures:

```hcl
batch_size = 1
```

Therefore, each Lambda invocation normally receives one message:

- Empty failure list: the message is deleted.
- Message ID included in `batchItemFailures`: the message is retried.

