package com.endava.floci.orders.domain;

import java.math.BigDecimal;

public record OrderItem(String sku, int quantity, BigDecimal unitPrice) {
    public OrderItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }
}
