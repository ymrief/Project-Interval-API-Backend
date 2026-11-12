package com.interval.apibackend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 409 - konflik data, mis. email sudah terdaftar. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message, null);
    }

    public ConflictException(String message, Map<String, String> errors) {
        super(HttpStatus.CONFLICT, message, errors);
    }

    public ConflictException(String message, String field, String error) {
        super(HttpStatus.CONFLICT, message, Map.of(field, error));
    }
}
