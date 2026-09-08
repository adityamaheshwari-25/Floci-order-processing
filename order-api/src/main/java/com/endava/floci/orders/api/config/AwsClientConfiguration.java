package com.endava.floci.orders.api.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsClientConfiguration {
    @Bean
    @Profile("local")
    DynamoDbClient localDynamoDb(AwsProperties properties) {
        return DynamoDbClient.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(requiredEndpoint(properties.endpoint()))
                .credentialsProvider(localCredentials(properties))
                .build();
    }

    @Bean
    @Profile("local")
    SqsClient localSqs(AwsProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(requiredEndpoint(properties.endpoint()))
                .credentialsProvider(localCredentials(properties))
                .build();
    }

    @Bean
    @Profile("!local")
    DynamoDbClient awsDynamoDb(AwsProperties properties) {
        return DynamoDbClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("!local")
    SqsClient awsSqs(AwsProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    private static StaticCredentialsProvider localCredentials(AwsProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private static URI requiredEndpoint(URI endpoint) {
        if (endpoint == null) {
            throw new IllegalStateException("orders.aws.endpoint is required in the local profile");
        }
        return endpoint;
    }
}
