package com.interval.apibackend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 401 - belum login, token tidak valid, atau kredensial salah. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message, null);
    }

    public UnauthorizedException(String message, Map<String, String> errors) {
        super(HttpStatus.UNAUTHORIZED, message, errors);
    }

    public UnauthorizedException(String message, String field, String error) {
        super(HttpStatus.UNAUTHORIZED, message, Map.of(field, error));
    }
}
