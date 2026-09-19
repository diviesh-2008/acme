package com.acme.salary.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads HR Manager accounts for Spring Security's password authentication.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository appUserRepository;

	public AppUserDetailsService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return appUserRepository.findByEmail(email)
			.map(user -> User.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.roles(user.getRole().name())
				.build())
			// Spring Security reports this as bad credentials, so callers can't tell
			// an unknown email from a wrong password.
			.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

}
