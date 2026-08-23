package com.carpark.singapore.geo;

/**
 * Converts SVY21 (EPSG:3414) coordinates to WGS84 latitude/longitude.
 *
 * <p>Implements the inverse transverse Mercator formulae published by Singapore's
 * SLA for the SVY21 projection (equivalent to the Redfearn/Snyder inverse series).
 * Variable names below follow the standard geodesy symbols used in that formula
 * (e.g. {@code n} = third flattening, {@code psi} = ratio of radii of curvature,
 * {@code t} = tangent of latitude) rather than descriptive names, so the code can
 * be visually checked term-by-term against the published reference formula —
 * renaming these would make the series expansions harder to verify, not easier.
 */
public final class Svy21ToWgs84Converter {

    private static final double DEGREES_TO_RADIANS = Math.PI / 180;

    // WGS84 reference ellipsoid.
    private static final double SEMI_MAJOR_AXIS = 6378137.0;
    private static final double FLATTENING = 1 / 298.257223563;
    private static final double SEMI_MINOR_AXIS = SEMI_MAJOR_AXIS * (1 - FLATTENING);

    // SVY21 projection parameters (EPSG:3414).
    private static final double ORIGIN_LATITUDE_DEGREES = 1.366666;
    private static final double ORIGIN_LONGITUDE_DEGREES = 103.833333;
    private static final double FALSE_NORTHING = 38744.572;
    private static final double FALSE_EASTING = 28001.642;
    private static final double CENTRAL_MERIDIAN_SCALE_FACTOR = 1.0;

    private static final double E2 = (2 * FLATTENING) - (FLATTENING * FLATTENING);
    private static final double E4 = E2 * E2;
    private static final double E6 = E4 * E2;

    // Meridian-distance series coefficients.
    private static final double A0 = 1 - (E2 / 4) - (3 * E4 / 64) - (5 * E6 / 256);
    private static final double A2 = (3.0 / 8.0) * (E2 + (E4 / 4) + (15 * E6 / 128));
    private static final double A4 = (15.0 / 256.0) * (E4 + (3 * E6 / 4));
    private static final double A6 = 35 * E6 / 3072;

    // Third flattening, used in the footpoint-latitude series.
    private static final double N = (SEMI_MAJOR_AXIS - SEMI_MINOR_AXIS) / (SEMI_MAJOR_AXIS + SEMI_MINOR_AXIS);
    private static final double N2 = N * N;
    private static final double N3 = N2 * N;
    private static final double N4 = N2 * N2;

    private static final double MERIDIAN_ARC_UNIT_DISTANCE = SEMI_MAJOR_AXIS * (1 - N) * (1 - N2)
            * (1 + (9 * N2 / 4) + (225 * N4 / 64)) * DEGREES_TO_RADIANS;

    private Svy21ToWgs84Converter() {
    }

    public static WgsCoordinate convert(Svy21Coordinate coordinate) {
        double footpointLatitude = calculateFootpointLatitude(coordinate.northing());

        double sinFootpointLat = Math.sin(footpointLatitude);
        double sin2FootpointLat = sinFootpointLat * sinFootpointLat;
        double meridianRadius = calculateMeridianRadiusOfCurvature(sin2FootpointLat);
        double primeVerticalRadius = calculatePrimeVerticalRadius(sin2FootpointLat);
        double psi = primeVerticalRadius / meridianRadius;
        double t = Math.tan(footpointLatitude);

        double eastingOffset = coordinate.easting() - FALSE_EASTING;
        double x = eastingOffset / (CENTRAL_MERIDIAN_SCALE_FACTOR * primeVerticalRadius);

        double latitude = calculateLatitude(footpointLatitude, eastingOffset, x, psi, t, meridianRadius);
        double longitude = calculateLongitude(latitude, x, psi, t);

        return new WgsCoordinate(latitude / DEGREES_TO_RADIANS, longitude / DEGREES_TO_RADIANS);
    }

    private static double calculateMeridianDistance(double latitudeDegrees) {
        double latitudeRadians = latitudeDegrees * DEGREES_TO_RADIANS;
        return SEMI_MAJOR_AXIS * ((A0 * latitudeRadians) - (A2 * Math.sin(2 * latitudeRadians))
                + (A4 * Math.sin(4 * latitudeRadians)) - (A6 * Math.sin(6 * latitudeRadians)));
    }

    private static double calculateMeridianRadiusOfCurvature(double sinLatitudeSquared) {
        double numerator = SEMI_MAJOR_AXIS * (1 - E2);
        double denominator = Math.pow(1 - E2 * sinLatitudeSquared, 1.5);
        return numerator / denominator;
    }

    private static double calculatePrimeVerticalRadius(double sinLatitudeSquared) {
        return SEMI_MAJOR_AXIS / Math.sqrt(1 - E2 * sinLatitudeSquared);
    }

    /** The latitude of the point on the central meridian with the same meridian distance as this northing. */
    private static double calculateFootpointLatitude(double northing) {
        double northingOffset = northing - FALSE_NORTHING;
        double originMeridianDistance = calculateMeridianDistance(ORIGIN_LATITUDE_DEGREES);
        double meridianDistance = originMeridianDistance + (northingOffset / CENTRAL_MERIDIAN_SCALE_FACTOR);
        double sigma = (meridianDistance / MERIDIAN_ARC_UNIT_DISTANCE) * DEGREES_TO_RADIANS;

        double term1 = ((3 * N / 2) - (27 * N3 / 32)) * Math.sin(2 * sigma);
        double term2 = ((21 * N2 / 16) - (55 * N4 / 32)) * Math.sin(4 * sigma);
        double term3 = (151 * N3 / 96) * Math.sin(6 * sigma);
        double term4 = (1097 * N4 / 512) * Math.sin(8 * sigma);

        return sigma + term1 + term2 + term3 + term4;
    }

    private static double calculateLatitude(double footpointLatitude, double eastingOffset, double x, double psi,
            double t, double meridianRadius) {
        double psi2 = psi * psi;
        double psi3 = psi2 * psi;
        double psi4 = psi3 * psi;
        double t2 = t * t;
        double t4 = t2 * t2;
        double t6 = t4 * t2;
        double x2 = x * x;
        double x3 = x2 * x;
        double x5 = x3 * x2;
        double x7 = x5 * x2;

        double latitudeFactor = t / (CENTRAL_MERIDIAN_SCALE_FACTOR * meridianRadius);
        double term1 = latitudeFactor * ((eastingOffset * x) / 2);
        double term2 = latitudeFactor * ((eastingOffset * x3) / 24)
                * ((-4 * psi2) + (9 * psi * (1 - t2)) + (12 * t2));
        double term3 = latitudeFactor * ((eastingOffset * x5) / 720)
                * ((8 * psi4 * (11 - 24 * t2)) - (12 * psi3 * (21 - 71 * t2))
                        + (15 * psi2 * (15 - 98 * t2 + 15 * t4)) + (180 * psi * (5 * t2 - 3 * t4)) + 360 * t4);
        double term4 = latitudeFactor * ((eastingOffset * x7) / 40320)
                * (1385 - 3633 * t2 + 4095 * t4 + 1575 * t6);

        return footpointLatitude - term1 + term2 - term3 + term4;
    }

    private static double calculateLongitude(double latitude, double x, double psi, double t) {
        double psi2 = psi * psi;
        double psi3 = psi2 * psi;
        double t2 = t * t;
        double t4 = t2 * t2;
        double t6 = t4 * t2;
        double x2 = x * x;
        double x3 = x2 * x;
        double x5 = x3 * x2;
        double x7 = x5 * x2;
        double secLatitude = 1.0 / Math.cos(latitude);

        double term1 = x * secLatitude;
        double term2 = ((x3 * secLatitude) / 6) * (psi + 2 * t2);
        double term3 = ((x5 * secLatitude) / 120)
                * ((-4 * psi3 * (1 - 6 * t2)) + (psi2 * (9 - 68 * t2)) + (72 * psi * t2) + 24 * t4);
        double term4 = ((x7 * secLatitude) / 5040) * (61 + 662 * t2 + 1320 * t4 + 720 * t6);

        return (ORIGIN_LONGITUDE_DEGREES * DEGREES_TO_RADIANS) + term1 - term2 + term3 - term4;
    }
}
