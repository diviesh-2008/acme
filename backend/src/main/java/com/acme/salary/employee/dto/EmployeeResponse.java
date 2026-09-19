package com.acme.salary.employee.dto;

import java.time.LocalDate;

import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmploymentStatus;

public record EmployeeResponse(Long id, String employeeCode, String firstName, String lastName, String email,
		String jobTitle, String department, String countryCode, EmploymentStatus employmentStatus,
		LocalDate hireDate) {

	public static EmployeeResponse from(Employee employee) {
		return new EmployeeResponse(employee.getId(), employee.getEmployeeCode(), employee.getFirstName(),
				employee.getLastName(), employee.getEmail(), employee.getJobTitle(), employee.getDepartment(),
				employee.getCountryCode(), employee.getEmploymentStatus(), employee.getHireDate());
	}

}
