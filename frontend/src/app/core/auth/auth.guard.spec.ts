import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { authGuard, guestGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  let signedIn: boolean;
  let clearSession: jasmine.Spy;

  beforeEach(() => {
    signedIn = false;
    clearSession = jasmine.createSpy('clearSession');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: () => signedIn, clearSession } },
      ],
    });
  });

  function runAuthGuard(url: string): ReturnType<typeof authGuard> {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  it('lets a signed-in user through', () => {
    signedIn = true;

    expect(runAuthGuard('/employees')).toBeTrue();
  });

  it('sends a signed-out user to /login, remembering where they were going', () => {
    const result = runAuthGuard('/employees/7/salary');

    expect(result instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Femployees%2F7%2Fsalary');
    expect(clearSession).toHaveBeenCalled();
  });

  it('sends a signed-in user away from the login page', () => {
    signedIn = true;

    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, { url: '/login' } as RouterStateSnapshot),
    );

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });

  it('shows the login page to signed-out users', () => {
    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, { url: '/login' } as RouterStateSnapshot),
    );

    expect(result).toBeTrue();
  });
});
