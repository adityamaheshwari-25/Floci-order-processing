package com.endava.floci.orders.api.service;

import com.endava.floci.orders.api.model.CreateOrderRequest;
import com.endava.floci.orders.api.model.OrderResponse;
import com.endava.floci.orders.domain.JsonSupport;
import com.endava.floci.orders.domain.Money;
import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderPlacedEvent;
import com.endava.floci.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository repository;
    private final OrderPublisher publisher;
    private final Clock clock;

    @Autowired
    public OrderService(OrderRepository repository, OrderPublisher publisher) {
        this(repository, publisher, Clock.systemUTC());
    }

    OrderService(OrderRepository repository, OrderPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    public OrderResponse create(
            CreateOrderRequest request, String idempotencyKey, String correlationId) {
        List<OrderItem> items =
                request.items().stream()
                        .map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
                        .toList();
        String currency = request.currency() == null ? "INR" : request.currency();
        BigDecimal total =
                items.stream()
                        .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        Money money = new Money(total, currency);
        String payloadHash = hash(request.customerId(), items, money);
        Instant now = clock.instant();
        var order =
                new OrderRecord(
                        UUID.randomUUID(),
                        idempotencyKey,
                        payloadHash,
                        request.customerId(),
                        OrderStatus.RECEIVED,
                        items,
                        money.currency(),
                        money.amount(),
                        null,
                        correlationId,
                        now,
                        now,
                        1);

        OrderRepository.CreateResult result = repository.create(order);
        if (result instanceof OrderRepository.CreateResult.Existing existing) {
            if (!existing.order().payloadHash().equals(payloadHash)) {
                throw new OrderExceptions.IdempotencyConflict(
                        "Idempotency-Key was already used with a different request");
            }
            return response(existing.order());
        }

        var event =
                new OrderPlacedEvent(
                        UUID.randomUUID(),
                        OrderPlacedEvent.CURRENT_VERSION,
                        order.orderId(),
                        correlationId,
                        now,
                        order.customerId(),
                        order.items(),
                        order.currency(),
                        order.totalAmount());
        try {
            publisher.publish(event);
        } catch (RuntimeException ex) {
            throw new OrderExceptions.DependencyFailure("Failed to publish the order event", ex);
        }
        try (var ignoredOrder = MDC.putCloseable("orderId", order.orderId().toString());
                var ignoredCorrelation = MDC.putCloseable("correlationId", correlationId)) {
            LOG.info("order accepted orderId={} correlationId={}", order.orderId(), correlationId);
        }
        return response(order);
    }

    public OrderResponse get(UUID orderId) {
        return repository
                .findById(orderId)
                .map(OrderService::response)
                .orElseThrow(() -> new OrderExceptions.NotFound("Order not found: " + orderId));
    }

    private static String hash(String customerId, List<OrderItem> items, Money total) {
        try {
            String canonical =
                    JsonSupport.mapper()
                            .writeValueAsString(
                                    List.of(customerId, items, total.currency(), total.amount()));
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static OrderResponse response(OrderRecord order) {
        return new OrderResponse(
                order.orderId(),
                order.customerId(),
                order.status(),
                order.items(),
                order.currency(),
                order.totalAmount(),
                order.receiptKey(),
                order.correlationId(),
                order.createdAt(),
                order.updatedAt());
    }
}
