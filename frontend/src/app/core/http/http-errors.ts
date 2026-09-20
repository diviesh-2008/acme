import { HttpErrorResponse } from '@angular/common/http';

/** The backend's RFC 9457 error body. */
export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  /** Field name → message, for validation errors. */
  errors?: Record<string, string>;
}

export const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.';

/** Optional wording for the current screen, e.g. what a 404 means here. */
export interface ErrorMessageOptions {
  notFound?: string;
}

/**
 * Turns any API failure into a message that is safe to show. Backend `detail` and field
 * messages are written for users (no stack traces or internals), so they are shown for
 * 400, 404 and 409; everything else gets a fixed message.
 */
export function describeHttpError(error: unknown, options: ErrorMessageOptions = {}): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Something went wrong. Please try again.';
  }
  const problem = problemDetail(error);
  switch (error.status) {
    case 0:
      return 'Unable to connect to the server. Please try again.';
    case 400: {
      const fields = Object.entries(problem?.errors ?? {});
      if (fields.length > 0) {
        return fields.map(([field, message]) => `${field}: ${message}`).join('; ');
      }
      return problem?.detail ?? 'The request was not valid.';
    }
    case 401:
      return SESSION_EXPIRED_MESSAGE;
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return options.notFound ?? problem?.detail ?? 'The requested item was not found.';
    case 409:
      return problem?.detail ?? 'The request conflicts with existing data.';
    default:
      return error.status >= 500
        ? 'The server could not complete the request. Please try again later.'
        : 'Something went wrong. Please try again.';
  }
}

/** Field-level validation messages from a 400 response, or an empty object. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof HttpErrorResponse && error.status === 400) {
    return problemDetail(error)?.errors ?? {};
  }
  return {};
}

function problemDetail(error: HttpErrorResponse): ProblemDetail | null {
  const body: unknown = error.error;
  return body !== null && typeof body === 'object' ? (body as ProblemDetail) : null;
}
