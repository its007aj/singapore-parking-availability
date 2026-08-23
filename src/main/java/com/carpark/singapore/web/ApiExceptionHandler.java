package com.carpark.singapore.web;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

/**
 * Turns request-parameter validation failures into a readable, field-specific message.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} is required: Spring Boot's built-in
 * {@code ProblemDetailsExceptionHandler} (enabled via {@code spring.mvc.problemdetails.enabled})
 * is also a {@code @ControllerAdvice} handling this same exception type with a generic
 * "Validation failure" message, and without explicit ordering it wins over this one.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException exception) {
        String detail = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
