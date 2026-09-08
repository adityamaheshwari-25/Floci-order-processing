package com.endava.floci.orders.api.model;

import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String customerId,
        OrderStatus status,
        List<OrderItem> items,
        String currency,
        BigDecimal totalAmount,
        String receiptKey,
        String correlationId,
        Instant createdAt,
        Instant updatedAt) {}
