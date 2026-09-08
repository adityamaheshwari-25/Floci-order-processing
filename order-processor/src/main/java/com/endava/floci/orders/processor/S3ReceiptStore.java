package com.endava.floci.orders.processor;

import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3ReceiptStore implements ReceiptStore {
    private final S3Client s3;
    private final String bucket;

    public S3ReceiptStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, String json) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/json")
                        .build(),
                RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
    }
}
