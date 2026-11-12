package com.interval.apibackend.security;

import com.interval.apibackend.common.exception.BusinessRuleException;

import java.nio.charset.StandardCharsets;

/**
 * BCrypt hanya memproses 72 BYTE pertama. @Size menghitung karakter, sehingga password
 * berisi karakter non-ASCII bisa lolos @Size tetapi melebihi 72 byte. Cek byte dilakukan di sini.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;
    public static final String LENGTH_MESSAGE = "Password must be between 8 and 72 characters";

    private PasswordPolicy() {
    }

    public static boolean exceedsMaxBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    public static void requireValid(String field, String password) {
        if (password.length() < MIN_LENGTH || exceedsMaxBytes(password)) {
            throw new BusinessRuleException("Validation failed", field, LENGTH_MESSAGE);
        }
    }
}
