import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { forkJoin } from 'rxjs';

import { AnalyticsService } from '../../core/api/analytics.service';
import { describeHttpError } from '../../core/http/http-errors';
import { countryName } from '../../shared/formatting';
import { BusinessDatePipe } from '../../shared/pipes';
import { StateMessageComponent } from '../../shared/state-message.component';
import { StatisticsRow, StatisticsTableComponent } from './statistics-table.component';

interface AnalyticsView {
  asOfDate: string;
  overview: StatisticsRow[];
  byCountry: StatisticsRow[];
  byDepartment: StatisticsRow[];
}

/** The three analytics views, each shown per currency exactly as the backend computes them. */
@Component({
  selector: 'app-analytics-page',
  imports: [
    MatTabsModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    BusinessDatePipe,
    StateMessageComponent,
    StatisticsTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analytics-page.component.html',
  styleUrl: './analytics-page.component.scss',
})
export class AnalyticsPageComponent implements OnInit {
  private readonly analytics = inject(AnalyticsService);

  readonly view = signal<AnalyticsView | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      overview: this.analytics.overview(),
      byCountry: this.analytics.byCountry(),
      byDepartment: this.analytics.byDepartment(),
    }).subscribe({
      next: ({ overview, byCountry, byDepartment }) => {
        this.view.set({
          asOfDate: overview.asOfDate,
          overview: overview.currencies,
          byCountry: byCountry.map((row) => ({ ...row, group: `${countryName(row.country)} (${row.country})` })),
          byDepartment: byDepartment.map((row) => ({ ...row, group: row.department })),
        });
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(describeHttpError(error));
        this.loading.set(false);
      },
    });
  }
}
