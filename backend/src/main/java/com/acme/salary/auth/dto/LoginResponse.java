package com.acme.salary.auth.dto;

import java.time.Instant;

/**
 * A successful login.
 *
 * @param accessToken the signed JWT to send as {@code Authorization: Bearer <token>}
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the token expires
 * @param expiresAt when the token expires
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {
}
