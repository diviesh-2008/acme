/** Body of POST /api/auth/login. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Response of POST /api/auth/login. */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  /** Seconds until the token expires. */
  expiresIn: number;
  /** ISO-8601 instant, e.g. "2026-09-20T09:30:00Z". */
  expiresAt: string;
}

/** Response of GET /api/auth/me. */
export interface CurrentUser {
  email: string;
  role: string;
}
