package com.acme.salary.employee;

import java.util.ArrayList;
import java.util.List;

import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates search criteria into a SQL WHERE clause, so filtering happens in MySQL.
 * <p>
 * Case-insensitivity comes from the columns' {@code utf8mb4_0900_ai_ci} collation, so no
 * {@code LOWER()} calls are needed.
 */
final class EmployeeSpecifications {

	// Not a backslash: MySQL treats backslashes in string literals as escapes themselves.
	private static final char LIKE_ESCAPE = '!';

	private EmployeeSpecifications() {
	}

	static Specification<Employee> matching(EmployeeSearchCriteria criteria) {
		List<Specification<Employee>> conditions = new ArrayList<>();
		if (criteria.search() != null) {
			conditions.add(search(criteria.search()));
		}
		if (criteria.country() != null) {
			conditions.add(equalTo("countryCode", criteria.country()));
		}
		if (criteria.department() != null) {
			conditions.add(equalTo("department", criteria.department()));
		}
		if (criteria.status() != null) {
			conditions.add(equalTo("employmentStatus", criteria.status()));
		}
		return Specification.allOf(conditions);
	}

	/** Substring match on employee code, first name, last name, "first last" or email. */
	private static Specification<Employee> search(String term) {
		String pattern = "%" + escapeLikeWildcards(term) + "%";
		return (root, query, cb) -> {
			Expression<String> fullName = cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"));
			return cb.or(
					cb.like(root.get("employeeCode"), pattern, LIKE_ESCAPE),
					cb.like(root.get("firstName"), pattern, LIKE_ESCAPE),
					cb.like(root.get("lastName"), pattern, LIKE_ESCAPE),
					cb.like(fullName, pattern, LIKE_ESCAPE),
					cb.like(root.get("email"), pattern, LIKE_ESCAPE));
		};
	}

	private static Specification<Employee> equalTo(String attribute, Object value) {
		return (root, query, cb) -> cb.equal(root.get(attribute), value);
	}

	// A user typing "%" or "_" means the literal character, not a wildcard.
	static String escapeLikeWildcards(String term) {
		return term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

}
