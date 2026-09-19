package com.acme.salary.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every API error into an RFC 9457 {@code ProblemDetail} response.
 * <p>
 * Standard Spring MVC errors (malformed JSON, unsupported media type, unknown route, ...)
 * are handled by the base class. Messages are generic: no stack traces, exception
 * details or submitted values are ever returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		// A value of the wrong type (e.g. page=abc) gets a plain message instead of Java conversion details.
		ex.getBindingResult()
			.getFieldErrors()
			.forEach(error -> errors.putIfAbsent(error.getField(),
					error.isBindingFailure() ? "Invalid value" : error.getDefaultMessage()));

		ProblemDetail problem = ex.getBody();
		problem.setDetail("One or more fields are invalid.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/** Failed login. The message is the same for an unknown email and a wrong password. */
	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail handleBadCredentials(BadCredentialsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
	}

	/** Authentication could not be attempted, e.g. the user store is unavailable. */
	@ExceptionHandler(AuthenticationServiceException.class)
	ProblemDetail handleAuthenticationService(AuthenticationServiceException ex) {
		return handleUnexpected(ex);
	}

	/** Missing, malformed, expired or wrongly signed bearer token. */
	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
			.body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "A valid access token is required."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"You do not have permission to access this resource.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
	}

}
