package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.acme.salary.common.PageResponse;
import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.employee.dto.EmployeeResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Service behaviour with the repository mocked. Whether filters match the right rows is
 * checked against MySQL in EmployeeIT, and how they become SQL in EmployeeSpecificationsTest.
 */
class EmployeeServiceTest {

	private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);

	private final EmployeeService employeeService = new EmployeeService(employeeRepository);

	@Test
	void getByIdReturnsTheEmployee() {
		when(employeeRepository.findById(42L)).thenReturn(Optional.of(employee("EMP-00042", "Ada", "Lovelace")));

		EmployeeResponse response = employeeService.getById(42);

		assertThat(response.employeeCode()).isEqualTo("EMP-00042");
		assertThat(response.firstName()).isEqualTo("Ada");
		assertThat(response.hireDate()).isEqualTo(LocalDate.of(2022, 4, 15));
	}

	@Test
	void getByIdThrowsNotFoundForMissingEmployee() {
		when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> employeeService.getById(99)).isInstanceOf(NotFoundException.class)
			.hasMessage("Employee 99 was not found.");
	}

	@Test
	void searchPagesInTheDatabaseWithDeterministicOrder() {
		stubFindAll(List.of(), 0);

		employeeService.search(criteria(3, 25));

		Pageable pageable = capturedPageable();
		assertThat(pageable.getPageNumber()).isEqualTo(3);
		assertThat(pageable.getPageSize()).isEqualTo(25);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("lastName"), Sort.Order.asc("firstName"),
				Sort.Order.asc("id")));
	}

	@Test
	void searchReturnsPageContentAndMetadata() {
		stubFindAll(List.of(employee("EMP-00001", "Ada", "Lovelace"), employee("EMP-00002", "Alan", "Turing")), 45);

		PageResponse<EmployeeResponse> response = employeeService.search(criteria(1, 20));

		assertThat(response.content()).extracting(EmployeeResponse::employeeCode)
			.containsExactly("EMP-00001", "EMP-00002");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(20);
		assertThat(response.totalElements()).isEqualTo(45);
		assertThat(response.totalPages()).isEqualTo(3);
		assertThat(response.hasNext()).isTrue();
		assertThat(response.hasPrevious()).isTrue();
	}

	@Test
	void firstAndLastPagesReportNoPreviousOrNext() {
		stubFindAll(List.of(employee("EMP-00001", "Ada", "Lovelace")), 1);

		PageResponse<EmployeeResponse> response = employeeService.search(criteria(0, 20));

		assertThat(response.hasPrevious()).isFalse();
		assertThat(response.hasNext()).isFalse();
		assertThat(response.totalPages()).isEqualTo(1);
	}

	private void stubFindAll(List<Employee> content, long total) {
		when(employeeRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenAnswer(invocation -> new PageImpl<>(content, invocation.getArgument(1, Pageable.class), total));
	}

	@SuppressWarnings("unchecked")
	private Pageable capturedPageable() {
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(employeeRepository).findAll(any(Specification.class), captor.capture());
		return captor.getValue();
	}

	private static EmployeeSearchCriteria criteria(int page, int size) {
		return new EmployeeSearchCriteria(page, size, null, null, null, null);
	}

	private static Employee employee(String code, String firstName, String lastName) {
		return new Employee(code, firstName, lastName, firstName.toLowerCase() + "@acme.example", "Engineer",
				"Engineering", "GB", EmploymentStatus.ACTIVE, LocalDate.of(2022, 4, 15));
	}

}
