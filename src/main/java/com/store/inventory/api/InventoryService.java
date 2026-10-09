package com.store.inventory.api;

/**
 * Public contract of the inventory service. Do not modify this file.
 */
public interface InventoryService {

    /**
     * Registers a product and its category. A product must be registered before adding stock.
     */
    void registerProduct(String sku, ProductCategory category);

    /**
     * Adds units of a registered product to the warehouse.
     *
     * @throws IllegalArgumentException if quantity is zero or negative, or the product is not registered
     */
    void addStock(String sku, int quantity);

    /**
     * Reserves units of a product for an order. Each order reserves a single product.
     * The reservation expires according to the product category unless it is confirmed.
     *
     * @throws IllegalArgumentException if quantity is zero or negative
     * @throws OrderLimitExceededException if the category does not allow that many units per order
     * @throws InsufficientStockException if there are not enough available units (unknown products have none)
     */
    Reservation reserve(String orderId, String sku, int quantity);

    /**
     * Confirms the reservation of a paid order. Confirmed units are sold and never return to stock.
     *
     * @throws IllegalStateException if the order has no active reservation
     */
    void confirm(String orderId);

    /**
     * Units that can still be reserved: stock minus active (non-expired) reservations.
     * Returns 0 for unknown products.
     */
    int available(String sku);
}
