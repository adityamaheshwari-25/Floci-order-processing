package com.endava.floci.orders.api.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orders.aws")
public record AwsProperties(
        String region,
        URI endpoint,
        String tableName,
        String queueUrl,
        String accessKey,
        String secretKey) {}
