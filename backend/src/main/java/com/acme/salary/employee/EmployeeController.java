package com.acme.salary.employee;

import com.acme.salary.common.PageResponse;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only employee API. Access requires the HR_MANAGER role (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

	private final EmployeeService employeeService;

	public EmployeeController(EmployeeService employeeService) {
		this.employeeService = employeeService;
	}

	/** e.g. {@code GET /api/employees?page=0&size=20&search=john&country=US&department=Engineering&status=ACTIVE} */
	@GetMapping
	public PageResponse<EmployeeResponse> search(@Valid EmployeeSearchCriteria criteria) {
		return employeeService.search(criteria);
	}

	@GetMapping("/{id}")
	public EmployeeResponse getById(@PathVariable long id) {
		return employeeService.getById(id);
	}

}
