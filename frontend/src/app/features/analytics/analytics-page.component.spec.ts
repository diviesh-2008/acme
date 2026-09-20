import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { byCountry, byDepartment, overview } from '../../testing/fixtures';
import { AnalyticsPageComponent } from './analytics-page.component';

describe('AnalyticsPageComponent', () => {
  let fixture: ComponentFixture<AnalyticsPageComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AnalyticsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnalyticsPageComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function respond(withData = true): void {
    http.expectOne('/api/analytics/overview').flush(withData ? overview() : overview({ currencies: [] }));
    http.expectOne('/api/analytics/by-country').flush(withData ? byCountry() : []);
    http.expectOne('/api/analytics/by-department').flush(withData ? byDepartment() : []);
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function rows(): string[] {
    return Array.from(element().querySelectorAll('tr.mat-mdc-row')).map((row) => row.textContent ?? '');
  }

  async function openTab(label: string): Promise<void> {
    const tab = Array.from(element().querySelectorAll<HTMLElement>('[role=tab]')).find((t) => t.textContent?.includes(label));
    tab?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows the UTC business date the figures are for, without shifting it', () => {
    respond();

    expect(element().querySelector('.as-of')?.textContent).toContain('Compensation figures as of 19 Sep 2026 (UTC)');
  });

  it('shows one overview row per currency, each amount with its own currency', () => {
    respond();

    const overviewRows = rows();
    expect(overviewRows.length).toBe(2);
    expect(overviewRows[0]).toContain('INR');
    expect(overviewRows[0]).toContain('₹2,759,634.33 INR');
    expect(overviewRows[0]).toContain('₹2,738,000.00 INR');
    expect(overviewRows[1]).toContain('$126,293.86 USD');
    expect(overviewRows[1]).not.toContain('INR');
  });

  it('never shows a combined figure across currencies', () => {
    respond();

    expect(element().textContent).not.toMatch(/company average|all currencies|total salary/i);
    expect(element().textContent).toContain('Amounts in different currencies are never combined');
  });

  it('keeps a country with two currencies as two separate rows', async () => {
    respond();

    await openTab('By country');

    const countryRows = rows();
    expect(countryRows.length).toBe(3);
    expect(countryRows[0]).toContain('India (IN)');
    expect(countryRows[0]).toContain('₹2,759,634.33 INR');
    expect(countryRows[1]).toContain('India (IN)');
    expect(countryRows[1]).toContain('$127,551.52 USD');
    expect(countryRows[2]).toContain('United States (US)');
  });

  it('shows department rows per currency', async () => {
    respond();

    await openTab('By department');

    const departmentRows = rows();
    expect(departmentRows.length).toBe(2);
    expect(departmentRows[0]).toContain('Engineering');
    expect(departmentRows[0]).toContain('£80,000.00 GBP');
    expect(departmentRows[1]).toContain('₹3,100,000.00 INR');
  });

  it('shows an empty state, not zero amounts, when no one has a current salary', async () => {
    respond(false);

    expect(element().textContent).toContain('No current salaries');
    expect(element().textContent).not.toMatch(/[$₹£€]0/);
    await openTab('By country');
    expect(element().textContent).toContain('No country data');
  });

  it('shows an error state with a retry', () => {
    const others = [http.expectOne('/api/analytics/by-country'), http.expectOne('/api/analytics/by-department')];
    http.expectOne('/api/analytics/overview').flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element().textContent).toContain('Could not load analytics');
    expect(element().textContent).toContain('The server could not complete the request');
    expect(others.every((request) => request.cancelled)).toBeTrue();

    element().querySelector<HTMLButtonElement>('app-state-message button')?.click();
    respond();
    expect(rows().length).toBe(2);
  });
});
