package com.acme.salary.salary;

import java.util.List;

import com.acme.salary.salary.dto.CorrectSalaryRequest;
import com.acme.salary.salary.dto.CreateSalaryRequest;
import com.acme.salary.salary.dto.SalaryRecordResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Salary history of one employee. Access requires the HR_MANAGER role (see SecurityConfig).
 * There is no delete: salary history is never removed.
 */
@RestController
@RequestMapping("/api/employees/{employeeId}/salary")
public class SalaryController {

	private final SalaryService salaryService;

	public SalaryController(SalaryService salaryService) {
		this.salaryService = salaryService;
	}

	/** The salary in force today. */
	@GetMapping
	public SalaryRecordResponse currentSalary(@PathVariable long employeeId) {
		return salaryService.currentSalary(employeeId);
	}

	/** All salary records, newest effective date first, including future-dated ones. */
	@GetMapping("/history")
	public List<SalaryRecordResponse> history(@PathVariable long employeeId) {
		return salaryService.history(employeeId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SalaryRecordResponse create(@PathVariable long employeeId, @Valid @RequestBody CreateSalaryRequest request) {
		return salaryService.create(employeeId, request);
	}

	/** Corrects amount and currency; the effective date cannot be changed. */
	@PutMapping("/{salaryId}")
	public SalaryRecordResponse correct(@PathVariable long employeeId, @PathVariable long salaryId,
			@Valid @RequestBody CorrectSalaryRequest request) {
		return salaryService.correct(employeeId, salaryId, request);
	}

}
