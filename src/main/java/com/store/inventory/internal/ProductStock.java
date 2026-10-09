package com.store.inventory.internal;

import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ProductStock {

    private final String sku;
    private final ProductCategory category;
    private int totalStock;
    private boolean alertSent;
    private final Map<String, Reservation> activeReservations = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public ProductStock(String sku, ProductCategory category) {
        this.sku = sku;
        this.category = category;
        this.totalStock = 0;
        this.alertSent = false;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public String getSku() {
        return sku;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void addStock(int quantity) {
        this.totalStock += quantity;
        this.alertSent = false; // Se reinicia el aviso cuando Compras reabastece
    }

    public void purgeExpired(Instant now) {
        activeReservations.values().removeIf(res -> !res.expiresAt().isAfter(now));
    }

    public int getAvailableUnits(Instant now) {
        purgeExpired(now);
        int reservedUnits = activeReservations.values().stream()
                .mapToInt(Reservation::quantity)
                .sum();
        return Math.max(0, totalStock - reservedUnits);
    }

    public Reservation getActiveReservation(String orderId, Instant now) {
        purgeExpired(now);
        return activeReservations.get(orderId);
    }

    public void addReservation(Reservation reservation) {
        activeReservations.put(reservation.orderId(), reservation);
    }

    public void removeReservation(String orderId) {
        activeReservations.remove(orderId);
    }

    public boolean confirmReservation(String orderId, Instant now) {
        purgeExpired(now);
        Reservation reservation = activeReservations.remove(orderId);
        if (reservation == null) {
            return false;
        }
        this.totalStock -= reservation.quantity();
        return true;
    }

    public boolean isAlertSent() {
        return alertSent;
    }

    public void setAlertSent(boolean alertSent) {
        this.alertSent = alertSent;
    }
}