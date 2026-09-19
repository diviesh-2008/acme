package com.acme.salary.common.error;

/**
 * A requested resource does not exist. Mapped to 404; the message is returned to the client,
 * so it must not contain internal details.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
