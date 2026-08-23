package com.carpark.singapore.availability;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CarParkAvailabilityScheduler {

    private final CarParkAvailabilitySyncService syncService;

    public CarParkAvailabilityScheduler(CarParkAvailabilitySyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
            fixedDelayString = "${parking.availability.poll-interval-ms}",
            initialDelayString = "${parking.availability.initial-delay-ms}")
    public void pollAvailability() {
        syncService.sync();
    }
}
