package com.carpark.singapore.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link CarParkAvailabilityEntity}: one row per car park + lot type. */
@Embeddable
public class CarParkAvailabilityId implements Serializable {

    @Column(name = "car_park_no")
    private String carParkNo;

    @Column(name = "lot_type")
    private String lotType;

    protected CarParkAvailabilityId() {
        // required by JPA
    }

    public CarParkAvailabilityId(String carParkNo, String lotType) {
        this.carParkNo = carParkNo;
        this.lotType = lotType;
    }

    public String getCarParkNo() {
        return carParkNo;
    }

    public String getLotType() {
        return lotType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CarParkAvailabilityId that)) {
            return false;
        }
        return Objects.equals(carParkNo, that.carParkNo) && Objects.equals(lotType, that.lotType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carParkNo, lotType);
    }
}
