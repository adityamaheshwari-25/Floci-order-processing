package com.endava.floci.orders.processor;

import java.net.URI;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

final class ProcessorConfiguration {
    private ProcessorConfiguration() {}

    static OrderProcessor fromEnvironment() {
        Map<String, String> env = System.getenv();
        String regionName = required(env, "AWS_REGION");
        String table = required(env, "ORDERS_TABLE");
        String bucket = required(env, "RECEIPTS_BUCKET");
        String endpoint = env.get("AWS_ENDPOINT_URL");
        Region region = Region.of(regionName);

        /**
         * DefaultCredentialsProvider supports both cases: - local fake credentials supplied to the
         * Floci environment; - Lambda’s IAM-role credentials in AWS.
         */
        DefaultCredentialsProvider credentials = DefaultCredentialsProvider.create();

        var dynamoBuilder =
                DynamoDbClient.builder().region(region).credentialsProvider(credentials);
        var s3Builder = S3Client.builder().region(region).credentialsProvider(credentials);
        if (endpoint != null && !endpoint.isBlank()) {
            URI endpointUri = URI.create(endpoint);
            dynamoBuilder.endpointOverride(endpointUri);
            s3Builder.endpointOverride(endpointUri).forcePathStyle(true);
        }
        return new OrderProcessor(
                new DynamoOrderStateStore(dynamoBuilder.build(), table),
                new S3ReceiptStore(s3Builder.build(), bucket),
                java.time.Clock.systemUTC());
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
