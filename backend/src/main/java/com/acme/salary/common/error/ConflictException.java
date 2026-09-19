package com.acme.salary.common.error;

/**
 * The request is valid but conflicts with the current state, e.g. a duplicate salary date.
 * Mapped to 409; the message is returned to the client.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
