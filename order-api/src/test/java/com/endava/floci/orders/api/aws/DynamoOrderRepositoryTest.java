package com.endava.floci.orders.api.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.endava.floci.orders.api.config.AwsProperties;
import com.endava.floci.orders.api.service.OrderRecord;
import com.endava.floci.orders.api.service.OrderRepository;
import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

class DynamoOrderRepositoryTest {
    @Test
    void atomicallyClaimsTheOrderAndIdempotencyKey() {
        DynamoDbClient client = Mockito.mock(DynamoDbClient.class);
        var repository =
                new DynamoOrderRepository(
                        client,
                        new AwsProperties("us-east-1", null, "orders", "queue", null, null));
        OrderRecord order = order();

        OrderRepository.CreateResult result = repository.create(order);

        assertThat(result).isInstanceOf(OrderRepository.CreateResult.Created.class);
        var request = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(request.capture());
        assertThat(request.getValue().transactItems()).hasSize(2);
        assertThat(request.getValue().transactItems())
                .allSatisfy(
                        item ->
                                assertThat(item.put().conditionExpression())
                                        .isEqualTo("attribute_not_exists(orderId)"));
    }

    private static OrderRecord order() {
        Instant now = Instant.parse("2026-08-25T12:00:00Z");
        return new OrderRecord(
                UUID.randomUUID(),
                "idem-1",
                "hash",
                "customer",
                OrderStatus.RECEIVED,
                List.of(new OrderItem("SKU", 1, BigDecimal.ONE)),
                "INR",
                BigDecimal.ONE,
                null,
                "corr-1",
                now,
                now,
                1);
    }
}
