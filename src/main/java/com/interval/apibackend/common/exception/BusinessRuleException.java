package com.interval.apibackend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 422 - validasi field atau aturan bisnis gagal. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message, null);
    }

    public BusinessRuleException(String message, Map<String, String> errors) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message, errors);
    }

    public BusinessRuleException(String message, String field, String error) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message, Map.of(field, error));
    }
}
