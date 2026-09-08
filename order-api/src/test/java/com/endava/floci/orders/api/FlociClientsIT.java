package com.endava.floci.orders.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.floci.testcontainers.FlociContainer;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

class FlociClientsIT {
    private static FlociContainer floci;
    private static URI endpoint;

    @BeforeAll
    static void startFloci() {
        String externalEndpoint = System.getenv("FLOCI_EXTERNAL_ENDPOINT");
        if ((externalEndpoint == null || externalEndpoint.isBlank())
                && System.getProperty("os.name").startsWith("Windows")) {
            externalEndpoint = "http://localhost:4566";
        }
        if (externalEndpoint != null && !externalEndpoint.isBlank()) {
            endpoint = URI.create(externalEndpoint);
            return;
        }
        floci = new FlociContainer("floci/floci:1.5.34");
        floci.start();
        endpoint = URI.create(floci.getEndpoint());
    }

    @AfterAll
    static void stopFloci() {
        if (floci != null) {
            floci.stop();
        }
    }

    private static StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    }

    @Test
    void s3SqsAndDynamoDbRoundTripThroughFloci() {
        String suffix = UUID.randomUUID().toString();
        String bucket = "it-receipts-" + suffix;
        String queue = "it-orders-" + suffix;
        String table = "it-orders-" + suffix;
        try (var s3 =
                        S3Client.builder()
                                .endpointOverride(endpoint)
                                .region(Region.US_EAST_1)
                                .credentialsProvider(credentials())
                                .forcePathStyle(true)
                                .build();
                var sqs =
                        SqsClient.builder()
                                .endpointOverride(endpoint)
                                .region(Region.US_EAST_1)
                                .credentialsProvider(credentials())
                                .build();
                var dynamo =
                        DynamoDbClient.builder()
                                .endpointOverride(endpoint)
                                .region(Region.US_EAST_1)
                                .credentialsProvider(credentials())
                                .build()) {
            s3.createBucket(r -> r.bucket(bucket));
            s3.putObject(
                    r -> r.bucket(bucket).key("receipts/test.json"),
                    RequestBody.fromString("{\"ok\":true}"));
            assertThat(s3.listObjectsV2(r -> r.bucket(bucket)).keyCount()).isOne();

            var queueUrl = sqs.createQueue(r -> r.queueName(queue)).queueUrl();
            sqs.sendMessage(r -> r.queueUrl(queueUrl).messageBody("event"));
            assertThat(sqs.receiveMessage(r -> r.queueUrl(queueUrl)).messages()).hasSize(1);

            dynamo.createTable(
                    CreateTableRequest.builder()
                            .tableName(table)
                            .billingMode(BillingMode.PAY_PER_REQUEST)
                            .keySchema(
                                    KeySchemaElement.builder()
                                            .attributeName("orderId")
                                            .keyType(KeyType.HASH)
                                            .build())
                            .attributeDefinitions(
                                    AttributeDefinition.builder()
                                            .attributeName("orderId")
                                            .attributeType(ScalarAttributeType.S)
                                            .build())
                            .build());
            dynamo.putItem(
                    r -> r.tableName(table).item(Map.of("orderId", AttributeValue.fromS("test"))));
            assertThat(
                            dynamo.getItem(
                                            r ->
                                                    r.tableName(table)
                                                            .key(
                                                                    Map.of(
                                                                            "orderId",
                                                                            AttributeValue.fromS(
                                                                                    "test"))))
                                    .hasItem())
                    .isTrue();

            s3.deleteObject(r -> r.bucket(bucket).key("receipts/test.json"));
            s3.deleteBucket(r -> r.bucket(bucket));
            sqs.deleteQueue(r -> r.queueUrl(queueUrl));
            dynamo.deleteTable(r -> r.tableName(table));
        }
    }
}
