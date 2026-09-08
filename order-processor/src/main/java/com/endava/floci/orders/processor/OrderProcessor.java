package com.endava.floci.orders.processor;

import com.endava.floci.orders.domain.JsonSupport;
import com.endava.floci.orders.domain.OrderPlacedEvent;
import com.endava.floci.orders.domain.Receipt;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OrderProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(OrderProcessor.class);
    private final OrderStateStore stateStore;
    private final ReceiptStore receiptStore;
    private final Clock clock;

    public OrderProcessor(OrderStateStore stateStore, ReceiptStore receiptStore, Clock clock) {
        this.stateStore = stateStore;
        this.receiptStore = receiptStore;
        this.clock = clock;
    }

    public void process(String messageBody) {
        OrderPlacedEvent event;
        try {
            event = JsonSupport.mapper().readValue(messageBody, OrderPlacedEvent.class);
        } catch (RuntimeException ex) {
            throw new ProcessingExceptions.Poison("Message is not a valid OrderPlaced event", ex);
        }
        if (event.eventVersion() != OrderPlacedEvent.CURRENT_VERSION) {
            throw new ProcessingExceptions.Poison(
                    "Unsupported event version: " + event.eventVersion());
        }
        try (var ignoredOrder = MDC.putCloseable("orderId", event.orderId().toString());
                var ignoredCorrelation = MDC.putCloseable("correlationId", event.correlationId())) {
            Instant now = clock.instant();
            OrderStateStore.ClaimResult claim = stateStore.claim(event.orderId(), now);
            if (claim == OrderStateStore.ClaimResult.COMPLETED_DUPLICATE) {
                LOG.info(
                        "duplicate order event acknowledged orderId={} correlationId={}",
                        event.orderId(),
                        event.correlationId());
                return;
            }
            String receiptKey = "receipts/" + event.orderId() + ".json";
            var receipt =
                    new Receipt(
                            event.orderId(),
                            event.customerId(),
                            event.correlationId(),
                            event.currency(),
                            event.totalAmount(),
                            now);
            receiptStore.put(receiptKey, JsonSupport.mapper().writeValueAsString(receipt));
            stateStore.complete(event.orderId(), receiptKey, now);
            LOG.info(
                    "order processing completed orderId={} correlationId={} receiptKey={}",
                    event.orderId(),
                    event.correlationId(),
                    receiptKey);
        } catch (ProcessingExceptions.Poison | ProcessingExceptions.Retryable ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProcessingExceptions.Retryable("Order processing failed", ex);
        }
    }
}
