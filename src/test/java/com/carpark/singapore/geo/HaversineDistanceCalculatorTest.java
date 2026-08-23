package com.carpark.singapore.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Uses mathematically exact reference distances (fractions of Earth's circumference)
 * rather than approximate real-world distances, so the expected values are derived
 * independently of this implementation rather than by trusting it.
 */
class HaversineDistanceCalculatorTest {

    private static final double KM_TOLERANCE = 0.01;
    // A quarter of the sphere's circumference (Earth's radius as assumed by this
    // calculator: 6371.0088 km), e.g. equator to pole, or a quarter of the equator.
    private static final double QUARTER_CIRCUMFERENCE_KM = 10007.557221017962;

    @Test
    void distanceFromAPointToItselfIsZero() {
        WgsCoordinate point = new WgsCoordinate(1.3521, 103.8198);

        assertThat(HaversineDistanceCalculator.distanceKm(point, point)).isZero();
    }

    @Test
    void distanceAcrossAQuarterOfTheEquatorMatchesAQuarterCircumference() {
        WgsCoordinate start = new WgsCoordinate(0, 0);
        WgsCoordinate quarterWayAround = new WgsCoordinate(0, 90);

        double distance = HaversineDistanceCalculator.distanceKm(start, quarterWayAround);

        assertThat(distance).isCloseTo(QUARTER_CIRCUMFERENCE_KM, within(KM_TOLERANCE));
    }

    @Test
    void distanceFromEquatorToPoleMatchesAQuarterCircumference() {
        WgsCoordinate equator = new WgsCoordinate(0, 0);
        WgsCoordinate northPole = new WgsCoordinate(90, 0);

        double distance = HaversineDistanceCalculator.distanceKm(equator, northPole);

        assertThat(distance).isCloseTo(QUARTER_CIRCUMFERENCE_KM, within(KM_TOLERANCE));
    }

    @Test
    void distanceIsSymmetric() {
        WgsCoordinate a = new WgsCoordinate(1.3010626054202958, 103.85411771659147);
        WgsCoordinate b = new WgsCoordinate(1.456621749809935, 103.6854073065924);

        assertThat(HaversineDistanceCalculator.distanceKm(a, b))
                .isCloseTo(HaversineDistanceCalculator.distanceKm(b, a), within(1e-9));
    }

    @Test
    void distanceBetweenTwoRealSingaporeCarparksIsWithinAPlausibleRange() {
        // ACB (Albert Centre, near Bugis) and a car park in the far west of Singapore.
        WgsCoordinate acb = new WgsCoordinate(1.3010626054202958, 103.85411771659147);
        WgsCoordinate farWest = new WgsCoordinate(1.456621749809935, 103.6854073065924);

        double distance = HaversineDistanceCalculator.distanceKm(acb, farWest);

        // Singapore is roughly 50km east-west/north-south, so any two points on the
        // island are well under that; this guards against gross unit/formula errors.
        assertThat(distance).isBetween(1.0, 50.0);
    }
}
