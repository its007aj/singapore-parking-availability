package com.carpark.singapore.availability;

import com.carpark.singapore.entities.CarParkAvailabilityEntity;
import com.carpark.singapore.exceptions.AvailabilityFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates one availability sync cycle: fetch, then store.
 *
 * <p>If the live fetch fails (after the client's own retry/circuit-breaker policies are
 * exhausted), the last successfully-synced data is left untouched in the database rather
 * than being cleared or overwritten with an empty result — callers reading availability
 * simply see slightly older data until the next successful cycle.
 */
@Service
public class CarParkAvailabilitySyncService {

    private static final Logger log = LoggerFactory.getLogger(CarParkAvailabilitySyncService.class);

    private final CarParkAvailabilityClient client;
    private final CarParkAvailabilityRepository repository;

    public CarParkAvailabilitySyncService(CarParkAvailabilityClient client, CarParkAvailabilityRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    public void sync() {
        try {
            List<CarParkAvailabilityEntity> latest = client.fetchLatestAvailability();
            repository.saveAll(latest);
            log.info("Synced live availability for {} car park/lot-type combinations", latest.size());
        } catch (AvailabilityFetchException e) {
            log.warn("Live availability fetch failed; keeping last known data. Cause: {}", e.getMessage());
        }
    }
}
