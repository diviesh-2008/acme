package com.acme.salary.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.FieldValidationException;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeNotFoundException;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmploymentStatus;
import com.acme.salary.salary.dto.CorrectSalaryRequest;
import com.acme.salary.salary.dto.CreateSalaryRequest;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salary history for one employee at a time. "Today" comes from the injected {@link Clock}
 * (UTC), so date-dependent rules are testable.
 */
@Service
@Transactional(readOnly = true)
public class SalaryService {

	/** How far ahead a salary change may be scheduled. */
	static final int MAX_YEARS_AHEAD = 1;

	private final SalaryRecordRepository salaryRecordRepository;

	private final EmployeeRepository employeeRepository;

	private final Clock clock;

	public SalaryService(SalaryRecordRepository salaryRecordRepository, EmployeeRepository employeeRepository,
			Clock clock) {
		this.salaryRecordRepository = salaryRecordRepository;
		this.employeeRepository = employeeRepository;
		this.clock = clock;
	}

	/**
	 * The salary in force today: the record with the latest effective date on or before today.
	 * Future-dated records are ignored until their date arrives.
	 *
	 * @throws EmployeeNotFoundException if the employee does not exist
	 * @throws SalaryRecordNotFoundException if no salary is in force yet
	 */
	public SalaryRecordResponse currentSalary(long employeeId) {
		requireEmployee(employeeId);
		LocalDate today = LocalDate.now(clock);
		return salaryRecordRepository
			.findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employeeId, today)
			.map(SalaryRecordResponse::from)
			.orElseThrow(() -> SalaryRecordNotFoundException.noCurrentSalary(employeeId, today));
	}

	/** Every salary record for the employee, newest effective date first; empty if none. */
	public List<SalaryRecordResponse> history(long employeeId) {
		requireEmployee(employeeId);
		return salaryRecordRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId)
			.stream()
			.map(SalaryRecordResponse::from)
			.toList();
	}

	/**
	 * Adds a salary change. Earlier records are never touched, so history is preserved.
	 * <p>
	 * The duplicate-date check gives a clear 409 in the normal case. The unique
	 * {@code (employee_id, effective_date)} constraint remains the final guard: if two
	 * requests race past the check, the second insert fails and is also reported as 409.
	 */
	@Transactional
	public SalaryRecordResponse create(long employeeId, CreateSalaryRequest request) {
		Employee employee = employeeRepository.findById(employeeId)
			.orElseThrow(() -> new EmployeeNotFoundException(employeeId));
		if (employee.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
			throw new ConflictException(
					"Employee " + employeeId + " is terminated; new salary records cannot be added.");
		}
		validateEffectiveDate(request.effectiveDate(), employee.getHireDate());
		if (salaryRecordRepository.existsByEmployeeIdAndEffectiveDate(employeeId, request.effectiveDate())) {
			throw new ConflictException("Employee " + employeeId + " already has a salary record effective on "
					+ request.effectiveDate() + ". Correct that record instead.");
		}

		SalaryRecord record = new SalaryRecord(employeeId, money(request.amount()), request.currency(),
				request.effectiveDate(), clock.instant());
		return SalaryRecordResponse.from(salaryRecordRepository.saveAndFlush(record));
	}

	/**
	 * Corrects the amount and currency of an existing record in place. The effective date,
	 * employee and creation time never change, and no other record is affected. Corrections
	 * are allowed for terminated employees, since they fix historical data.
	 *
	 * @throws SalaryRecordNotFoundException if the record does not exist or belongs to
	 *         another employee
	 */
	@Transactional
	public SalaryRecordResponse correct(long employeeId, long salaryId, CorrectSalaryRequest request) {
		requireEmployee(employeeId);
		SalaryRecord record = salaryRecordRepository.findByIdAndEmployeeId(salaryId, employeeId)
			.orElseThrow(() -> SalaryRecordNotFoundException.forRecord(employeeId, salaryId));
		record.correct(money(request.amount()), request.currency());
		return SalaryRecordResponse.from(salaryRecordRepository.saveAndFlush(record));
	}

	private void requireEmployee(long employeeId) {
		if (!employeeRepository.existsById(employeeId)) {
			throw new EmployeeNotFoundException(employeeId);
		}
	}

	private void validateEffectiveDate(LocalDate effectiveDate, LocalDate hireDate) {
		if (effectiveDate.isBefore(hireDate)) {
			throw new FieldValidationException("effectiveDate",
					"must not be before the employee's hire date (" + hireDate + ")");
		}
		LocalDate latestAllowed = LocalDate.now(clock).plusYears(MAX_YEARS_AHEAD);
		if (effectiveDate.isAfter(latestAllowed)) {
			throw new FieldValidationException("effectiveDate",
					"must be at most " + MAX_YEARS_AHEAD + " year in the future (" + latestAllowed + ")");
		}
	}

	// Stored as DECIMAL(15,2); validation already guarantees at most 2 decimal places.
	private static BigDecimal money(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.UNNECESSARY);
	}

}
