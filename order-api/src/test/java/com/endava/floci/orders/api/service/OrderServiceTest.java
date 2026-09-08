package com.endava.floci.orders.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.endava.floci.orders.api.model.CreateOrderRequest;
import com.endava.floci.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository repository;
    @Mock OrderPublisher publisher;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service =
                new OrderService(
                        repository,
                        publisher,
                        Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void calculatesTotalAndPublishesThePersistedOrder() {
        when(repository.create(any()))
                .thenAnswer(
                        invocation ->
                                new OrderRepository.CreateResult.Created(
                                        invocation.getArgument(0)));
        var request =
                new CreateOrderRequest(
                        "customer-101",
                        List.of(
                                new CreateOrderRequest.Item(
                                        "KEYBOARD", 1, new BigDecimal("2499.00")),
                                new CreateOrderRequest.Item("MOUSE", 2, new BigDecimal("799.00"))),
                        null);

        var result = service.create(request, "idem-1", "corr-1");

        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(result.totalAmount()).isEqualByComparingTo("4097.00");
        assertThat(result.currency()).isEqualTo("INR");
        var event = ArgumentCaptor.forClass(com.endava.floci.orders.domain.OrderPlacedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().totalAmount()).isEqualByComparingTo("4097.00");
        assertThat(event.getValue().correlationId()).isEqualTo("corr-1");
    }

    @Test
    void returnsExistingLogicalOrderWithoutRepublishing() {
        when(repository.create(any()))
                .thenAnswer(
                        invocation ->
                                new OrderRepository.CreateResult.Existing(
                                        invocation.getArgument(0)));
        var request = request("10.00");

        var result = service.create(request, "idem-1", "corr-1");

        assertThat(result.status()).isEqualTo(OrderStatus.RECEIVED);
        verify(publisher, never()).publish(any());
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForAnotherPayload() {
        when(repository.create(any()))
                .thenAnswer(
                        invocation -> {
                            OrderRecord proposed = invocation.getArgument(0);
                            return new OrderRepository.CreateResult.Existing(
                                    new OrderRecord(
                                            proposed.orderId(),
                                            proposed.idempotencyKey(),
                                            "different-hash",
                                            proposed.customerId(),
                                            proposed.status(),
                                            proposed.items(),
                                            proposed.currency(),
                                            proposed.totalAmount(),
                                            null,
                                            proposed.correlationId(),
                                            proposed.createdAt(),
                                            proposed.updatedAt(),
                                            1));
                        });

        assertThatThrownBy(() -> service.create(request("10.00"), "idem-1", "corr-1"))
                .isInstanceOf(OrderExceptions.IdempotencyConflict.class);
        verify(publisher, never()).publish(any());
    }

    private static CreateOrderRequest request(String price) {
        return new CreateOrderRequest(
                "customer-101",
                List.of(new CreateOrderRequest.Item("SKU-1", 1, new BigDecimal(price))),
                "INR");
    }
}
