package com.acme.salary.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IsoCurrencyCodeValidatorTest {

	private final IsoCurrencyCodeValidator validator = new IsoCurrencyCodeValidator();

	@ParameterizedTest
	@ValueSource(strings = { "INR", "USD", "GBP", "EUR", "AUD", "CAD", "SGD", "JPY", "CHF" })
	void acceptsIsoCodes(String code) {
		assertThat(validator.isValid(code, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ZZZ", "US", "RUPEE", "usd", "", " USD" })
	void rejectsUnknownOrMalformedCodes(String code) {
		assertThat(validator.isValid(code, null)).isFalse();
	}

	@ParameterizedTest
	@NullSource
	void leavesNullToNotNull(String code) {
		assertThat(validator.isValid(code, null)).isTrue();
	}

}
