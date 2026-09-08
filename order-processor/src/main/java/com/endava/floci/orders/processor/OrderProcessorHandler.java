package com.endava.floci.orders.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.ArrayList;

public class OrderProcessorHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {
    private final OrderProcessor processor;

    public OrderProcessorHandler() {
        this(ProcessorConfiguration.fromEnvironment());
    }

    public OrderProcessorHandler(OrderProcessor processor) {
        this.processor = processor;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        var failures =
                new ArrayList<
                        SQSBatchResponse
                                .BatchItemFailure>(); // The supplied method handles an entire SQS
        // batch.

        //        It starts with no failed records.
        if (event == null || event.getRecords() == null) {
            return new SQSBatchResponse(
                    failures); // An empty event has nothing to process, so it returns an empty
            // failure list.
        }

        // It processes every SQS message separately.
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                processor.process(
                        message.getBody()); // This passes the JSON message body to OrderProcessor.

                // If processing succeeds, the message is not added to failures. Lambda reports it
                // as successful, and SQS deletes it.

                /**
                 * This(ProcessingExceptions) catches permanently invalid input, such as: -
                 * malformed JSON; - unsupported event version; - an event referring to a
                 * nonexistent order. It logs the reason and adds the message ID to the batch
                 * failure response.
                 */
            } catch (ProcessingExceptions.Poison ex) {

                context.getLogger()
                        .log(
                                "poison message rejected messageId="
                                        + message.getMessageId()
                                        + " reason="
                                        + ex.getMessage());
                failures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));

                /**
                 * This(RuntimeException) catches transient or unexpected failures, such as DynamoDB
                 * or S3 being temporarily unavailable. It also adds the message ID to the batch
                 * failure response.
                 */
            } catch (RuntimeException ex) {
                context.getLogger()
                        .log("retryable processing failure messageId=" + message.getMessageId());
                failures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
            }
        }

        // The returned object goes back to the AWS Lambda runtime. The Lambda event-source mapping
        // uses it to report results to SQS.
        return new SQSBatchResponse(failures);
    }
}
