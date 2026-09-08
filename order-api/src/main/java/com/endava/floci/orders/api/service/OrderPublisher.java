package com.endava.floci.orders.api.service;

import com.endava.floci.orders.domain.OrderPlacedEvent;

public interface OrderPublisher {
    void publish(OrderPlacedEvent event);
}
