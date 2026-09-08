package com.endava.floci.orders.processor;

public final class ProcessingExceptions {
    private ProcessingExceptions() {}

    public static class Retryable extends RuntimeException {
        public Retryable(String message, Throwable cause) {
            super(message, cause);
        }

        public Retryable(String message) {
            super(message);
        }
    }

    public static class Poison extends RuntimeException {
        public Poison(String message) {
            super(message);
        }

        public Poison(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
