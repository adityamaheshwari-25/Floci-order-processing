package com.endava.floci.orders.api.aws;

import com.endava.floci.orders.api.config.AwsProperties;
import com.endava.floci.orders.api.service.OrderRecord;
import com.endava.floci.orders.api.service.OrderRepository;
import com.endava.floci.orders.domain.JsonSupport;
import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Repository
public class DynamoOrderRepository implements OrderRepository {
    private static final String IDEMPOTENCY_PREFIX = "IDEMPOTENCY#";
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoOrderRepository(DynamoDbClient dynamoDb, AwsProperties properties) {
        this.dynamoDb = dynamoDb;
        this.tableName = properties.tableName();
    }

    @Override
    public CreateResult create(OrderRecord order) {
        Map<String, AttributeValue> idempotencyItem =
                Map.of(
                        "orderId", s(IDEMPOTENCY_PREFIX + order.idempotencyKey()),
                        "recordType", s("IDEMPOTENCY"),
                        "linkedOrderId", s(order.orderId().toString()),
                        "payloadHash", s(order.payloadHash()));
        try {
            dynamoDb.transactWriteItems(
                    TransactWriteItemsRequest.builder()
                            .transactItems(
                                    putWithNotExists(toItem(order)),
                                    putWithNotExists(idempotencyItem))
                            .build());
            return new CreateResult.Created(order);
        } catch (TransactionCanceledException ex) {
            Map<String, AttributeValue> keyItem =
                    getItem(IDEMPOTENCY_PREFIX + order.idempotencyKey()).orElseThrow(() -> ex);
            String linkedOrderId = keyItem.get("linkedOrderId").s();
            OrderRecord existing = findById(UUID.fromString(linkedOrderId)).orElseThrow(() -> ex);
            return new CreateResult.Existing(existing);
        }
    }

    @Override
    public Optional<OrderRecord> findById(UUID orderId) {
        return getItem(orderId.toString()).map(DynamoOrderRepository::fromItem);
    }

    private Optional<Map<String, AttributeValue>> getItem(String orderId) {
        Map<String, AttributeValue> item =
                dynamoDb.getItem(
                                GetItemRequest.builder()
                                        .tableName(tableName)
                                        .consistentRead(true)
                                        .key(Map.of("orderId", s(orderId)))
                                        .build())
                        .item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(item);
    }

    private TransactWriteItem putWithNotExists(Map<String, AttributeValue> item) {
        return TransactWriteItem.builder()
                .put(
                        Put.builder()
                                .tableName(tableName)
                                .item(item)
                                .conditionExpression("attribute_not_exists(orderId)")
                                .build())
                .build();
    }

    private static Map<String, AttributeValue> toItem(OrderRecord order) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("orderId", s(order.orderId().toString()));
        item.put("recordType", s("ORDER"));
        item.put("idempotencyKey", s(order.idempotencyKey()));
        item.put("payloadHash", s(order.payloadHash()));
        item.put("customerId", s(order.customerId()));
        item.put("status", s(order.status().name()));
        item.put("itemsJson", s(JsonSupport.mapper().writeValueAsString(order.items())));
        item.put("currency", s(order.currency()));
        item.put("totalAmount", n(order.totalAmount().toPlainString()));
        item.put("correlationId", s(order.correlationId()));
        item.put("createdAt", s(order.createdAt().toString()));
        item.put("updatedAt", s(order.updatedAt().toString()));
        item.put("version", n(Long.toString(order.version())));
        if (order.receiptKey() != null) {
            item.put("receiptKey", s(order.receiptKey()));
        }
        return Map.copyOf(item);
    }

    private static OrderRecord fromItem(Map<String, AttributeValue> item) {
        List<OrderItem> items =
                JsonSupport.mapper()
                        .readerForListOf(OrderItem.class)
                        .readValue(item.get("itemsJson").s());
        return new OrderRecord(
                UUID.fromString(item.get("orderId").s()),
                item.get("idempotencyKey").s(),
                item.get("payloadHash").s(),
                item.get("customerId").s(),
                OrderStatus.valueOf(item.get("status").s()),
                items,
                item.get("currency").s(),
                new java.math.BigDecimal(item.get("totalAmount").n()),
                item.containsKey("receiptKey") ? item.get("receiptKey").s() : null,
                item.get("correlationId").s(),
                java.time.Instant.parse(item.get("createdAt").s()),
                java.time.Instant.parse(item.get("updatedAt").s()),
                Long.parseLong(item.get("version").n()));
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(String value) {
        return AttributeValue.builder().n(value).build();
    }
}
