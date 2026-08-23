package com.carpark.singapore.availability;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Raw response shape of data.gov.sg's live car park availability API. */
record CarparkAvailabilityResponse(List<Item> items) {

    record Item(
            String timestamp,
            @JsonProperty("carpark_data") List<CarparkData> carparkData) {
    }

    record CarparkData(
            @JsonProperty("carpark_number") String carparkNumber,
            @JsonProperty("carpark_info") List<CarparkInfo> carparkInfo,
            @JsonProperty("update_datetime") String updateDatetime) {
    }

    record CarparkInfo(
            @JsonProperty("lot_type") String lotType,
            @JsonProperty("total_lots") String totalLots,
            @JsonProperty("lots_available") String lotsAvailable) {
    }
}
