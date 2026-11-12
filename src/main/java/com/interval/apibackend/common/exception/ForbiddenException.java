package com.interval.apibackend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 403 - dipakai HANYA untuk akun nonaktif. Data milik pengguna lain memakai NotFoundException. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message, null);
    }

    public ForbiddenException(String message, Map<String, String> errors) {
        super(HttpStatus.FORBIDDEN, message, errors);
    }

    public ForbiddenException(String message, String field, String error) {
        super(HttpStatus.FORBIDDEN, message, Map.of(field, error));
    }
}
