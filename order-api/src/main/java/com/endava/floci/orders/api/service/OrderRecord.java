package com.endava.floci.orders.api.service;

import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderRecord(
        UUID orderId,
        String idempotencyKey,
        String payloadHash,
        String customerId,
        OrderStatus status,
        List<OrderItem> items,
        String currency,
        BigDecimal totalAmount,
        String receiptKey,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        long version) {
    public OrderRecord {
        items = List.copyOf(items);
    }
}
