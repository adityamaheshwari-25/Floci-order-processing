package com.endava.floci.orders.processor;

public interface ReceiptStore {
    void put(String key, String json);
}
