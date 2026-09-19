package com.acme.salary.salary;

import java.time.LocalDate;

import com.acme.salary.common.error.NotFoundException;

/** The employee exists, but the requested salary record does not. */
public class SalaryRecordNotFoundException extends NotFoundException {

	private SalaryRecordNotFoundException(String message) {
		super(message);
	}

	static SalaryRecordNotFoundException forRecord(long employeeId, long salaryId) {
		return new SalaryRecordNotFoundException(
				"Salary record " + salaryId + " was not found for employee " + employeeId + ".");
	}

	static SalaryRecordNotFoundException noCurrentSalary(long employeeId, LocalDate date) {
		return new SalaryRecordNotFoundException(
				"Employee " + employeeId + " has no salary effective on or before " + date + ".");
	}

}
