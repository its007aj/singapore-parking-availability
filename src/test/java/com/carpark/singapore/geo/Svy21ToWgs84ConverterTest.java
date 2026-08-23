package com.carpark.singapore.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Expected values were computed by an independent Python port of the same published
 * SVY21 inverse-transverse-Mercator formula, not derived from {@link Svy21ToWgs84Converter}
 * itself, so these tests catch transcription errors in this class rather than just
 * confirming it agrees with itself.
 */
class Svy21ToWgs84ConverterTest {

    private static final double DEGREES_TOLERANCE = 1e-9;

    // Singapore's approximate bounding box, used as a sanity guard against gross errors
    // (e.g. swapped easting/northing arguments) rather than as a precise assertion.
    private static final double SINGAPORE_MIN_LATITUDE = 1.13;
    private static final double SINGAPORE_MAX_LATITUDE = 1.48;
    private static final double SINGAPORE_MIN_LONGITUDE = 103.55;
    private static final double SINGAPORE_MAX_LONGITUDE = 104.15;

    @Test
    void convertsFalseOriginBackToProjectionOriginLatLon() {
        Svy21Coordinate falseOrigin = new Svy21Coordinate(28001.642, 38744.572);

        WgsCoordinate result = Svy21ToWgs84Converter.convert(falseOrigin);

        assertThat(result.latitude()).isCloseTo(1.3666659999997715, within(DEGREES_TOLERANCE));
        assertThat(result.longitude()).isCloseTo(103.833333, within(DEGREES_TOLERANCE));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // name,        easting,     northing,    expectedLat,          expectedLon
            "ACB carpark, 30314.7936, 31490.4942, 1.3010626054202958, 103.85411771659147",
            "ACM carpark, 33758.4143, 33695.5198, 1.321003623439093,  103.8850606142569",
            "AH1 carpark, 29257.7203, 34500.3599, 1.3282828284423165, 103.84461955580953",
    })
    void convertsRealCarparkCoordinatesToKnownLatLon(String name, double easting, double northing,
            double expectedLatitude, double expectedLongitude) {
        WgsCoordinate result = Svy21ToWgs84Converter.convert(new Svy21Coordinate(easting, northing));

        assertThat(result.latitude()).isCloseTo(expectedLatitude, within(DEGREES_TOLERANCE));
        assertThat(result.longitude()).isCloseTo(expectedLongitude, within(DEGREES_TOLERANCE));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "ACB carpark, 30314.7936, 31490.4942",
            "ACM carpark, 33758.4143, 33695.5198",
            "AH1 carpark, 29257.7203, 34500.3599",
    })
    void convertedCoordinatesFallWithinSingaporesBoundingBox(String name, double easting, double northing) {
        WgsCoordinate result = Svy21ToWgs84Converter.convert(new Svy21Coordinate(easting, northing));

        assertThat(result.latitude()).isBetween(SINGAPORE_MIN_LATITUDE, SINGAPORE_MAX_LATITUDE);
        assertThat(result.longitude()).isBetween(SINGAPORE_MIN_LONGITUDE, SINGAPORE_MAX_LONGITUDE);
    }
}
