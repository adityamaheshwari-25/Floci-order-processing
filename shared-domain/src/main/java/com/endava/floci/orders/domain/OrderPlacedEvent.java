package com.endava.floci.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlacedEvent(
        UUID eventId,
        int eventVersion,
        UUID orderId,
        String correlationId,
        Instant occurredAt,
        String customerId,
        List<OrderItem> items,
        String currency,
        BigDecimal totalAmount) {
    public static final int CURRENT_VERSION = 1;

    public OrderPlacedEvent {
        if (eventId == null || orderId == null || occurredAt == null) {
            throw new IllegalArgumentException("eventId, orderId and occurredAt are required");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items are required");
        }
        Money total = new Money(totalAmount, currency);
        totalAmount = total.amount();
        currency = total.currency();
    }
}
