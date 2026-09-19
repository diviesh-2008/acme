package com.acme.salary.analytics;

import java.util.List;

import com.acme.salary.analytics.dto.AnalyticsOverviewResponse;
import com.acme.salary.analytics.dto.CountrySalaryStatistics;
import com.acme.salary.analytics.dto.DepartmentSalaryStatistics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compensation analytics over current salaries, always per currency. Access requires the
 * HR_MANAGER role (see SecurityConfig). Returns empty results, not 404, when there is no data.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping("/overview")
	public AnalyticsOverviewResponse overview() {
		return analyticsService.overview();
	}

	@GetMapping("/by-country")
	public List<CountrySalaryStatistics> byCountry() {
		return analyticsService.byCountry();
	}

	@GetMapping("/by-department")
	public List<DepartmentSalaryStatistics> byDepartment() {
		return analyticsService.byDepartment();
	}

}
