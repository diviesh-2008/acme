package com.acme.salary.seed;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One generated salary record, keyed by employee code (database ids are not known yet). */
record SeedSalary(String employeeCode, BigDecimal amount, String currency, LocalDate effectiveDate) {
}
