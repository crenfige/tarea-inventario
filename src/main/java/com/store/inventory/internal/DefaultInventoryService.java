package com.store.inventory.internal;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.api.StockAlertListener;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultInventoryService implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final Clock clock;
    private final StockAlertListener alertListener;
    private final CategoryPolicyRegistry policyRegistry;
    private final ConcurrentMap<String, ProductStock> products = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReservationRecord> reservationsByOrderId = new ConcurrentHashMap<>();

    public DefaultInventoryService(Clock clock, StockAlertListener alertListener) {
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
        this.alertListener = Objects.requireNonNull(alertListener, "StockAlertListener cannot be null");
        this.policyRegistry = new CategoryPolicyRegistry();
    }

    @Override
    public void registerProduct(String sku, ProductCategory category) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        ProductStock previous = products.putIfAbsent(sku, new ProductStock(sku, category));
        if (previous != null) {
            throw new IllegalArgumentException("Product is already registered: " + sku);
        }
    }

    @Override
    public void addStock(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, requested: " + quantity);
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or blank");
        }
        ProductStock product = products.get(sku);
        if (product == null) {
            throw new IllegalArgumentException("Product is not registered: " + sku);
        }

        product.getLock().lock();
        try {
            product.addStock(quantity);
        } finally {
            product.getLock().unlock();
        }
    }

    @Override
    public Reservation reserve(String orderId, String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, requested: " + quantity);
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }
        if (sku == null || sku.isBlank()) {
            throw new InsufficientStockException(sku, quantity, 0);
        }

        ProductStock product = products.get(sku);
        if (product == null) {
            throw new InsufficientStockException(sku, quantity, 0);
        }

        CategoryPolicy policy = policyRegistry.getPolicy(product.getCategory());
        if (policy.hasLimit() && quantity > policy.getLimit()) {
            throw new OrderLimitExceededException(sku, quantity, policy.getLimit());
        }

        boolean triggerAlert = false;
        int remainingAvailable = 0;
        Reservation reservation;

        product.getLock().lock();
        try {
            Instant now = clock.instant();

            ReservationRecord existingRecord = reservationsByOrderId.get(orderId);
            if (existingRecord != null) {
                if (existingRecord.isConfirmed()) {
                    throw new IllegalStateException("Order " + orderId + " is already confirmed");
                }
                Reservation existing = existingRecord.getReservation();
                if (existing.expiresAt().isAfter(now)) {
                    if (existing.sku().equals(sku) && existing.quantity() == quantity) {
                        return existing;
                    }
                    throw new IllegalArgumentException("Order ID already has an active reservation with different attributes");
                } else {
                    product.removeReservation(orderId);
                    reservationsByOrderId.remove(orderId);
                }
            }

            int currentAvailable = product.getAvailableUnits(now);
            if (quantity > currentAvailable) {
                throw new InsufficientStockException(sku, quantity, currentAvailable);
            }

            Instant expiresAt = now.plus(policy.ttl());
            reservation = new Reservation(orderId, sku, quantity, expiresAt);

            product.addReservation(reservation);
            reservationsByOrderId.put(orderId, new ReservationRecord(reservation));

            remainingAvailable = product.getAvailableUnits(now);
            if (remainingAvailable <= LOW_STOCK_THRESHOLD && !product.isAlertSent()) {
                product.setAlertSent(true);
                triggerAlert = true;
            }
        } finally {
            product.getLock().unlock();
        }

        if (triggerAlert) {
            alertListener.onLowStock(sku, remainingAvailable);
        }

        return reservation;
    }

    @Override
    public void confirm(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalStateException("Order ID cannot be null or blank");
        }

        ReservationRecord record = reservationsByOrderId.get(orderId);
        if (record == null) {
            throw new IllegalStateException("No active reservation found for order: " + orderId);
        }

        if (record.isConfirmed()) {
            throw new IllegalStateException("Order " + orderId + " has already been confirmed");
        }

        Reservation reservation = record.getReservation();
        ProductStock product = products.get(reservation.sku());
        if (product == null) {
            throw new IllegalStateException("Product not registered for reservation: " + reservation.sku());
        }

        product.getLock().lock();
        try {
            Instant now = clock.instant();
            if (record.isConfirmed()) {
                throw new IllegalStateException("Order " + orderId + " has already been confirmed");
            }
            if (!reservation.expiresAt().isAfter(now)) {
                product.removeReservation(orderId);
                throw new IllegalStateException("Reservation for order " + orderId + " has expired");
            }

            boolean confirmed = product.confirmReservation(orderId, now);
            if (!confirmed) {
                throw new IllegalStateException("Reservation for order " + orderId + " is no longer active");
            }
            record.setConfirmed(true);
        } finally {
            product.getLock().unlock();
        }
    }

    @Override
    public int available(String sku) {
        if (sku == null || sku.isBlank()) {
            return 0;
        }
        ProductStock product = products.get(sku);
        if (product == null) {
            return 0;
        }
        product.getLock().lock();
        try {
            return product.getAvailableUnits(clock.instant());
        } finally {
            product.getLock().unlock();
        }
    }
}