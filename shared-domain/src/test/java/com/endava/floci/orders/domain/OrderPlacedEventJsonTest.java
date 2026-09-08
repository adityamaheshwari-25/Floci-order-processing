package com.endava.floci.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPlacedEventJsonTest {
    @Test
    void roundTripsTheVersionedEventContract() throws Exception {
        var event =
                new OrderPlacedEvent(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        1,
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "corr-123",
                        Instant.parse("2026-08-25T10:15:30Z"),
                        "customer-101",
                        List.of(new OrderItem("KEYBOARD-01", 2, new BigDecimal("2499.00"))),
                        "INR",
                        new BigDecimal("4998"));

        String json = JsonSupport.mapper().writeValueAsString(event);
        OrderPlacedEvent restored = JsonSupport.mapper().readValue(json, OrderPlacedEvent.class);

        assertThat(restored).isEqualTo(event);
        assertThat(json).contains("\"eventVersion\":1", "\"totalAmount\":4998.00");
    }
}
