package com.carpark.singapore.carpark;

import com.carpark.singapore.exceptions.StaticDatasetUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches the latest HDB car park information CSV from data.gov.sg.
 *
 * <p>The dataset's poll-download endpoint can in principle return a PENDING status while
 * an export job runs. {@code fetchLatestCsv} is wrapped with a bounded retry (transient
 * timeouts/PENDING responses) and a circuit breaker (stop hammering data.gov.sg once it's
 * clearly down), both configured under {@code resilience4j.*.instances.carparkStaticData}.
 * If every attempt fails, {@link #fallbackToEmpty} reports an empty result so the caller
 * falls back to the bundled dataset snapshot instead of failing application startup.
 */
@Component
public class CarParkStaticDataClient {

    private static final Logger log = LoggerFactory.getLogger(CarParkStaticDataClient.class);

    private final RestClient restClient;
    private final String pollDownloadBaseUrl;
    private final String datasetId;

    public CarParkStaticDataClient(
            RestClient.Builder restClientBuilder,
            @Value("${parking.static-data.poll-download-base-url}") String pollDownloadBaseUrl,
            @Value("${parking.static-data.dataset-id}") String datasetId,
            @Value("${parking.static-data.request-timeout-ms}") long requestTimeoutMs) {
        this.pollDownloadBaseUrl = pollDownloadBaseUrl;
        this.datasetId = datasetId;
        this.restClient = restClientBuilder.requestFactory(timeoutBoundedRequestFactory(requestTimeoutMs)).build();
    }

    private static JdkClientHttpRequestFactory timeoutBoundedRequestFactory(long requestTimeoutMs) {
        Duration timeout = Duration.ofMillis(requestTimeoutMs);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    /** Returns the latest dataset CSV content, or empty if it could not be fetched after retries. */
    @Retry(name = "carparkStaticData")
    @CircuitBreaker(name = "carparkStaticData", fallbackMethod = "fallbackToEmpty")
    public Optional<String> fetchLatestCsv() {
        String downloadUrl = resolveDownloadUrl();
        // Pass a pre-built URI rather than a template string: the download URL is an AWS
        // pre-signed S3 URL whose query string is already percent-encoded, and RestClient's
        // string-template parsing would otherwise re-encode it and invalidate the signature.
        URI uri = URI.create(downloadUrl);
        return Optional.ofNullable(restClient.get().uri(uri).retrieve().body(String.class));
    }

    /** Invoked by resilience4j once retries are exhausted or the circuit is open. */
    private Optional<String> fallbackToEmpty(Throwable throwable) {
        log.warn("Failed to fetch live car park static dataset, falling back to bundled snapshot: {}",
                throwable.getMessage());
        return Optional.empty();
    }

    private String resolveDownloadUrl() {
        String pollUrl = pollDownloadBaseUrl + "/" + datasetId + "/poll-download";
        PollDownloadResponse response = restClient.get().uri(pollUrl).retrieve().body(PollDownloadResponse.class);
        if (response == null || !response.isDownloadReady()) {
            throw new StaticDatasetUnavailableException("Static dataset download was not ready: " + describe(response));
        }
        return response.data().url();
    }

    private static String describe(PollDownloadResponse response) {
        return response == null ? "no response" : String.valueOf(response.data());
    }
}
