import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { employee } from '../../testing/fixtures';
import { EmployeeDetailComponent } from './employee-detail.component';

describe('EmployeeDetailComponent', () => {
  let fixture: ComponentFixture<EmployeeDetailComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EmployeeDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '7' })) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDetailComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('shows the employee read-only, with a link to the salary', () => {
    http.expectOne('/api/employees/7').flush(employee({ employmentStatus: 'ON_LEAVE' }));
    fixture.detectChanges();

    expect(text()).toContain('Priya Sharma');
    expect(text()).toContain('EMP-00007');
    expect(text()).toContain('priya.sharma7@acme.example');
    expect(text()).toContain('Software Engineer');
    expect(text()).toContain('Engineering');
    expect(text()).toContain('India (IN)');
    expect(text()).toContain('On leave');
    expect(text()).toContain('1 Mar 2020');
    const salaryLink = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('a[href="/employees/7/salary"]');
    expect(salaryLink?.textContent).toContain('View salary');
    // v1 is read-only: no edit or delete actions.
    expect(text()).not.toMatch(/Edit|Delete/);
  });

  it('says so when the employee does not exist', () => {
    http.expectOne('/api/employees/7').flush({ detail: 'Employee 7 was not found.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text()).toContain('Employee not found.');
  });
});
