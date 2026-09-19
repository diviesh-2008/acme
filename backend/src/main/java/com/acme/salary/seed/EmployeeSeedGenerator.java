package com.acme.salary.seed;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.acme.salary.employee.EmploymentStatus;

/**
 * Generates the same 10,000 demo employees on every run.
 * <p>
 * {@link Random}'s algorithm is fixed by the Java SE specification, so a fixed seed gives an
 * identical sequence on every JVM. Dates come from fixed constants, never from "today".
 * Changing any list or weight below changes the generated data.
 */
final class EmployeeSeedGenerator {

	static final int EMPLOYEE_COUNT = 10_000;

	static final long RANDOM_SEED = 20_260_101L;

	/** Hire dates fall in [EARLIEST_HIRE_DATE, REFERENCE_DATE). */
	static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 1, 1);

	static final LocalDate EARLIEST_HIRE_DATE = LocalDate.of(2010, 1, 1);

	static final String EMAIL_DOMAIN = "acme.example";

	private static final List<String> FIRST_NAMES = List.of("James", "Olivia", "Liam", "Emma", "Noah", "Ava",
			"Lucas", "Sophia", "Ethan", "Mia", "Oliver", "Amelia", "Aarav", "Priya", "Rahul", "Ananya", "Arjun",
			"Diya", "Lukas", "Anna", "Felix", "Lena", "Jonas", "Clara", "Hugo", "Chloe", "Louis", "Camille", "Wei",
			"Mei", "Jun", "Li", "Hiroshi", "Yuki", "Kenji", "Sakura", "Mateo", "Sofia", "Diego", "Valentina",
			"Daniel", "Grace", "John", "Hannah", "Jack", "Isla", "Ryan", "Zoe", "Omar", "Leila");

	private static final List<String> LAST_NAMES = List.of("Smith", "Johnson", "Williams", "Brown", "Jones",
			"Garcia", "Miller", "Davis", "Wilson", "Taylor", "Anderson", "Thomas", "Moore", "Martin", "Lee", "Clark",
			"Walker", "Hall", "Young", "King", "Patel", "Sharma", "Singh", "Gupta", "Kumar", "Reddy", "Iyer",
			"Mueller", "Schmidt", "Schneider", "Fischer", "Weber", "Wagner", "Becker", "Dubois", "Moreau", "Laurent",
			"Lefebvre", "Tan", "Lim", "Ng", "Wong", "Chen", "Nguyen", "Tanaka", "Sato", "Rossi", "Silva", "Murphy",
			"Kelly");

	// Weights are percentages of the workforce.
	private static final List<Weighted<String>> COUNTRIES = List.of(new Weighted<>("US", 30),
			new Weighted<>("IN", 16), new Weighted<>("GB", 14), new Weighted<>("DE", 10), new Weighted<>("AU", 10),
			new Weighted<>("CA", 8), new Weighted<>("FR", 7), new Weighted<>("SG", 5));

	private static final List<Weighted<String>> DEPARTMENTS = List.of(new Weighted<>("Engineering", 35),
			new Weighted<>("Sales", 15), new Weighted<>("Customer Support", 12), new Weighted<>("Product", 8),
			new Weighted<>("Marketing", 7), new Weighted<>("Operations", 7), new Weighted<>("Finance", 6),
			new Weighted<>("Human Resources", 5), new Weighted<>("Legal", 5));

	private static final Map<String, List<String>> JOB_TITLES = Map.of(
			"Engineering", List.of("Software Engineer", "Senior Software Engineer", "Staff Engineer", "QA Engineer",
					"Engineering Manager"),
			"Sales", List.of("Sales Development Representative", "Account Executive", "Sales Manager"),
			"Customer Support", List.of("Support Specialist", "Senior Support Specialist", "Support Team Lead"),
			"Product", List.of("Product Manager", "Senior Product Manager", "Product Designer"),
			"Marketing", List.of("Marketing Specialist", "Content Strategist", "Marketing Manager"),
			"Operations", List.of("Operations Analyst", "Office Manager", "Operations Manager"),
			"Finance", List.of("Accountant", "Financial Analyst", "Finance Manager"),
			"Human Resources", List.of("Recruiter", "HR Generalist", "HR Business Partner"),
			"Legal", List.of("Paralegal", "Legal Counsel", "Senior Legal Counsel"));

	private static final List<Weighted<EmploymentStatus>> STATUSES = List.of(
			new Weighted<>(EmploymentStatus.ACTIVE, 90), new Weighted<>(EmploymentStatus.ON_LEAVE, 4),
			new Weighted<>(EmploymentStatus.TERMINATED, 6));

	List<SeedEmployee> generate() {
		Random random = new Random(RANDOM_SEED);
		int hireDateRange = (int) ChronoUnit.DAYS.between(EARLIEST_HIRE_DATE, REFERENCE_DATE);
		List<SeedEmployee> employees = new ArrayList<>(EMPLOYEE_COUNT);

		// Every employee consumes the same draws in the same order; do not reorder them.
		for (int number = 1; number <= EMPLOYEE_COUNT; number++) {
			String firstName = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()));
			String lastName = LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
			String country = pick(random, COUNTRIES);
			String department = pick(random, DEPARTMENTS);
			List<String> titles = JOB_TITLES.get(department);
			String jobTitle = titles.get(random.nextInt(titles.size()));
			EmploymentStatus status = pick(random, STATUSES);
			LocalDate hireDate = EARLIEST_HIRE_DATE.plusDays(random.nextInt(hireDateRange));

			employees.add(new SeedEmployee("EMP-%05d".formatted(number), firstName, lastName,
					email(firstName, lastName, number), jobTitle, department, country, status, hireDate));
		}
		return employees;
	}

	// The employee number makes every address unique, e.g. olivia.smith42@acme.example.
	private static String email(String firstName, String lastName, int number) {
		return (firstName + "." + lastName).toLowerCase(Locale.ROOT) + number + "@" + EMAIL_DOMAIN;
	}

	private static <T> T pick(Random random, List<Weighted<T>> options) {
		int total = options.stream().mapToInt(Weighted::weight).sum();
		int roll = random.nextInt(total);
		for (Weighted<T> option : options) {
			roll -= option.weight();
			if (roll < 0) {
				return option.value();
			}
		}
		throw new IllegalStateException("unreachable");
	}

	private record Weighted<T>(T value, int weight) {
	}

}
