package com.store.inventory.internal;

import java.time.Duration;
import java.util.OptionalInt;

public record CategoryPolicy(Duration ttl, OptionalInt orderLimit) {

    public static CategoryPolicy unlimited(Duration ttl) {
        return new CategoryPolicy(ttl, OptionalInt.empty());
    }

    public static CategoryPolicy withLimit(Duration ttl, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        return new CategoryPolicy(ttl, OptionalInt.of(limit));
    }

    public boolean hasLimit() {
        return orderLimit.isPresent();
    }

    public int getLimit() {
        return orderLimit.orElseThrow();
    }
}