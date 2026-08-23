package com.carpark.singapore.exceptions;

/** Thrown when the live static dataset cannot be resolved to a downloadable URL. */
public class StaticDatasetUnavailableException extends RuntimeException {

    public StaticDatasetUnavailableException(String message) {
        super(message);
    }
}
