package com.interval.apibackend.common.validation;

import java.time.ZoneId;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidTimezoneValidator implements ConstraintValidator<ValidTimezone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // ZoneId.of() juga menerima "+07:00" atau "UTC+7"; kita hanya izinkan nama IANA.
        return value == null || ZoneId.getAvailableZoneIds().contains(value);
    }
}
