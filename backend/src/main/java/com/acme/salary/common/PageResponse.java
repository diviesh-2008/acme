package com.acme.salary.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * One page of results. Our own JSON shape, so the API does not depend on how Spring Data
 * serializes {@link Page}.
 *
 * @param page zero-based page index
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
		boolean hasNext, boolean hasPrevious) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.hasNext(), page.hasPrevious());
	}

}
