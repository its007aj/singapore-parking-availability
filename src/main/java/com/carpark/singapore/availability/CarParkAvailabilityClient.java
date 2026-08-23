package com.carpark.singapore.availability;

import com.carpark.singapore.entities.CarParkAvailabilityEntity;
import com.carpark.singapore.exceptions.AvailabilityFetchException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Fetches live car park availability from data.gov.sg.
 *
 * <p>Wrapped with a bounded retry and circuit breaker (configured under
 * {@code resilience4j.*.instances.carparkAvailability}) that control how hard to try against
 * a slow/flaky/rate-limited endpoint. Once those policies are exhausted, {@link #giveUp}
 * raises {@link AvailabilityFetchException} — the business decision of what to do when live
 * data truly can't be fetched (keep serving last-known-good data) belongs to the caller,
 * not this client.
 */
@Component
public class CarParkAvailabilityClient {

    private final RestClient restClient;
    private final String availabilityUrl;
    private final Clock clock;

    public CarParkAvailabilityClient(
            RestClient.Builder restClientBuilder,
            @Value("${parking.availability.url}") String availabilityUrl,
            @Value("${parking.availability.request-timeout-ms}") long requestTimeoutMs,
            Clock clock) {
        this.availabilityUrl = availabilityUrl;
        this.clock = clock;
        this.restClient = restClientBuilder.requestFactory(timeoutBoundedRequestFactory(requestTimeoutMs)).build();
    }

    private static JdkClientHttpRequestFactory timeoutBoundedRequestFactory(long requestTimeoutMs) {
        Duration timeout = Duration.ofMillis(requestTimeoutMs);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    @Retry(name = "carparkAvailability")
    @CircuitBreaker(name = "carparkAvailability", fallbackMethod = "giveUp")
    public List<CarParkAvailabilityEntity> fetchLatestAvailability() {
        CarparkAvailabilityResponse response = restClient.get().uri(availabilityUrl)
                .retrieve()
                .body(CarparkAvailabilityResponse.class);
        return CarParkAvailabilityMapper.toDomain(response, clock.instant());
    }

    /** Invoked by resilience4j once retries are exhausted or the circuit is open. */
    private List<CarParkAvailabilityEntity> giveUp(Throwable throwable) {
        throw new AvailabilityFetchException("Failed to fetch live car park availability", throwable);
    }
}
