package com.acme.salary.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	// The column collation is case-insensitive, so lookups ignore letter case.
	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

}
