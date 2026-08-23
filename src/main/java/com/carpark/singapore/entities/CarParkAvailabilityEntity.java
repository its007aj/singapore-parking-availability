package com.carpark.singapore.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** A car park's availability for one lot type (e.g. "C" = car), as of the last live fetch. */
@Entity
@Table(name = "car_park_availability")
public class CarParkAvailabilityEntity {

    @EmbeddedId
    private CarParkAvailabilityId id;

    @Column(name = "total_lots", nullable = false)
    private int totalLots;

    @Column(name = "lots_available", nullable = false)
    private int lotsAvailable;

    @Column(name = "lot_updated_at", nullable = false)
    private Instant lotUpdatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected CarParkAvailabilityEntity() {
        // required by JPA
    }

    public CarParkAvailabilityEntity(
            String carParkNo, String lotType, int totalLots, int lotsAvailable, Instant lotUpdatedAt, Instant fetchedAt) {
        this.id = new CarParkAvailabilityId(carParkNo, lotType);
        this.totalLots = totalLots;
        this.lotsAvailable = lotsAvailable;
        this.lotUpdatedAt = lotUpdatedAt;
        this.fetchedAt = fetchedAt;
    }

    public String getCarParkNo() {
        return id.getCarParkNo();
    }

    public String getLotType() {
        return id.getLotType();
    }

    public int getTotalLots() {
        return totalLots;
    }

    public int getLotsAvailable() {
        return lotsAvailable;
    }

    public Instant getLotUpdatedAt() {
        return lotUpdatedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
