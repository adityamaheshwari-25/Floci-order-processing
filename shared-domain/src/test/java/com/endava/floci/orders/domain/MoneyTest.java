package com.endava.floci.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void appliesDeterministicBankersRounding() {
        assertThat(new Money(new BigDecimal("10.125"), "INR").amount())
                .isEqualByComparingTo("10.12");
        assertThat(new Money(new BigDecimal("10.135"), "INR").amount())
                .isEqualByComparingTo("10.14");
    }

    @Test
    void rejectsNegativeAmountsAndInvalidCurrencies() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"), "INR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "NOT-A-CURRENCY"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
