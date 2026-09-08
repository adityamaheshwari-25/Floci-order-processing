package com.endava.floci.orders.processor;

import java.time.Instant;
import java.util.UUID;

public interface OrderStateStore {
    ClaimResult claim(UUID orderId, Instant now);

    void complete(UUID orderId, String receiptKey, Instant now);

    enum ClaimResult {
        CLAIMED, // the order changed from RECEIVED to PROCESSING.
        RESUMED, // it was already PROCESSING, probably because an earlier attempt failed partway
        // through.
        COMPLETED_DUPLICATE // - processing was already completed, so no work is needed.
    }
}
