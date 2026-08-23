package com.carpark.singapore.availability;

import com.carpark.singapore.entities.CarParkAvailabilityEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps the raw availability API response to {@link CarParkAvailabilityEntity} rows.
 *
 * <p>The API's per-record {@code update_datetime} (e.g. "2026-08-23T13:52:29") carries no
 * timezone offset, unlike the response's top-level {@code timestamp} (which has "+08:00")
 * — it is documented as local Singapore time, so it is interpreted against
 * {@link #SINGAPORE_ZONE} rather than UTC or the JVM's default zone.
 *
 * <p>A single malformed lot-type entry (unparseable numbers or timestamp) is skipped with a
 * warning rather than failing the whole fetch, so one bad record from an upstream data
 * quality issue doesn't discard availability for every other car park in the same response.
 */
final class CarParkAvailabilityMapper {

    private static final Logger log = LoggerFactory.getLogger(CarParkAvailabilityMapper.class);
    private static final ZoneId SINGAPORE_ZONE = ZoneId.of("Asia/Singapore");

    private CarParkAvailabilityMapper() {
    }

    static List<CarParkAvailabilityEntity> toDomain(CarparkAvailabilityResponse response, Instant fetchedAt) {
        List<CarParkAvailabilityEntity> results = new ArrayList<>();
        if (response == null || response.items() == null) {
            return results;
        }
        for (CarparkAvailabilityResponse.Item item : response.items()) {
            results.addAll(toDomain(item, fetchedAt));
        }
        return results;
    }

    private static List<CarParkAvailabilityEntity> toDomain(CarparkAvailabilityResponse.Item item, Instant fetchedAt) {
        List<CarParkAvailabilityEntity> results = new ArrayList<>();
        if (item.carparkData() == null) {
            return results;
        }
        for (CarparkAvailabilityResponse.CarparkData carparkData : item.carparkData()) {
            results.addAll(toDomain(carparkData, fetchedAt));
        }
        return results;
    }

    private static List<CarParkAvailabilityEntity> toDomain(CarparkAvailabilityResponse.CarparkData carparkData,
            Instant fetchedAt) {
        List<CarParkAvailabilityEntity> results = new ArrayList<>();
        if (carparkData.carparkInfo() == null) {
            return results;
        }
        for (CarparkAvailabilityResponse.CarparkInfo lotInfo : carparkData.carparkInfo()) {
            toDomain(carparkData, lotInfo, fetchedAt).ifPresent(results::add);
        }
        return results;
    }

    private static Optional<CarParkAvailabilityEntity> toDomain(
            CarparkAvailabilityResponse.CarparkData carparkData,
            CarparkAvailabilityResponse.CarparkInfo lotInfo,
            Instant fetchedAt) {
        try {
            return Optional.of(new CarParkAvailabilityEntity(
                    carparkData.carparkNumber(),
                    lotInfo.lotType(),
                    Integer.parseInt(lotInfo.totalLots()),
                    Integer.parseInt(lotInfo.lotsAvailable()),
                    parseSingaporeLocalDateTime(carparkData.updateDatetime()),
                    fetchedAt));
        } catch (RuntimeException e) {
            log.warn("Skipping malformed availability record for car park {}: {}",
                    carparkData.carparkNumber(), e.getMessage());
            return Optional.empty();
        }
    }

    private static Instant parseSingaporeLocalDateTime(String updateDatetime) {
        return LocalDateTime.parse(updateDatetime).atZone(SINGAPORE_ZONE).toInstant();
    }
}
