import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CurrentUser, LoginRequest, LoginResponse } from '../models/auth.models';

/** What is kept between page reloads. The password is never stored. */
interface StoredSession {
  accessToken: string;
  /** ISO-8601 instant from the login response. */
  expiresAt: string;
}

export const SESSION_STORAGE_KEY = 'acme.session';

export const LOGIN_URL = `${environment.apiBaseUrl}/auth/login`;

/**
 * Holds the signed-in HR Manager's access token.
 * <p>
 * The token lives in sessionStorage: it survives a page reload but not closing the tab or
 * browser. The JWT is never decoded; `expiresAt` from the login response only decides
 * when to stop sending it. The backend validates every request and stays the authority.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<StoredSession | null>(readStoredSession());

  /** The signed-in user from GET /api/auth/me, once loaded. */
  readonly currentUser = signal<CurrentUser | null>(null);

  /** True while a non-expired token is held. A UX check only; the backend decides. */
  isAuthenticated(): boolean {
    const session = this.session();
    return session !== null && Date.parse(session.expiresAt) > Date.now();
  }

  /** The bearer token to send, or null when signed out or expired. */
  accessToken(): string | null {
    return this.isAuthenticated() ? (this.session()?.accessToken ?? null) : null;
  }

  login(credentials: LoginRequest): Observable<CurrentUser> {
    return this.http.post<LoginResponse>(LOGIN_URL, credentials).pipe(
      tap((response) => this.storeSession({ accessToken: response.accessToken, expiresAt: response.expiresAt })),
      switchMap(() => this.loadCurrentUser()),
    );
  }

  loadCurrentUser(): Observable<CurrentUser> {
    return this.http
      .get<CurrentUser>(`${environment.apiBaseUrl}/auth/me`)
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  /**
   * Forgets the token and user (e.g. after a 401) without navigating.
   *
   * @returns whether there was a session to clear, so parallel 401s report it only once
   */
  clearSession(): boolean {
    const hadSession = this.session() !== null;
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
    this.session.set(null);
    this.currentUser.set(null);
    return hadSession;
  }

  logout(): void {
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  private storeSession(session: StoredSession): void {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
  }
}

function readStoredSession(): StoredSession | null {
  const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
  if (raw === null) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (isStoredSession(parsed)) {
      return parsed;
    }
  } catch {
    // Corrupt entry: treat as signed out.
  }
  sessionStorage.removeItem(SESSION_STORAGE_KEY);
  return null;
}

function isStoredSession(value: unknown): value is StoredSession {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate['accessToken'] === 'string' && typeof candidate['expiresAt'] === 'string';
}
