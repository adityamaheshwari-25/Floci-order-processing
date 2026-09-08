package com.endava.floci.orders.api;

import com.endava.floci.orders.api.config.AwsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AwsProperties.class)
public class OrderApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApiApplication.class, args);
    }
}
