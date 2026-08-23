package com.carpark.singapore.search;

import java.time.Instant;

/** A car park joined with its current availability, as read from the database. */
public record CarParkAvailabilitySnapshot(
        String carParkNo,
        String address,
        double latitude,
        double longitude,
        int lotsAvailable,
        int totalLots,
        Instant lotUpdatedAt) {
}
