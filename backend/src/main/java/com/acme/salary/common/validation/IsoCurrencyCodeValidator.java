package com.acme.salary.common.validation;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsoCurrencyCodeValidator implements ConstraintValidator<IsoCurrencyCode, String> {

	// The JDK's ISO 4217 table: every active currency, no network lookup.
	private static final Set<String> CODES = Currency.getAvailableCurrencies()
		.stream()
		.map(Currency::getCurrencyCode)
		.collect(Collectors.toUnmodifiableSet());

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || CODES.contains(value);
	}

}
