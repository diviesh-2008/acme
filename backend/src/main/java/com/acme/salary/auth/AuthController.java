package com.acme.salary.auth;

import com.acme.salary.auth.dto.CurrentUserResponse;
import com.acme.salary.auth.dto.LoginRequest;
import com.acme.salary.auth.dto.LoginResponse;
import com.acme.salary.common.security.SecurityConfig;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	/** The signed-in user, read from the validated access token. */
	@GetMapping("/me")
	public CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
		return new CurrentUserResponse(jwt.getSubject(), jwt.getClaimAsString(SecurityConfig.ROLE_CLAIM));
	}

}
