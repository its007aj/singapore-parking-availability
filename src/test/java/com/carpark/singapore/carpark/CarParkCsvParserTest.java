package com.carpark.singapore.carpark;

import com.carpark.singapore.entities.CarParkEntity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CarParkCsvParserTest {

    private static final double SINGAPORE_MIN_LATITUDE = 1.13;
    private static final double SINGAPORE_MAX_LATITUDE = 1.48;
    private static final double SINGAPORE_MIN_LONGITUDE = 103.55;
    private static final double SINGAPORE_MAX_LONGITUDE = 104.15;

    @Test
    void parsesQuotedAddressContainingCommasAsASingleField() throws IOException {
        String csv = """
                car_park_no,address,x_coord,y_coord,car_park_type,type_of_parking_system,short_term_parking,free_parking,night_parking,car_park_decks,gantry_height,car_park_basement
                BE28,"BLK 213-215,218-227 BISHAN STREET 23",29639.4805,37778.0547,SURFACE CAR PARK,ELECTRONIC PARKING,7AM-7PM,SUN & PH FR 7AM-10.30PM,NO,0,4.5,N
                """;

        List<CarParkEntity> carParks = CarParkCsvParser.parse(new StringReader(csv));

        assertThat(carParks).hasSize(1);
        CarParkEntity carPark = carParks.get(0);
        assertThat(carPark.getCarParkNo()).isEqualTo("BE28");
        assertThat(carPark.getAddress()).isEqualTo("BLK 213-215,218-227 BISHAN STREET 23");
        assertThat(carPark.getCarParkType()).isEqualTo("SURFACE CAR PARK");
        assertThat(carPark.getCarParkDecks()).isZero();
        assertThat(carPark.getGantryHeight()).isEqualTo(4.5);
    }

    @Test
    void transformsRowCoordinatesIntoValidSingaporeLatLon() throws IOException {
        String csv = """
                car_park_no,address,x_coord,y_coord,car_park_type,type_of_parking_system,short_term_parking,free_parking,night_parking,car_park_decks,gantry_height,car_park_basement
                ACB,BLK 270/271 ALBERT CENTRE BASEMENT CAR PARK,30314.7936,31490.4942,BASEMENT CAR PARK,ELECTRONIC PARKING,WHOLE DAY,NO,YES,1,1.8,Y
                """;

        List<CarParkEntity> carParks = CarParkCsvParser.parse(new StringReader(csv));

        CarParkEntity carPark = carParks.get(0);
        assertThat(carPark.getLatitude()).isBetween(SINGAPORE_MIN_LATITUDE, SINGAPORE_MAX_LATITUDE);
        assertThat(carPark.getLongitude()).isBetween(SINGAPORE_MIN_LONGITUDE, SINGAPORE_MAX_LONGITUDE);
    }

    @Test
    void handlesBlankNumericFieldsAsZero() throws IOException {
        String csv = """
                car_park_no,address,x_coord,y_coord,car_park_type,type_of_parking_system,short_term_parking,free_parking,night_parking,car_park_decks,gantry_height,car_park_basement
                AH1,BLK 101 JALAN DUSUN,29257.7203,34500.3599,SURFACE CAR PARK,ELECTRONIC PARKING,WHOLE DAY,SUN & PH FR 7AM-10.30PM,YES,,,N
                """;

        List<CarParkEntity> carParks = CarParkCsvParser.parse(new StringReader(csv));

        CarParkEntity carPark = carParks.get(0);
        assertThat(carPark.getCarParkDecks()).isZero();
        assertThat(carPark.getGantryHeight()).isZero();
    }

    @Test
    void parsesBundledFallbackDatasetEntirelyWithinSingaporesBoundingBox() throws IOException {
        List<CarParkEntity> carParks;
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("data/hdb-carpark-information.csv")) {
            assertThat(inputStream).as("bundled fallback CSV must be present on the classpath").isNotNull();
            carParks = CarParkCsvParser.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }

        assertThat(carParks).isNotEmpty();

        Set<String> uniqueCarParkNumbers = carParks.stream().map(CarParkEntity::getCarParkNo).collect(Collectors.toSet());
        assertThat(uniqueCarParkNumbers).hasSameSizeAs(carParks);

        assertThat(carParks).allSatisfy(carPark -> {
            assertThat(carPark.getLatitude()).isBetween(SINGAPORE_MIN_LATITUDE, SINGAPORE_MAX_LATITUDE);
            assertThat(carPark.getLongitude()).isBetween(SINGAPORE_MIN_LONGITUDE, SINGAPORE_MAX_LONGITUDE);
        });
    }
}
