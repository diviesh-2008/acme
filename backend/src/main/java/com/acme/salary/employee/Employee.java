package com.acme.salary.employee;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An ACME employee. Read-only in v1: rows come from the HR system of record (the dev seed),
 * so Hibernate never issues updates for this entity.
 */
@Entity
@Table(name = "employee")
@Immutable
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String employeeCode;

	@Column(nullable = false, length = 100)
	private String firstName;

	@Column(nullable = false, length = 100)
	private String lastName;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false, length = 100)
	private String jobTitle;

	@Column(nullable = false, length = 100)
	private String department;

	// CHAR(2) in the schema; without this Hibernate validation expects VARCHAR.
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 2)
	private String countryCode;

	// Stored as VARCHAR (see V2 migration); without this Hibernate expects a MySQL ENUM column.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EmploymentStatus employmentStatus;

	@Column(nullable = false)
	private LocalDate hireDate;

	protected Employee() {
	}

	public Employee(String employeeCode, String firstName, String lastName, String email, String jobTitle,
			String department, String countryCode, EmploymentStatus employmentStatus, LocalDate hireDate) {
		this.employeeCode = employeeCode;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.jobTitle = jobTitle;
		this.department = department;
		this.countryCode = countryCode;
		this.employmentStatus = employmentStatus;
		this.hireDate = hireDate;
	}

	public Long getId() {
		return id;
	}

	public String getEmployeeCode() {
		return employeeCode;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getEmail() {
		return email;
	}

	public String getJobTitle() {
		return jobTitle;
	}

	public String getDepartment() {
		return department;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public EmploymentStatus getEmploymentStatus() {
		return employmentStatus;
	}

	public LocalDate getHireDate() {
		return hireDate;
	}

}
