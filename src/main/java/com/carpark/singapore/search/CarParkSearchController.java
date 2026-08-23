package com.carpark.singapore.search;

import com.carpark.singapore.geo.WgsCoordinate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carparks")
public class CarParkSearchController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final double DEFAULT_RADIUS_KM = 3.0;
    private static final double MIN_RADIUS_KM = 0.1;
    private static final double MAX_RADIUS_KM = 50.0;

    private final NearbyCarParkService nearbyCarParkService;

    public CarParkSearchController(NearbyCarParkService nearbyCarParkService) {
        this.nearbyCarParkService = nearbyCarParkService;
    }

    @GetMapping("/nearby")
    public List<NearbyCarPark> findNearby(
            @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
            @RequestParam(defaultValue = "" + DEFAULT_RADIUS_KM)
            @DecimalMin("" + MIN_RADIUS_KM) @DecimalMax("" + MAX_RADIUS_KM) double radiusKm,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit) {
        return nearbyCarParkService.findNearby(new WgsCoordinate(lat, lon), radiusKm, limit);
    }
}
