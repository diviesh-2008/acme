package com.acme.salary.salary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One salary change for an employee, effective from {@code effectiveDate}.
 * <p>
 * The employee, effective date and creation time never change after insert (Hibernate
 * will not write them on update). Only {@link #correct} may change amount and currency.
 * The employee is held as an id, not a JPA association, so nothing is lazily loaded.
 */
@Entity
@Table(name = "salary_record")
public class SalaryRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long employeeId;

	@Column(nullable = false, precision = 15, scale = 2)
	private BigDecimal amount;

	// CHAR(3) in the schema; without this Hibernate validation expects VARCHAR.
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private String currency;

	@Column(nullable = false, updatable = false)
	private LocalDate effectiveDate;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected SalaryRecord() {
	}

	public SalaryRecord(Long employeeId, BigDecimal amount, String currency, LocalDate effectiveDate,
			Instant createdAt) {
		this.employeeId = employeeId;
		this.amount = amount;
		this.currency = currency;
		this.effectiveDate = effectiveDate;
		this.createdAt = createdAt;
	}

	/** Fixes a mistaken amount or currency in place; the record keeps its effective date. */
	public void correct(BigDecimal amount, String currency) {
		this.amount = amount;
		this.currency = currency;
	}

	public Long getId() {
		return id;
	}

	public Long getEmployeeId() {
		return employeeId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public LocalDate getEffectiveDate() {
		return effectiveDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
