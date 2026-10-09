package com.store.inventory.internal;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class CategoryPolicyRegistry {

    private final Map<ProductCategory, CategoryPolicy> policies = new EnumMap<>(ProductCategory.class);

    public CategoryPolicyRegistry() {
        policies.put(ProductCategory.STANDARD, CategoryPolicy.unlimited(Duration.ofMinutes(15)));
        policies.put(ProductCategory.PRE_ORDER, CategoryPolicy.unlimited(Duration.ofHours(24)));
        policies.put(ProductCategory.FLASH_SALE, CategoryPolicy.withLimit(Duration.ofMinutes(5), 2));
    }

    public CategoryPolicy getPolicy(ProductCategory category) {
        Objects.requireNonNull(category, "Category cannot be null");
        CategoryPolicy policy = policies.get(category);
        if (policy == null) {
            throw new IllegalArgumentException("No policy found for category: " + category);
        }
        return policy;
    }

    public void registerPolicy(ProductCategory category, CategoryPolicy policy) {
        Objects.requireNonNull(category, "Category cannot be null");
        Objects.requireNonNull(policy, "Policy cannot be null");
        policies.put(category, policy);
    }
}