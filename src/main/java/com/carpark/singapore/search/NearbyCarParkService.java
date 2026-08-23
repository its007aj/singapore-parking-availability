package com.carpark.singapore.search;

import com.carpark.singapore.geo.HaversineDistanceCalculator;
import com.carpark.singapore.geo.WgsCoordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Finds car parks with available lots near a location, nearest first.
 *
 * <p>Results are not restricted to car parks whose data is fresh — a car park just outside
 * the staleness window is still more useful to show (flagged as {@code stale}) than to hide
 * outright, since its last-known availability is likely still roughly accurate.
 */
@Service
public class NearbyCarParkService {

    private final NearbyCarParkQuery query;
    private final Clock clock;
    private final Duration staleAfter;

    NearbyCarParkService(
            NearbyCarParkQuery query,
            Clock clock,
            @Value("${parking.availability.stale-after-minutes}") long staleAfterMinutes) {
        this.query = query;
        this.clock = clock;
        this.staleAfter = Duration.ofMinutes(staleAfterMinutes);
    }

    List<NearbyCarPark> findNearby(WgsCoordinate location, double radiusKm, int limit) {
        Instant now = clock.instant();
        return query.findAvailableCarParks().stream()
                .map(snapshot -> toNearbyCarPark(snapshot, location, now))
                .filter(carPark -> carPark.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyCarPark::distanceKm))
                .limit(limit)
                .toList();
    }

    private NearbyCarPark toNearbyCarPark(CarParkAvailabilitySnapshot snapshot, WgsCoordinate from, Instant now) {
        WgsCoordinate carParkLocation = new WgsCoordinate(snapshot.latitude(), snapshot.longitude());
        double distanceKm = HaversineDistanceCalculator.distanceKm(from, carParkLocation);
        boolean stale = Duration.between(snapshot.lotUpdatedAt(), now).compareTo(staleAfter) > 0;

        return new NearbyCarPark(
                snapshot.carParkNo(),
                snapshot.address(),
                snapshot.latitude(),
                snapshot.longitude(),
                distanceKm,
                snapshot.lotsAvailable(),
                snapshot.totalLots(),
                snapshot.lotUpdatedAt(),
                stale);
    }
}
