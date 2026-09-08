package com.endava.floci.orders.api.aws;

import com.endava.floci.orders.api.config.AwsProperties;
import com.endava.floci.orders.api.service.OrderPublisher;
import com.endava.floci.orders.domain.JsonSupport;
import com.endava.floci.orders.domain.OrderPlacedEvent;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
public class SqsOrderPublisher implements OrderPublisher {
    private final SqsClient sqs;
    private final String queueUrl;

    public SqsOrderPublisher(SqsClient sqs, AwsProperties properties) {
        this.sqs = sqs;
        this.queueUrl = properties.queueUrl();
    }

    @Override
    public void publish(OrderPlacedEvent event) {
        sqs.sendMessage(
                SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(JsonSupport.mapper().writeValueAsString(event))
                        .build());
    }
}
