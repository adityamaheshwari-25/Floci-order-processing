package com.endava.floci.orders.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderProcessorHandlerTest {
    @Test
    void reportsOnlyFailedRecordsForPartialBatchRetry() {
        OrderProcessor processor = Mockito.mock(OrderProcessor.class);
        doThrow(new ProcessingExceptions.Poison("bad")).when(processor).process("poison");
        doThrow(new ProcessingExceptions.Retryable("retry")).when(processor).process("retry");
        var event = new SQSEvent();
        event.setRecords(
                List.of(
                        message("ok-1", "ok"),
                        message("bad-2", "poison"),
                        message("bad-3", "retry")));

        var response = new OrderProcessorHandler(processor).handleRequest(event, context());

        assertThat(response.getBatchItemFailures())
                .extracting(failure -> failure.getItemIdentifier())
                .containsExactly("bad-2", "bad-3");
    }

    private static SQSEvent.SQSMessage message(String id, String body) {
        var message = new SQSEvent.SQSMessage();
        message.setMessageId(id);
        message.setBody(body);
        return message;
    }

    private static Context context() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getLogger()).thenReturn(Mockito.mock(LambdaLogger.class));
        return context;
    }
}
