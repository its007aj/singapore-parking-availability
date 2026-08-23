package com.carpark.singapore.availability;

import com.carpark.singapore.entities.CarParkAvailabilityEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarParkAvailabilityMapperTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-08-23T06:00:00Z");

    @Test
    void interpretsUpdateDatetimeAsSingaporeLocalTimeNotUtc() {
        CarparkAvailabilityResponse response = responseOf(carpark("HE12", info("C", "105", "31"), "2026-08-23T13:52:29"));

        List<CarParkAvailabilityEntity> result = CarParkAvailabilityMapper.toDomain(response, FETCHED_AT);

        assertThat(result).hasSize(1);
        // 13:52:29 Singapore time (UTC+8, fixed, no DST) is 05:52:29 UTC.
        assertThat(result.get(0).getLotUpdatedAt()).isEqualTo(Instant.parse("2026-08-23T05:52:29Z"));
        assertThat(result.get(0).getFetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void mapsEachLotTypeUnderACarparkToASeparateRecord() {
        CarparkAvailabilityResponse response = responseOf(new CarparkAvailabilityResponse.CarparkData(
                "BP1",
                List.of(info("C", "577", "326"), info("H", "10", "2")),
                "2026-08-23T13:52:11"));

        List<CarParkAvailabilityEntity> result = CarParkAvailabilityMapper.toDomain(response, FETCHED_AT);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CarParkAvailabilityEntity::getLotType).containsExactlyInAnyOrder("C", "H");
        assertThat(result).allSatisfy(r -> assertThat(r.getCarParkNo()).isEqualTo("BP1"));
    }

    @Test
    void skipsAMalformedLotEntryButKeepsOthersInTheSameResponse() {
        CarparkAvailabilityResponse response = new CarparkAvailabilityResponse(List.of(
                new CarparkAvailabilityResponse.Item("2026-08-23T13:52:37+08:00", List.of(
                        carpark("BAD1", info("C", "not-a-number", "31"), "2026-08-23T13:52:29"),
                        carpark("GOOD1", info("C", "105", "31"), "2026-08-23T13:52:29")))));

        List<CarParkAvailabilityEntity> result = CarParkAvailabilityMapper.toDomain(response, FETCHED_AT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarParkNo()).isEqualTo("GOOD1");
    }

    @Test
    void returnsEmptyListForNullOrEmptyResponse() {
        assertThat(CarParkAvailabilityMapper.toDomain(null, FETCHED_AT)).isEmpty();
        assertThat(CarParkAvailabilityMapper.toDomain(new CarparkAvailabilityResponse(List.of()), FETCHED_AT)).isEmpty();
    }

    private static CarparkAvailabilityResponse responseOf(CarparkAvailabilityResponse.CarparkData carparkData) {
        return new CarparkAvailabilityResponse(
                List.of(new CarparkAvailabilityResponse.Item("2026-08-23T13:52:37+08:00", List.of(carparkData))));
    }

    private static CarparkAvailabilityResponse.CarparkData carpark(
            String carParkNo, CarparkAvailabilityResponse.CarparkInfo info, String updateDatetime) {
        return new CarparkAvailabilityResponse.CarparkData(carParkNo, List.of(info), updateDatetime);
    }

    private static CarparkAvailabilityResponse.CarparkInfo info(String lotType, String totalLots, String lotsAvailable) {
        return new CarparkAvailabilityResponse.CarparkInfo(lotType, totalLots, lotsAvailable);
    }
}
