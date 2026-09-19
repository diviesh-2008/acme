package com.acme.salary.employee;

import com.acme.salary.common.PageResponse;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

	/** Fixed order; id breaks ties between equal names so pages never overlap or skip rows. */
	static final Sort DEFAULT_SORT = Sort.by("lastName", "firstName", "id");

	private final EmployeeRepository employeeRepository;

	public EmployeeService(EmployeeRepository employeeRepository) {
		this.employeeRepository = employeeRepository;
	}

	/** One page of matching employees; filtering, ordering and paging all run in the database. */
	public PageResponse<EmployeeResponse> search(EmployeeSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(), DEFAULT_SORT);
		Page<Employee> page = employeeRepository.findAll(EmployeeSpecifications.matching(criteria), pageRequest);
		return PageResponse.from(page.map(EmployeeResponse::from));
	}

	/**
	 * @throws EmployeeNotFoundException if no employee has this id
	 */
	public EmployeeResponse getById(long id) {
		return employeeRepository.findById(id)
			.map(EmployeeResponse::from)
			.orElseThrow(() -> new EmployeeNotFoundException(id));
	}

}
