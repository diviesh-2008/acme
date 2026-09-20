import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';

import { SalaryStatistics } from '../../core/models/analytics.models';
import { CountPipe, MoneyPipe } from '../../shared/pipes';

/** One row of a statistics table: an optional group label plus one currency's figures. */
export interface StatisticsRow extends SalaryStatistics {
  group?: string;
}

/**
 * Current-salary statistics, one row per currency (optionally per group). Rows keep the
 * backend's order; the table never sorts by amount, because amounts in different
 * currencies are not comparable. Every amount shows its currency.
 */
@Component({
  selector: 'app-statistics-table',
  imports: [MatTableModule, CountPipe, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-scroll">
      <table mat-table [dataSource]="rows()" class="statistics-table">
        <caption class="visually-hidden">{{ caption() }}</caption>

        <ng-container matColumnDef="group">
          <th mat-header-cell *matHeaderCellDef scope="col">{{ groupLabel() }}</th>
          <td mat-cell *matCellDef="let row">{{ row.group }}</td>
        </ng-container>

        <ng-container matColumnDef="currency">
          <th mat-header-cell *matHeaderCellDef scope="col">Currency</th>
          <td mat-cell *matCellDef="let row"><strong>{{ row.currency }}</strong></td>
        </ng-container>

        <ng-container matColumnDef="employeeCount">
          <th mat-header-cell *matHeaderCellDef scope="col" class="numeric">Employees</th>
          <td mat-cell *matCellDef="let row" class="numeric">{{ row.employeeCount | count }}</td>
        </ng-container>

        <ng-container matColumnDef="averageSalary">
          <th mat-header-cell *matHeaderCellDef scope="col" class="numeric">Average</th>
          <td mat-cell *matCellDef="let row" class="numeric">{{ row.averageSalary | money: row.currency }}</td>
        </ng-container>

        <ng-container matColumnDef="medianSalary">
          <th mat-header-cell *matHeaderCellDef scope="col" class="numeric">Median</th>
          <td mat-cell *matCellDef="let row" class="numeric">{{ row.medianSalary | money: row.currency }}</td>
        </ng-container>

        <ng-container matColumnDef="minimumSalary">
          <th mat-header-cell *matHeaderCellDef scope="col" class="numeric">Minimum</th>
          <td mat-cell *matCellDef="let row" class="numeric">{{ row.minimumSalary | money: row.currency }}</td>
        </ng-container>

        <ng-container matColumnDef="maximumSalary">
          <th mat-header-cell *matHeaderCellDef scope="col" class="numeric">Maximum</th>
          <td mat-cell *matCellDef="let row" class="numeric">{{ row.maximumSalary | money: row.currency }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns()"></tr>
        <tr mat-row *matRowDef="let row; columns: columns()"></tr>
      </table>
    </div>
  `,
  styles: `
    .statistics-table {
      width: 100%;
      min-width: 820px;
    }
    .visually-hidden {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class StatisticsTableComponent {
  readonly rows = input.required<StatisticsRow[]>();
  /** Header of the group column ("Country", "Department"); no group column when null. */
  readonly groupLabel = input<string | null>(null);
  readonly caption = input.required<string>();

  readonly columns = computed(() => [
    ...(this.groupLabel() ? ['group'] : []),
    'currency',
    'employeeCount',
    'averageSalary',
    'medianSalary',
    'minimumSalary',
    'maximumSalary',
  ]);
}
