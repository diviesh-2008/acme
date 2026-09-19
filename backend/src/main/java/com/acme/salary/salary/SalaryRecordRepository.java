package com.acme.salary.salary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query is scoped to one employee and served by the unique
 * {@code (employee_id, effective_date)} index.
 */
public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {

	/** Newest first. */
	List<SalaryRecord> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);

	/** The salary in force on {@code date}: the latest record effective on or before it. */
	Optional<SalaryRecord> findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
			Long employeeId, LocalDate date);

	/** Only returns the record if it belongs to this employee. */
	Optional<SalaryRecord> findByIdAndEmployeeId(Long id, Long employeeId);

	boolean existsByEmployeeIdAndEffectiveDate(Long employeeId, LocalDate effectiveDate);

}
