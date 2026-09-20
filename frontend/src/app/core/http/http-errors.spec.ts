import { HttpErrorResponse } from '@angular/common/http';

import { describeHttpError, fieldErrors } from './http-errors';

describe('describeHttpError', () => {
  const response = (status: number, error: unknown = null) =>
    new HttpErrorResponse({ status, error, statusText: 'x', url: '/api/x' });

  it('explains network failures', () => {
    expect(describeHttpError(response(0))).toBe('Unable to connect to the server. Please try again.');
  });

  it('lists field errors for 400', () => {
    const error = response(400, { detail: 'One or more fields are invalid.', errors: { amount: 'must be greater than 0' } });

    expect(describeHttpError(error)).toBe('amount: must be greater than 0');
    expect(fieldErrors(error)).toEqual({ amount: 'must be greater than 0' });
  });

  it('uses fixed messages for 401 and 403', () => {
    expect(describeHttpError(response(401))).toBe('Your session has expired. Please sign in again.');
    expect(describeHttpError(response(403))).toBe('You do not have permission to perform this action.');
  });

  it('uses the screen-specific message for 404 when given', () => {
    expect(describeHttpError(response(404, { detail: 'Employee 9 was not found.' }), { notFound: 'Employee not found.' }))
      .toBe('Employee not found.');
    expect(describeHttpError(response(404, { detail: 'Employee 9 was not found.' }))).toBe('Employee 9 was not found.');
  });

  it("shows the backend's conflict message for 409", () => {
    const error = response(409, { detail: 'Employee 7 already has a salary record effective on 2026-01-01.' });

    expect(describeHttpError(error)).toBe('Employee 7 already has a salary record effective on 2026-01-01.');
  });

  it('never shows server internals for 500', () => {
    const error = response(500, { detail: 'java.lang.NullPointerException at com.acme...' });

    expect(describeHttpError(error)).toBe('The server could not complete the request. Please try again later.');
  });
});
