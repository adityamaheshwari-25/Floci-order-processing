package com.endava.floci.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Receipt(
        UUID orderId,
        String customerId,
        String correlationId,
        String currency,
        BigDecimal totalAmount,
        Instant completedAt) {}
