package com.carpark.singapore.search;

import com.carpark.singapore.geo.WgsCoordinate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class NearbyCarParkServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");
    private static final long STALE_AFTER_MINUTES = 15;
    // Singapore city-hall-ish reference point; the snapshots below are placed at
    // increasing distances due east of it purely to get a known distance ordering.
    private static final WgsCoordinate USER_LOCATION = new WgsCoordinate(1.30, 103.85);

    private final NearbyCarParkQuery query = mock(NearbyCarParkQuery.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final NearbyCarParkService service =
            new NearbyCarParkService(query, clock, STALE_AFTER_MINUTES);

    @Test
    void sortsResultsByAscendingDistanceFromTheUser() {
        CarParkAvailabilitySnapshot far = snapshotAt("FAR", 1.30, 103.95, NOW);
        CarParkAvailabilitySnapshot near = snapshotAt("NEAR", 1.30, 103.851, NOW);
        CarParkAvailabilitySnapshot mid = snapshotAt("MID", 1.30, 103.90, NOW);
        given(query.findAvailableCarParks()).willReturn(List.of(far, near, mid));

        List<NearbyCarPark> result = service.findNearby(USER_LOCATION, 50.0, 10);

        assertThat(result).extracting(NearbyCarPark::carParkNo).containsExactly("NEAR", "MID", "FAR");
        assertThat(result.get(0).distanceKm())
                .isLessThan(result.get(1).distanceKm())
                .isLessThan(result.get(2).distanceKm());
    }

    @Test
    void excludesResultsOutsideTheRequestedRadius() {
        CarParkAvailabilitySnapshot withinRadius = snapshotAt("CLOSE", 1.30, 103.851, NOW);
        CarParkAvailabilitySnapshot outsideRadius = snapshotAt("FAR_AWAY", 1.30, 104.50, NOW);
        given(query.findAvailableCarParks()).willReturn(List.of(withinRadius, outsideRadius));

        List<NearbyCarPark> result = service.findNearby(USER_LOCATION, 5.0, 10);

        assertThat(result).extracting(NearbyCarPark::carParkNo).containsExactly("CLOSE");
    }

    @Test
    void limitsTheNumberOfResultsReturned() {
        List<CarParkAvailabilitySnapshot> manySnapshots = List.of(
                snapshotAt("A", 1.30, 103.851, NOW),
                snapshotAt("B", 1.30, 103.852, NOW),
                snapshotAt("C", 1.30, 103.853, NOW));
        given(query.findAvailableCarParks()).willReturn(manySnapshots);

        List<NearbyCarPark> result = service.findNearby(USER_LOCATION, 50.0, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void flagsACarParkAsStaleOnceItsLastUpdateExceedsTheConfiguredThreshold() {
        Instant justUnderThreshold = NOW.minusSeconds(14 * 60);
        Instant justOverThreshold = NOW.minusSeconds(16 * 60);
        CarParkAvailabilitySnapshot fresh = snapshotAt("FRESH", 1.30, 103.851, justUnderThreshold);
        CarParkAvailabilitySnapshot stale = snapshotAt("STALE", 1.30, 103.852, justOverThreshold);
        given(query.findAvailableCarParks()).willReturn(List.of(fresh, stale));

        List<NearbyCarPark> result = service.findNearby(USER_LOCATION, 50.0, 10);

        assertThat(result).filteredOn(carPark -> carPark.carParkNo().equals("FRESH"))
                .extracting(NearbyCarPark::stale).containsExactly(false);
        assertThat(result).filteredOn(carPark -> carPark.carParkNo().equals("STALE"))
                .extracting(NearbyCarPark::stale).containsExactly(true);
    }

    private static CarParkAvailabilitySnapshot snapshotAt(String carParkNo, double lat, double lon, Instant lotUpdatedAt) {
        return new CarParkAvailabilitySnapshot(carParkNo, "Some address", lat, lon, 10, 100, lotUpdatedAt);
    }
}
