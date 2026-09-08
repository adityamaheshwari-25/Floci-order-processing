package com.endava.floci.orders.processor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

// It is the real DynamoDB implementation of OrderStateStore.
public class DynamoOrderStateStore implements OrderStateStore {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoOrderStateStore(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public ClaimResult claim(UUID orderId, Instant now) {
        try {
            dynamoDb.updateItem(
                    UpdateItemRequest.builder()
                            .tableName(tableName)
                            .key(key(orderId))
                            .conditionExpression("#status = :received")
                            .updateExpression(
                                    "SET #status = :processing, updatedAt = :now, version = version + :one")
                            .expressionAttributeNames(Map.of("#status", "status"))
                            .expressionAttributeValues(
                                    Map.of(
                                            ":received", s("RECEIVED"),
                                            ":processing", s("PROCESSING"),
                                            ":now", s(now.toString()),
                                            ":one", n("1")))
                            .build());
            return ClaimResult.CLAIMED;
        } catch (ConditionalCheckFailedException ex) {
            Map<String, AttributeValue> item =
                    dynamoDb.getItem(
                                    GetItemRequest.builder()
                                            .tableName(tableName)
                                            .consistentRead(true)
                                            .key(key(orderId))
                                            .build())
                            .item();
            if (item == null || item.isEmpty()) {
                throw new ProcessingExceptions.Poison("Order does not exist: " + orderId);
            }
            String status = item.get("status").s();
            return switch (status) {
                case "COMPLETED" -> ClaimResult.COMPLETED_DUPLICATE;
                case "PROCESSING" -> ClaimResult.RESUMED;
                default ->
                        throw new ProcessingExceptions.Retryable(
                                "Order cannot be claimed from status " + status);
            };
        }
    }

    @Override
    public void complete(UUID orderId, String receiptKey, Instant now) {
        try {
            dynamoDb.updateItem(
                    UpdateItemRequest.builder()
                            .tableName(tableName)
                            .key(key(orderId))
                            .conditionExpression("#status = :processing")
                            .updateExpression(
                                    "SET #status = :completed, receiptKey = :receipt, updatedAt = :now, version = version + :one")
                            .expressionAttributeNames(Map.of("#status", "status"))
                            .expressionAttributeValues(
                                    Map.of(
                                            ":processing", s("PROCESSING"),
                                            ":completed", s("COMPLETED"),
                                            ":receipt", s(receiptKey),
                                            ":now", s(now.toString()),
                                            ":one", n("1")))
                            .build());
        } catch (ConditionalCheckFailedException ex) {
            throw new ProcessingExceptions.Retryable("Completion transition was rejected", ex);
        }
    }

    private static Map<String, AttributeValue> key(UUID orderId) {
        return Map.of("orderId", s(orderId.toString()));
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(String value) {
        return AttributeValue.builder().n(value).build();
    }
}
