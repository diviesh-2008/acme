package com.acme.salary.salary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.acme.salary.salary.SalaryRecord;

public record SalaryRecordResponse(Long id, BigDecimal amount, String currency, LocalDate effectiveDate) {

	public static SalaryRecordResponse from(SalaryRecord record) {
		return new SalaryRecordResponse(record.getId(), record.getAmount(), record.getCurrency(),
				record.getEffectiveDate());
	}

}
