package com.acme.salary.seed;

import java.time.LocalDate;

import com.acme.salary.employee.EmploymentStatus;

/** One generated employee row. A record, so two generator runs can be compared with equals(). */
record SeedEmployee(String employeeCode, String firstName, String lastName, String email, String jobTitle,
		String department, String countryCode, EmploymentStatus employmentStatus, LocalDate hireDate) {
}
