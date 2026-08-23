package com.carpark.singapore.search;

import java.time.Instant;

/** A car park near the requested location, with its current availability. */
public record NearbyCarPark(
        String carParkNo,
        String address,
        double latitude,
        double longitude,
        double distanceKm,
        int lotsAvailable,
        int totalLots,
        Instant lastUpdated,
        boolean stale) {
}
