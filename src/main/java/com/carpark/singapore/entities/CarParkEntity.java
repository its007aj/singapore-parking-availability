package com.carpark.singapore.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** A car park's static, rarely-changing details from the HDB car park information dataset. */
@Entity
@Table(name = "car_park")
public class CarParkEntity {

    @Id
    @Column(name = "car_park_no")
    private String carParkNo;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "car_park_type")
    private String carParkType;

    @Column(name = "type_of_parking_system")
    private String typeOfParkingSystem;

    @Column(name = "short_term_parking")
    private String shortTermParking;

    @Column(name = "free_parking")
    private String freeParking;

    @Column(name = "night_parking")
    private String nightParking;

    @Column(name = "car_park_decks")
    private int carParkDecks;

    @Column(name = "gantry_height")
    private double gantryHeight;

    @Column(name = "car_park_basement")
    private String carParkBasement;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CarParkEntity() {
        // required by JPA
    }

    public CarParkEntity(
            String carParkNo,
            String address,
            double latitude,
            double longitude,
            String carParkType,
            String typeOfParkingSystem,
            String shortTermParking,
            String freeParking,
            String nightParking,
            int carParkDecks,
            double gantryHeight,
            String carParkBasement) {
        this.carParkNo = carParkNo;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.carParkType = carParkType;
        this.typeOfParkingSystem = typeOfParkingSystem;
        this.shortTermParking = shortTermParking;
        this.freeParking = freeParking;
        this.nightParking = nightParking;
        this.carParkDecks = carParkDecks;
        this.gantryHeight = gantryHeight;
        this.carParkBasement = carParkBasement;
    }

    public String getCarParkNo() {
        return carParkNo;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getCarParkType() {
        return carParkType;
    }

    public String getTypeOfParkingSystem() {
        return typeOfParkingSystem;
    }

    public String getShortTermParking() {
        return shortTermParking;
    }

    public String getFreeParking() {
        return freeParking;
    }

    public String getNightParking() {
        return nightParking;
    }

    public int getCarParkDecks() {
        return carParkDecks;
    }

    public double getGantryHeight() {
        return gantryHeight;
    }

    public String getCarParkBasement() {
        return carParkBasement;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
