import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { overview } from '../../testing/fixtures';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideNoopAnimations()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('shows a card per currency and the as-of date, with no company-wide salary', () => {
    http.expectOne('/api/analytics/overview').flush(overview());
    fixture.detectChanges();

    const cards = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.currency-card')).map(
      (card) => card.textContent ?? '',
    );
    expect(cards.length).toBe(2);
    expect(cards[0]).toContain('INR');
    expect(cards[0]).toContain('1,433 employees');
    expect(cards[0]).toContain('₹2,759,634.33 INR');
    expect(cards[1]).toContain('$126,293.86 USD');
    expect(text()).toContain('Compensation figures as of 19 Sep 2026 (UTC)');
    // Head counts can be added up; salaries in different currencies cannot.
    expect(text()).toContain('4,432');
    expect(text()).toContain('Currencies paid');
    expect(text()).not.toMatch(/company average|average company/i);
  });

  it('shows an empty state instead of zero amounts', () => {
    http.expectOne('/api/analytics/overview').flush(overview({ currencies: [] }));
    fixture.detectChanges();

    expect(text()).toContain('No current salaries yet');
    expect(text()).not.toMatch(/[$₹]0/);
  });

  it('shows an error state when the overview cannot be loaded', () => {
    http.expectOne('/api/analytics/overview').error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(text()).toContain('Unable to connect to the server. Please try again.');
  });
});
