import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { CurrentUser } from '../../core/models/auth.models';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let login: jasmine.Spy;
  let navigateByUrl: jasmine.Spy;

  function setup(returnUrl: string | null = null): void {
    login = jasmine.createSpy('login');
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: AuthService, useValue: { login } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) } },
        },
      ],
    });
    navigateByUrl = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  }

  function fillIn(email: string, password: string): void {
    fixture.componentInstance.form.setValue({ email, password });
  }

  function submit(): void {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[type=submit]')?.click();
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('does not call the backend while the form is invalid', () => {
    setup();
    fillIn('not-an-email', '');

    submit();

    expect(login).not.toHaveBeenCalled();
    expect(text()).toContain('Enter a valid email address.');
    expect(text()).toContain('Password is required.');
  });

  it('signs in and goes to the dashboard', () => {
    setup();
    login.and.returnValue(of<CurrentUser>({ email: 'hr@acme.com', role: 'HR_MANAGER' }));
    fillIn('hr@acme.com', 'Secret-Pass-2026');

    submit();

    expect(login).toHaveBeenCalledWith({ email: 'hr@acme.com', password: 'Secret-Pass-2026' });
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
    // The password is not kept in the form once it has been sent.
    expect(fixture.componentInstance.form.controls.password.value).toBe('');
  });

  it('returns to the page the user originally asked for', () => {
    setup('/employees/7/salary');
    login.and.returnValue(of<CurrentUser>({ email: 'hr@acme.com', role: 'HR_MANAGER' }));
    fillIn('hr@acme.com', 'Secret-Pass-2026');

    submit();

    expect(navigateByUrl).toHaveBeenCalledWith('/employees/7/salary');
  });

  it('ignores a return URL pointing to another site', () => {
    setup('//evil.example/phish');
    login.and.returnValue(of<CurrentUser>({ email: 'hr@acme.com', role: 'HR_MANAGER' }));
    fillIn('hr@acme.com', 'Secret-Pass-2026');

    submit();

    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('shows the authentication error and does not keep the password', () => {
    setup();
    login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401, error: { detail: 'Invalid email or password.' } })));
    fillIn('hr@acme.com', 'wrong-password');

    submit();

    expect(text()).toContain('Invalid email or password.');
    expect(fixture.componentInstance.form.controls.password.value).toBe('');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('shows a connection error when the server is unreachable', () => {
    setup();
    login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    fillIn('hr@acme.com', 'Secret-Pass-2026');

    submit();

    expect(text()).toContain('Unable to connect to the server. Please try again.');
  });

  it('disables the button while signing in', () => {
    setup();
    const pending = new Subject<CurrentUser>();
    login.and.returnValue(pending);
    fillIn('hr@acme.com', 'Secret-Pass-2026');

    submit();

    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[type=submit]');
    expect(button?.disabled).toBeTrue();
    expect(button?.textContent).toContain('Signing in');
  });
});
