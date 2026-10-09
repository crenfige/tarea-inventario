package com.store.inventory.internal;

import com.store.inventory.api.Reservation;

public class ReservationRecord {

    private final Reservation reservation;
    private volatile boolean confirmed;

    public ReservationRecord(Reservation reservation) {
        this.reservation = reservation;
        this.confirmed = false;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}