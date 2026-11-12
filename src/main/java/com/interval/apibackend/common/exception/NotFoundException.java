package com.interval.apibackend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 404 - data tidak ada ATAU milik pengguna lain (jangan bocorkan keberadaannya). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message, null);
    }

    public NotFoundException(String message, Map<String, String> errors) {
        super(HttpStatus.NOT_FOUND, message, errors);
    }

    public NotFoundException(String message, String field, String error) {
        super(HttpStatus.NOT_FOUND, message, Map.of(field, error));
    }
}
