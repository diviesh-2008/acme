import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { CurrentUser } from '../models/auth.models';
import { AuthService, SESSION_STORAGE_KEY } from './auth.service';

describe('AuthService', () => {
  const inOneHour = () => new Date(Date.now() + 3_600_000).toISOString();

  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('stores the token in sessionStorage (never the password) and loads the user on login', () => {
    const auth = TestBed.inject(AuthService);
    let user: CurrentUser | undefined;

    auth.login({ email: 'hr@acme.com', password: 'Secret-Pass-2026' }).subscribe((u) => (user = u));
    const login = http.expectOne('/api/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ email: 'hr@acme.com', password: 'Secret-Pass-2026' });
    login.flush({ accessToken: 'token-123', tokenType: 'Bearer', expiresIn: 3600, expiresAt: inOneHour() });
    http.expectOne('/api/auth/me').flush({ email: 'hr@acme.com', role: 'HR_MANAGER' });

    expect(user).toEqual({ email: 'hr@acme.com', role: 'HR_MANAGER' });
    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.accessToken()).toBe('token-123');
    const stored = sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '';
    expect(stored).toContain('token-123');
    expect(stored).not.toContain('Secret-Pass-2026');
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('stores nothing when the credentials are rejected', () => {
    const auth = TestBed.inject(AuthService);
    let failed = false;

    auth.login({ email: 'hr@acme.com', password: 'wrong' }).subscribe({ error: () => (failed = true) });
    http
      .expectOne('/api/auth/login')
      .flush({ status: 401, detail: 'Invalid email or password.' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBeTrue();
    expect(auth.isAuthenticated()).toBeFalse();
    expect(auth.accessToken()).toBeNull();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('restores a valid session after a page reload', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'token-123', expiresAt: inOneHour() }));

    const auth = TestBed.inject(AuthService);

    expect(auth.isAuthenticated()).toBeTrue();
    expect(auth.accessToken()).toBe('token-123');
  });

  it('treats an expired token as signed out and never sends it', () => {
    const expired = new Date(Date.now() - 1000).toISOString();
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'old-token', expiresAt: expired }));

    const auth = TestBed.inject(AuthService);

    expect(auth.isAuthenticated()).toBeFalse();
    expect(auth.accessToken()).toBeNull();
  });

  it('ignores a corrupt stored session', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, '{not json');

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('logout clears the session and user and returns to /login', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'token-123', expiresAt: inOneHour() }));
    const auth = TestBed.inject(AuthService);
    auth.currentUser.set({ email: 'hr@acme.com', role: 'HR_MANAGER' });
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    auth.logout();

    expect(auth.isAuthenticated()).toBeFalse();
    expect(auth.currentUser()).toBeNull();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('reports whether there was a session to clear', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'token-123', expiresAt: inOneHour() }));
    const auth = TestBed.inject(AuthService);

    expect(auth.clearSession()).toBeTrue();
    expect(auth.clearSession()).toBeFalse();
  });
});
