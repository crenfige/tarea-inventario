package com.store.inventory.api;

import java.time.Instant;

/**
 * Do not modify this file.
 */
public record Reservation(String orderId, String sku, int quantity, Instant expiresAt) {
}
