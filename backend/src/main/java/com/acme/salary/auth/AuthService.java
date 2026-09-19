package com.acme.salary.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.acme.salary.auth.JwtTokenService.IssuedToken;
import com.acme.salary.auth.dto.LoginRequest;
import com.acme.salary.auth.dto.LoginResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	static final String TOKEN_TYPE = "Bearer";

	/** BCrypt cannot hash more than 72 bytes; Spring Security rejects longer input outright. */
	static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final AuthenticationManager authenticationManager;

	private final JwtTokenService tokenService;

	public AuthService(AuthenticationManager authenticationManager, JwtTokenService tokenService) {
		this.authenticationManager = authenticationManager;
		this.tokenService = tokenService;
	}

	/**
	 * Verifies the email and password and issues an access token.
	 *
	 * @throws BadCredentialsException if the email is unknown or the password is wrong
	 */
	public LoginResponse login(LoginRequest request) {
		// No stored password can be this long, so treat it as a wrong password
		// instead of letting BCrypt fail with a server error.
		if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new BadCredentialsException("Bad credentials");
		}

		Authentication authentication = authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

		IssuedToken token = tokenService.issue(authentication);
		long expiresIn = Duration.between(token.issuedAt(), token.expiresAt()).toSeconds();
		return new LoginResponse(token.value(), TOKEN_TYPE, expiresIn, token.expiresAt());
	}

}
