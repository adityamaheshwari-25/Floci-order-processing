package com.endava.floci.orders.api.service;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    CreateResult create(OrderRecord order);

    Optional<OrderRecord> findById(UUID orderId);

    sealed interface CreateResult {
        record Created(OrderRecord order) implements CreateResult {}

        record Existing(OrderRecord order) implements CreateResult {}
    }
}
