import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AnalyticsService } from '../../core/api/analytics.service';
import { describeHttpError } from '../../core/http/http-errors';
import { AnalyticsOverview } from '../../core/models/analytics.models';
import { BusinessDatePipe, CountPipe, MoneyPipe } from '../../shared/pipes';
import { StateMessageComponent } from '../../shared/state-message.component';

/**
 * A concise overview built on GET /api/analytics/overview. Salaries are shown per currency
 * only; there is deliberately no company-wide average, because currencies cannot be combined.
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    CountPipe,
    MoneyPipe,
    BusinessDatePipe,
    StateMessageComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly analytics = inject(AnalyticsService);

  readonly overview = signal<AnalyticsOverview | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /** A head count, not money: each employee has exactly one current salary. */
  readonly employeesWithSalary = computed(
    () => this.overview()?.currencies.reduce((total, currency) => total + currency.employeeCount, 0) ?? 0,
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.analytics.overview().subscribe({
      next: (overview) => {
        this.overview.set(overview);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(describeHttpError(error));
        this.loading.set(false);
      },
    });
  }
}
