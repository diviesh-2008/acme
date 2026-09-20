import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SESSION_EXPIRED_MESSAGE } from '../http/http-errors';
import { NotificationService } from '../notifications/notification.service';
import { AuthService, LOGIN_URL } from './auth.service';

/**
 * Adds `Authorization: Bearer <token>` to backend API calls (never to login or other
 * hosts). When the backend answers 401 to a signed-in call, the session is over: forget the
 * token and send the user to /login.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationService);

  const isApiCall = request.url.startsWith(`${environment.apiBaseUrl}/`);
  const isLogin = request.url === LOGIN_URL;
  const token = isApiCall && !isLogin ? auth.accessToken() : null;
  const outgoing = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isApiCall && !isLogin) {
        // Several requests can fail together; only the first one that ends the session says so.
        if (auth.clearSession()) {
          notifications.error(SESSION_EXPIRED_MESSAGE);
        }
        const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
        void router.navigate(['/login'], { queryParams: returnUrl ? { returnUrl } : {} });
      }
      return throwError(() => error);
    }),
  );
};
