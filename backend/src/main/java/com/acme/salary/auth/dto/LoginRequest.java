package com.acme.salary.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank String password) {

	// Records print every component by default; keep the password out of logs.
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=****]";
	}

}
