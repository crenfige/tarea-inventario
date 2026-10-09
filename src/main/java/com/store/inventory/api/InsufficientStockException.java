package com.store.inventory.api;

/**
 * Do not modify this file.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for " + sku + ": requested " + requested + ", available " + available);
    }
}
