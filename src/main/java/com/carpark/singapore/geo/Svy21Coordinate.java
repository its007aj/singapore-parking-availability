package com.carpark.singapore.geo;

/**
 * A coordinate in the SVY21 projected coordinate system (EPSG:3414) used by
 * Singapore government datasets, expressed in metres from the projection's false origin.
 */
public record Svy21Coordinate(double easting, double northing) {
}
