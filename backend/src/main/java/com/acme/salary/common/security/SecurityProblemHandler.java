package com.acme.salary.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Hands security-filter failures (missing or invalid token, missing role) to the MVC
 * exception handlers, so they get the same ProblemDetail body as every other API error.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final HandlerExceptionResolver exceptionResolver;

	public SecurityProblemHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		resolve(request, response, exception, HttpServletResponse.SC_UNAUTHORIZED);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException exception) throws IOException {
		resolve(request, response, exception, HttpServletResponse.SC_FORBIDDEN);
	}

	private void resolve(HttpServletRequest request, HttpServletResponse response, Exception exception,
			int fallbackStatus) throws IOException {
		if (exceptionResolver.resolveException(request, response, null, exception) == null) {
			response.sendError(fallbackStatus);
		}
	}

}
