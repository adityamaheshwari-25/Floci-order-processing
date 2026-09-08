package com.endava.floci.orders.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.endava.floci.orders.domain.JsonSupport;
import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderPlacedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderProcessorTest {
    @Mock OrderStateStore stateStore;
    @Mock ReceiptStore receiptStore;

    private OrderProcessor processor;
    private OrderPlacedEvent event;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-08-25T12:00:00Z");
        processor = new OrderProcessor(stateStore, receiptStore, Clock.fixed(now, ZoneOffset.UTC));
        event =
                new OrderPlacedEvent(
                        UUID.randomUUID(),
                        1,
                        UUID.randomUUID(),
                        "corr-1",
                        now.minusSeconds(5),
                        "customer-1",
                        List.of(new OrderItem("SKU", 2, new BigDecimal("10.00"))),
                        "INR",
                        new BigDecimal("20.00"));
    }

    @Test
    void claimsWritesDeterministicReceiptAndCompletes() {
        when(stateStore.claim(eq(event.orderId()), any()))
                .thenReturn(OrderStateStore.ClaimResult.CLAIMED);

        processor.process(JsonSupport.mapper().writeValueAsString(event));

        String key = "receipts/" + event.orderId() + ".json";
        var json = ArgumentCaptor.forClass(String.class);
        verify(receiptStore).put(eq(key), json.capture());
        assertThat(json.getValue()).contains(event.orderId().toString(), "\"totalAmount\":20.00");
        verify(stateStore).complete(eq(event.orderId()), eq(key), any());
    }

    @Test
    void completedDuplicateHasNoAdditionalSideEffect() {
        when(stateStore.claim(eq(event.orderId()), any()))
                .thenReturn(OrderStateStore.ClaimResult.COMPLETED_DUPLICATE);

        processor.process(JsonSupport.mapper().writeValueAsString(event));

        verify(receiptStore, never()).put(any(), any());
        verify(stateStore, never()).complete(any(), any(), any());
    }

    @Test
    void rejectsUnsupportedEventVersionAsPoison() {
        var future =
                new OrderPlacedEvent(
                        event.eventId(),
                        99,
                        event.orderId(),
                        event.correlationId(),
                        event.occurredAt(),
                        event.customerId(),
                        event.items(),
                        event.currency(),
                        event.totalAmount());

        assertThatThrownBy(() -> processor.process(JsonSupport.mapper().writeValueAsString(future)))
                .isInstanceOf(ProcessingExceptions.Poison.class);
        verify(stateStore, never()).claim(any(), any());
    }
}
