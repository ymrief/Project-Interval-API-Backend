package com.interval.apibackend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 400 - request tidak dapat diproses (bukan kegagalan validasi field). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message, null);
    }

    public BadRequestException(String message, Map<String, String> errors) {
        super(HttpStatus.BAD_REQUEST, message, errors);
    }

    public BadRequestException(String message, String field, String error) {
        super(HttpStatus.BAD_REQUEST, message, Map.of(field, error));
    }
}
