package com.interval.apibackend.common.exception;

import org.springframework.http.HttpStatus;

/** 429 - terlalu banyak percobaan. GlobalExceptionHandler menambahkan header Retry-After (detik). */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message, null);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
