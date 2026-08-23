package com.carpark.singapore.exceptions;

/** Thrown when live car park availability could not be fetched after retries/circuit-breaker policies. */
public class AvailabilityFetchException extends RuntimeException {

    public AvailabilityFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
