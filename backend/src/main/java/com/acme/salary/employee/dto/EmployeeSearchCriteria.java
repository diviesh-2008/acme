package com.acme.salary.employee.dto;

import java.util.Locale;

import com.acme.salary.employee.EmploymentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of {@code GET /api/employees}. Every filter is optional; blank values
 * are ignored. Invalid values are rejected with 400 rather than silently corrected.
 *
 * @param page zero-based page index, default 0
 * @param size page size, default 20, at most 100
 * @param search matched against employee code, name and email
 * @param country ISO 3166-1 alpha-2 code, any letter case
 * @param department exact department name, any letter case
 * @param status employment status
 */
public record EmployeeSearchCriteria(
		@Min(0) Integer page,
		@Min(1) @Max(EmployeeSearchCriteria.MAX_PAGE_SIZE) Integer size,
		@Size(max = 100) String search,
		@Pattern(regexp = "[A-Z]{2}", message = "must be a two-letter ISO 3166-1 country code") String country,
		@Size(max = 100) String department,
		EmploymentStatus status) {

	public static final int DEFAULT_PAGE_SIZE = 20;

	public static final int MAX_PAGE_SIZE = 100;

	public EmployeeSearchCriteria {
		page = (page == null) ? 0 : page;
		size = (size == null) ? DEFAULT_PAGE_SIZE : size;
		search = trimToNull(search);
		country = (trimToNull(country) == null) ? null : country.strip().toUpperCase(Locale.ROOT);
		department = trimToNull(department);
	}

	private static String trimToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
