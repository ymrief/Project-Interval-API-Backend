package com.interval.apibackend.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Nilai null dianggap valid (gabungkan dengan @NotNull bila wajib). Hanya nama IANA, mis. Asia/Jakarta. */
@Documented
@Constraint(validatedBy = ValidTimezoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimezone {

    String message() default "Timezone is not a valid IANA zone id";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
