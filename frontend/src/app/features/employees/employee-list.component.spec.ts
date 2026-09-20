import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { byCountry, byDepartment, employee, page } from '../../testing/fixtures';
import { EmployeeListComponent, SEARCH_DEBOUNCE_MS } from './employee-list.component';

describe('EmployeeListComponent', () => {
  let fixture: ComponentFixture<EmployeeListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EmployeeListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeListComponent);
    fixture.detectChanges();
    http.expectOne('/api/analytics/by-country').flush(byCountry());
    http.expectOne('/api/analytics/by-department').flush(byDepartment());
  });

  afterEach(() => http.verify());

  function employeesRequest(): TestRequest {
    return http.expectOne((request) => request.url === '/api/employees');
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('loads the first page from the server', () => {
    const request = employeesRequest();
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');

    request.flush(page([employee(), employee({ id: 8, employeeCode: 'EMP-00008', lastName: 'Tanaka' })], { totalElements: 10000, totalPages: 500 }));
    fixture.detectChanges();

    expect(text()).toContain('EMP-00007');
    expect(text()).toContain('Sharma, Priya');
    expect(text()).toContain('1 Mar 2020');
    expect(text()).toContain('10,000 employees in total');
    expect(fixture.nativeElement.querySelectorAll('tr.mat-mdc-row').length).toBe(2);
  });

  it('waits for typing to pause, then searches on the server from the first page', fakeAsync(() => {
    employeesRequest().flush(page([employee()]));
    fixture.componentInstance.onPage({ pageIndex: 3, pageSize: 20, length: 200 });
    employeesRequest().flush(page([employee()], { page: 3 }));

    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[formcontrolname=search]');
    input!.value = 'jo';
    input!.dispatchEvent(new Event('input'));
    tick(100);
    input!.value = 'john';
    input!.dispatchEvent(new Event('input'));
    tick(SEARCH_DEBOUNCE_MS - 1);
    http.expectNone((request) => request.url === '/api/employees');

    tick(1);
    const request = employeesRequest();
    expect(request.request.params.get('search')).toBe('john');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([employee({ firstName: 'John' })]));
  }));

  it('sends filters to the server, resets to the first page and combines them', () => {
    employeesRequest().flush(page([employee()]));
    const filters = fixture.componentInstance.filters.controls;

    fixture.componentInstance.onPage({ pageIndex: 2, pageSize: 20, length: 100 });
    employeesRequest().flush(page([]));
    filters.country.setValue('IN');
    const byCountryRequest = employeesRequest();
    expect(byCountryRequest.request.params.get('country')).toBe('IN');
    expect(byCountryRequest.request.params.get('page')).toBe('0');
    byCountryRequest.flush(page([employee()]));

    filters.department.setValue('Engineering');
    employeesRequest().flush(page([employee()]));
    filters.status.setValue('ACTIVE');
    const combined = employeesRequest().request.params;
    expect(combined.get('country')).toBe('IN');
    expect(combined.get('department')).toBe('Engineering');
    expect(combined.get('status')).toBe('ACTIVE');
  });

  it('requests the chosen page and page size', () => {
    employeesRequest().flush(page([employee()], { totalElements: 10000 }));

    fixture.componentInstance.onPage({ pageIndex: 4, pageSize: 100, length: 10000 });

    const request = employeesRequest();
    expect(request.request.params.get('page')).toBe('4');
    expect(request.request.params.get('size')).toBe('100');
  });

  it('offers dropdown values from the existing analytics data', () => {
    employeesRequest().flush(page([]));

    expect(fixture.componentInstance.countries()).toEqual(['IN', 'US']);
    expect(fixture.componentInstance.departments()).toEqual(['Engineering']);
  });

  it('clears all filters with one request', () => {
    employeesRequest().flush(page([employee()]));
    fixture.componentInstance.filters.controls.status.setValue('TERMINATED');
    employeesRequest().flush(page([]));

    fixture.componentInstance.clearFilters();

    const params = employeesRequest().request.params;
    expect(params.has('status')).toBeFalse();
    expect(params.get('page')).toBe('0');
  });

  it('shows an empty state when nothing matches', () => {
    employeesRequest().flush(page([]));
    fixture.detectChanges();

    expect(text()).toContain('No employees found');
  });

  it('shows an error state with a retry', () => {
    employeesRequest().flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load employees');
    fixture.componentInstance.retry();
    employeesRequest().flush(page([employee()]));
    fixture.detectChanges();
    expect(text()).toContain('EMP-00007');
  });
});
