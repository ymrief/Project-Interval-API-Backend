package com.interval.apibackend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** Induk semua exception bisnis yang dipetakan ke response error spec. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> errors;

    protected ApiException(HttpStatus status, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }
}
