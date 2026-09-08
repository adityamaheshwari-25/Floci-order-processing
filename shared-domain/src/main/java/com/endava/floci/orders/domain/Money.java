package com.endava.floci.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Money(BigDecimal amount, String currency) {
    public static final int SCALE = 2;

    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_EVEN);
        currency = Currency.getInstance(currency == null ? "INR" : currency).getCurrencyCode();
    }
}
