package com.endava.floci.orders.api.service;

public final class OrderExceptions {
    private OrderExceptions() {}

    public static final class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    public static final class IdempotencyConflict extends RuntimeException {
        public IdempotencyConflict(String message) {
            super(message);
        }
    }

    public static final class DependencyFailure extends RuntimeException {
        public DependencyFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
