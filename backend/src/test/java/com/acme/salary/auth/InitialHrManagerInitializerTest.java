package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialHrManagerInitializerTest {

	private static final String EMAIL = "hr.manager@acme.example";

	private static final String PASSWORD = "initial-password-123";

	private final AppUserRepository appUserRepository = mock(AppUserRepository.class);

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	@Test
	void createsHrManagerWhenAbsent() {
		when(appUserRepository.existsByEmail(EMAIL)).thenReturn(false);

		initializer(" HR.Manager@ACME.example ", PASSWORD).createIfAbsent();

		AppUser saved = savedUser();
		assertThat(saved.getEmail()).isEqualTo(EMAIL);
		assertThat(saved.getRole()).isEqualTo(Role.HR_MANAGER);
	}

	@Test
	void storesBcryptHashNotThePlainPassword() {
		when(appUserRepository.existsByEmail(EMAIL)).thenReturn(false);

		initializer(EMAIL, PASSWORD).createIfAbsent();

		String hash = savedUser().getPasswordHash();
		assertThat(hash).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
		assertThat(hash).matches("^\\$2[aby]\\$\\d{2}\\$[./0-9A-Za-z]{53}$");
		assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
	}

	@Test
	void doesNotRecreateAnExistingAccount() {
		when(appUserRepository.existsByEmail(EMAIL)).thenReturn(true);

		initializer(EMAIL, PASSWORD).createIfAbsent();

		verify(appUserRepository, never()).save(any());
	}

	@Test
	void runningOnEveryStartupCreatesTheAccountOnlyOnce() {
		when(appUserRepository.existsByEmail(EMAIL)).thenReturn(false, true);
		InitialHrManagerInitializer initializer = initializer(EMAIL, PASSWORD);

		initializer.createIfAbsent();
		initializer.createIfAbsent();

		verify(appUserRepository, times(1)).save(any());
	}

	@Test
	void doesNothingWhenNotConfigured() {
		initializer(null, null).createIfAbsent();
		initializer("", "  ").createIfAbsent();

		verifyNoInteractions(appUserRepository);
	}

	@Test
	void failsWhenOnlyOneValueIsSet() {
		assertThatIllegalStateException().isThrownBy(() -> initializer(EMAIL, null).createIfAbsent());
		assertThatIllegalStateException().isThrownBy(() -> initializer(null, PASSWORD).createIfAbsent());
		verifyNoInteractions(appUserRepository);
	}

	@Test
	void rejectsWeakPasswordWithoutRevealingIt() {
		String weak = "short-pw";

		assertThatIllegalStateException().isThrownBy(() -> initializer(EMAIL, weak).createIfAbsent())
			.withMessageContaining("at least 12 characters")
			.withMessageNotContaining(weak);
		verifyNoInteractions(appUserRepository);
	}

	@Test
	void rejectsPasswordLongerThanBcryptLimit() {
		assertThatIllegalStateException().isThrownBy(() -> initializer(EMAIL, "x".repeat(73)).createIfAbsent())
			.withMessageContaining("72 bytes");
		verifyNoInteractions(appUserRepository);
	}

	private InitialHrManagerInitializer initializer(String email, String password) {
		return new InitialHrManagerInitializer(new InitialHrManagerProperties(email, password), appUserRepository,
				passwordEncoder);
	}

	private AppUser savedUser() {
		ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
		verify(appUserRepository).save(captor.capture());
		return captor.getValue();
	}

}
