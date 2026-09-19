package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks which SQL conditions each filter produces, without a database. Whether those
 * conditions match the right rows in MySQL is covered by EmployeeIT.
 */
class EmployeeSpecificationsTest {

	@SuppressWarnings("unchecked")
	private final Root<Employee> root = mock(Root.class);

	private final CriteriaQuery<?> query = mock(CriteriaQuery.class);

	private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

	@SuppressWarnings("unchecked")
	private final Expression<String> fullName = mock(Expression.class, "fullName");

	@BeforeEach
	@SuppressWarnings("unchecked")
	void stubCriteriaApi() {
		for (String attribute : new String[] { "employeeCode", "firstName", "lastName", "email", "countryCode",
				"department", "employmentStatus" }) {
			Path<Object> path = mock(Path.class, attribute);
			when(root.get(attribute)).thenReturn(path);
		}
		when(cb.concat(any(Expression.class), anyString())).thenReturn(mock(Expression.class));
		when(cb.concat(any(Expression.class), any(Expression.class))).thenReturn(fullName);
		when(cb.equal(any(), any(Object.class))).thenReturn(mock(Predicate.class));
		when(cb.like(any(), anyString(), anyChar())).thenReturn(mock(Predicate.class));
		when(cb.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));
		when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(mock(Predicate.class));
	}

	@Test
	void noFiltersAddNoConditions() {
		Predicate predicate = toPredicate(criteria(null, null, null, null));

		assertThat(predicate).isNull();
		verifyNoInteractions(cb);
	}

	@Test
	void searchMatchesCodeNamesFullNameAndEmail() {
		toPredicate(criteria("john", null, null, null));

		verify(cb).like(root.get("employeeCode"), "%john%", '!');
		verify(cb).like(root.get("firstName"), "%john%", '!');
		verify(cb).like(root.get("lastName"), "%john%", '!');
		verify(cb).like(fullName, "%john%", '!');
		verify(cb).like(root.get("email"), "%john%", '!');
		verify(cb, times(5)).like(any(), anyString(), anyChar());
		verify(cb, never()).equal(any(), any(Object.class));
	}

	@Test
	void searchTreatsWildcardCharactersLiterally() {
		assertThat(EmployeeSpecifications.escapeLikeWildcards("50%_off!")).isEqualTo("50!%!_off!!");
	}

	@Test
	void countryFilterMatchesCountryCodeExactly() {
		toPredicate(criteria(null, "us", null, null));

		verify(cb).equal(root.get("countryCode"), "US");
		verify(cb, never()).like(any(), anyString(), anyChar());
	}

	@Test
	void departmentFilterMatchesDepartmentExactly() {
		toPredicate(criteria(null, null, " Engineering ", null));

		verify(cb).equal(root.get("department"), "Engineering");
	}

	@Test
	void statusFilterMatchesEmploymentStatus() {
		toPredicate(criteria(null, null, null, EmploymentStatus.ON_LEAVE));

		verify(cb).equal(root.get("employmentStatus"), EmploymentStatus.ON_LEAVE);
	}

	@Test
	void combinedFiltersAreAllRequired() {
		toPredicate(criteria("john", "US", "Engineering", EmploymentStatus.ACTIVE));

		verify(cb).equal(root.get("countryCode"), "US");
		verify(cb).equal(root.get("department"), "Engineering");
		verify(cb).equal(root.get("employmentStatus"), EmploymentStatus.ACTIVE);
		verify(cb, times(5)).like(any(), anyString(), anyChar());
		// Four conditions joined by three ANDs; OR only inside the search condition.
		verify(cb, times(3)).and(any(Predicate.class), any(Predicate.class));
		verify(cb, times(1)).or(any(Predicate[].class));
	}

	@Test
	void blankFiltersAreIgnored() {
		Predicate predicate = toPredicate(criteria("  ", " ", "", null));

		assertThat(predicate).isNull();
	}

	private Predicate toPredicate(EmployeeSearchCriteria criteria) {
		return EmployeeSpecifications.matching(criteria).toPredicate(root, query, cb);
	}

	private static EmployeeSearchCriteria criteria(String search, String country, String department,
			EmploymentStatus status) {
		return new EmployeeSearchCriteria(0, 20, search, country, department, status);
	}

}
