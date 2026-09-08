package com.endava.floci.orders.api.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.endava.floci.orders.api.config.AwsProperties;
import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderPlacedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class SqsOrderPublisherTest {
    @Test
    void publishesTheJsonContractToTheConfiguredQueue() {
        SqsClient sqs = Mockito.mock(SqsClient.class);
        var publisher =
                new SqsOrderPublisher(
                        sqs,
                        new AwsProperties(
                                "us-east-1", null, "orders", "http://floci/queue", null, null));
        var event =
                new OrderPlacedEvent(
                        UUID.randomUUID(),
                        1,
                        UUID.randomUUID(),
                        "corr-1",
                        Instant.parse("2026-08-25T12:00:00Z"),
                        "customer-1",
                        List.of(new OrderItem("SKU", 1, new BigDecimal("1.00"))),
                        "INR",
                        new BigDecimal("1.00"));

        publisher.publish(event);

        var request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo("http://floci/queue");
        assertThat(request.getValue().messageBody())
                .contains("\"eventVersion\":1", event.orderId().toString());
    }
}
