package com.acme.salary.common.error;

/**
 * A field value that passed format validation but breaks a business rule that needs
 * data, e.g. an effective date before the hire date. Mapped to 400 with the same
 * {@code errors} shape as Bean Validation failures.
 */
public class FieldValidationException extends RuntimeException {

	private final String field;

	public FieldValidationException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}

}
