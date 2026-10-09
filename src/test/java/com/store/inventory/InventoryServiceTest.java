package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private TestClock clock;
    private List<String> alerts;
    private InventoryService service;

    static class TestClock extends Clock {
        private Instant current;
        private final ZoneId zone;

        TestClock(Instant start, ZoneId zone) {
            this.current = start;
            this.zone = zone;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new TestClock(current, zone); }
        @Override public Instant instant() { return current; }
    }

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-10-08T12:00:00Z"), ZoneId.of("UTC"));
        alerts = Collections.synchronizedList(new ArrayList<>());
        service = Inventory.create(clock, (sku, available) -> alerts.add(sku + ":" + available));
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
    }

    @Test
    void reservingReducesAvailableUnits() {
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 3);
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        service.addStock("SKU-1", 2);
        assertThrows(InsufficientStockException.class, () -> service.reserve("ORDER-1", "SKU-1", 3));
    }

    @Test
    void confirmedUnitsStaySold() {
        service.addStock("SKU-1", 5);
        service.reserve("ORDER-1", "SKU-1", 2);
        service.confirm("ORDER-1");
        assertEquals(3, service.available("SKU-1"));
    }

    @Test
    void flashSaleEnforcesOrderLimit() {
        service.registerProduct("FLASH-1", ProductCategory.FLASH_SALE);
        service.addStock("FLASH-1", 10);

        assertThrows(OrderLimitExceededException.class, () -> service.reserve("ORD-FS-1", "FLASH-1", 3));
        Reservation res = service.reserve("ORD-FS-2", "FLASH-1", 2);
        assertNotNull(res);
        assertEquals(8, service.available("FLASH-1"));
    }

    @Test
    void reservationExpiresAndReleasesUnits() {
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-EXP", "SKU-1", 4);
        assertEquals(6, service.available("SKU-1"));

        // Standard expira a los 15 minutos
        clock.advance(Duration.ofMinutes(16));
        assertEquals(10, service.available("SKU-1"));
    }

    @Test
    void cannotConfirmExpiredReservation() {
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-EXP", "SKU-1", 4);

        clock.advance(Duration.ofMinutes(16));
        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-EXP"));
    }

    @Test
    void reserveIsIdempotentForSameOrder() {
        service.addStock("SKU-1", 10);
        Reservation r1 = service.reserve("RETRY-ORDER", "SKU-1", 3);
        Reservation r2 = service.reserve("RETRY-ORDER", "SKU-1", 3);

        assertEquals(r1, r2);
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void lowStockAlertTriggersOnlyOnceUntilRestocked() {
        service.addStock("SKU-1", 10);
        service.reserve("ORD-1", "SKU-1", 5); // Quedan 5 -> Alerta disparada
        assertEquals(1, alerts.size());
        assertEquals("SKU-1:5", alerts.get(0));

        service.reserve("ORD-2", "SKU-1", 2); // Quedan 3 -> No repite alerta
        assertEquals(1, alerts.size());

        service.addStock("SKU-1", 10); // Reabastecido -> resetea aviso
        service.reserve("ORD-3", "SKU-1", 9); // Quedan 4 -> Nueva alerta disparada
        assertEquals(2, alerts.size());
        assertEquals("SKU-1:4", alerts.get(1));
    }

    @Test
    void concurrentReservationsDoNotOversell() throws InterruptedException {
        service.addStock("SKU-1", 10);
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final String orderId = "CONC-ORD-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.reserve(orderId, "SKU-1", 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failedCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(10, successCount.get());
        assertEquals(10, failedCount.get());
        assertEquals(0, service.available("SKU-1"));
    }
}