package com.store.inventory.api;

/**
 * Do not modify this file.
 */
public class OrderLimitExceededException extends RuntimeException {

    public OrderLimitExceededException(String sku, int requested, int limit) {
        super("Order limit for " + sku + " is " + limit + " units, requested " + requested);
    }
}
