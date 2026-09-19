package com.acme.salary.salary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import com.acme.salary.common.validation.IsoCurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * A new salary record. The id and creation time are always set by the server; unknown JSON
 * fields such as {@code id} are rejected.
 *
 * @param amount annual gross amount, greater than zero, at most 2 decimal places
 * @param currency ISO 4217 code in any letter case, stored upper-case
 * @param effectiveDate first day the salary applies; past, current and future dates are allowed
 */
public record CreateSalaryRequest(
		@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal amount,
		@NotNull @IsoCurrencyCode String currency,
		@NotNull LocalDate effectiveDate) {

	public CreateSalaryRequest {
		currency = (currency == null) ? null : currency.strip().toUpperCase(Locale.ROOT);
	}

}
