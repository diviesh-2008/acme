package com.acme.salary.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * An upper-case ISO 4217 currency code known to {@link java.util.Currency}, e.g. {@code INR}.
 * {@code null} is valid; combine with {@code @NotNull} when the value is required.
 */
@Documented
@Constraint(validatedBy = IsoCurrencyCodeValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoCurrencyCode {

	String message() default "must be a valid ISO 4217 currency code, e.g. USD";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
