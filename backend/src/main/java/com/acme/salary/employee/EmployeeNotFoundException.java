package com.acme.salary.employee;

import com.acme.salary.common.error.NotFoundException;

public class EmployeeNotFoundException extends NotFoundException {

	public EmployeeNotFoundException(long employeeId) {
		super("Employee " + employeeId + " was not found.");
	}

}
