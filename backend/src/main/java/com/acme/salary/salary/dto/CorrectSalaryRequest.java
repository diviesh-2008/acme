package com.acme.salary.salary.dto;

import java.math.BigDecimal;
import java.util.Locale;

import com.acme.salary.common.validation.IsoCurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * A correction to an existing salary record. There is deliberately no effective date: it
 * identifies the record and cannot change, so a request that sends one is rejected.
 */
public record CorrectSalaryRequest(
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal amount,
		@NotNull @IsoCurrencyCode String currency) {

	public CorrectSalaryRequest {
		currency = (currency == null) ? null : currency.strip().toUpperCase(Locale.ROOT);
	}

}
