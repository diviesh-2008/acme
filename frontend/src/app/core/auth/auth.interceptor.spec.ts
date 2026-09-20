import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { SESSION_EXPIRED_MESSAGE } from '../http/http-errors';
import { NotificationService } from '../notifications/notification.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let clearSession: jasmine.Spy;
  let notifyError: jasmine.Spy;
  let navigate: jasmine.Spy;

  beforeEach(() => {
    clearSession = jasmine.createSpy('clearSession').and.returnValue(true);
    notifyError = jasmine.createSpy('error');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { accessToken: () => 'token-123', clearSession } },
        { provide: NotificationService, useValue: { error: notifyError, success: jasmine.createSpy('success') } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API requests', () => {
    http.get('/api/employees').subscribe();

    expect(backend.expectOne('/api/employees').request.headers.get('Authorization')).toBe('Bearer token-123');
  });

  it('never sends the token with the login request', () => {
    http.post('/api/auth/login', { email: 'hr@acme.com', password: 'x' }).subscribe();

    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBeFalse();
  });

  it('never sends the token to other hosts', () => {
    http.get('https://fonts.googleapis.com/icon').subscribe();

    expect(backend.expectOne('https://fonts.googleapis.com/icon').request.headers.has('Authorization')).toBeFalse();
  });

  it('on 401 clears the session, explains why and redirects to /login', () => {
    let received: HttpErrorResponse | undefined;
    http.get('/api/employees').subscribe({ error: (error: HttpErrorResponse) => (received = error) });

    backend.expectOne('/api/employees').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(received?.status).toBe(401);
    expect(clearSession).toHaveBeenCalled();
    expect(notifyError).toHaveBeenCalledWith(SESSION_EXPIRED_MESSAGE);
    expect(navigate).toHaveBeenCalledWith(['/login'], jasmine.any(Object));
  });

  it('reports an ended session once when several requests fail together', () => {
    clearSession.and.returnValues(true, false, false);
    for (const url of ['/api/analytics/overview', '/api/analytics/by-country', '/api/analytics/by-department']) {
      http.get(url).subscribe({ error: () => undefined });
    }

    for (const request of backend.match((r) => r.url.startsWith('/api/analytics/'))) {
      request.flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    }

    expect(clearSession).toHaveBeenCalledTimes(3);
    expect(notifyError).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalled();
  });

  it('leaves a failed login to the login page', () => {
    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });

    backend.expectOne('/api/auth/login').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(clearSession).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not sign out on other errors', () => {
    http.get('/api/employees/1').subscribe({ error: () => undefined });

    backend.expectOne('/api/employees/1').flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });

    expect(clearSession).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
});
