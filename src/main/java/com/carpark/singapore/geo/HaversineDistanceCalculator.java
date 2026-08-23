package com.carpark.singapore.geo;

/** Great-circle distance between two WGS84 points, via the haversine formula. */
public final class HaversineDistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private HaversineDistanceCalculator() {
    }

    public static double distanceKm(WgsCoordinate from, WgsCoordinate to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());

        double a = haversine(deltaLat) + Math.cos(lat1) * Math.cos(lat2) * haversine(deltaLon);
        double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * centralAngle;
    }

    private static double haversine(double angleRadians) {
        double halfSine = Math.sin(angleRadians / 2);
        return halfSine * halfSine;
    }
}
