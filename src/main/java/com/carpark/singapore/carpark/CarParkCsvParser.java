package com.carpark.singapore.carpark;

import com.carpark.singapore.entities.CarParkEntity;
import com.carpark.singapore.geo.Svy21Coordinate;
import com.carpark.singapore.geo.Svy21ToWgs84Converter;
import com.carpark.singapore.geo.WgsCoordinate;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the HDB car park information CSV (RFC4180 format — addresses may contain
 * quoted commas) into {@link CarParkEntity} rows, transforming each row's SVY21
 * coordinates to WGS84 latitude/longitude.
 */
public final class CarParkCsvParser {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    private CarParkCsvParser() {
    }

    public static List<CarParkEntity> parse(Reader csvReader) throws IOException {
        List<CarParkEntity> carParks = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(csvReader, CSV_FORMAT)) {
            for (CSVRecord record : parser) {
                carParks.add(toCarPark(record));
            }
        }
        return carParks;
    }

    private static CarParkEntity toCarPark(CSVRecord record) {
        WgsCoordinate location = convertLocation(record);
        return new CarParkEntity(
                record.get("car_park_no"),
                record.get("address"),
                location.latitude(),
                location.longitude(),
                record.get("car_park_type"),
                record.get("type_of_parking_system"),
                record.get("short_term_parking"),
                record.get("free_parking"),
                record.get("night_parking"),
                parseIntOrZero(record.get("car_park_decks")),
                parseDoubleOrZero(record.get("gantry_height")),
                record.get("car_park_basement"));
    }

    private static WgsCoordinate convertLocation(CSVRecord record) {
        double easting = Double.parseDouble(record.get("x_coord"));
        double northing = Double.parseDouble(record.get("y_coord"));
        return Svy21ToWgs84Converter.convert(new Svy21Coordinate(easting, northing));
    }

    private static int parseIntOrZero(String value) {
        return value.isBlank() ? 0 : Integer.parseInt(value.trim());
    }

    private static double parseDoubleOrZero(String value) {
        return value.isBlank() ? 0.0 : Double.parseDouble(value.trim());
    }
}
