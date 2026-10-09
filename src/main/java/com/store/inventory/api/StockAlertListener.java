package com.store.inventory.api;

/**
 * Receives low stock alerts. Do not modify this file.
 */
public interface StockAlertListener {

    void onLowStock(String sku, int availableUnits);
}
