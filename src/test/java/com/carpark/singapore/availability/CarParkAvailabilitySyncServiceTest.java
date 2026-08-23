package com.carpark.singapore.availability;

import com.carpark.singapore.entities.CarParkAvailabilityEntity;
import com.carpark.singapore.exceptions.AvailabilityFetchException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CarParkAvailabilitySyncServiceTest {

    private final CarParkAvailabilityClient client = mock(CarParkAvailabilityClient.class);
    private final CarParkAvailabilityRepository repository = mock(CarParkAvailabilityRepository.class);
    private final CarParkAvailabilitySyncService syncService =
            new CarParkAvailabilitySyncService(client, repository);

    @Test
    void storesFreshlyFetchedAvailabilityOnSuccess() {
        List<CarParkAvailabilityEntity> fetched = List.of(
                new CarParkAvailabilityEntity("HE12", "C", 105, 31, Instant.now(), Instant.now()));
        given(client.fetchLatestAvailability()).willReturn(fetched);

        syncService.sync();

        verify(repository, times(1)).saveAll(fetched);
    }

    @Test
    void keepsLastKnownDataAndDoesNotThrowWhenLiveFetchFails() {
        given(client.fetchLatestAvailability())
                .willThrow(new AvailabilityFetchException("simulated failure", new RuntimeException("boom")));

        assertThatCode(syncService::sync).doesNotThrowAnyException();

        verify(repository, never()).saveAll(anyList());
    }
}
