import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { EmployeeService } from '../../core/api/employee.service';
import { SalaryService } from '../../core/api/salary.service';
import { describeHttpError } from '../../core/http/http-errors';
import { Employee } from '../../core/models/employee.models';
import { SalaryRecord } from '../../core/models/salary.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { BusinessDatePipe, EmploymentStatusPipe, MoneyPipe } from '../../shared/pipes';
import { StateMessageComponent } from '../../shared/state-message.component';
import { SalaryFormDialogComponent, SalaryFormDialogData } from './salary-form-dialog.component';
import { SALARY_TIMELINE_LABELS, SalaryTimelineStatus, salaryTimelineStatus } from './salary-timeline';

interface HistoryRow {
  record: SalaryRecord;
  status: SalaryTimelineStatus;
  statusLabel: string;
}

/**
 * An employee's current salary and full salary history, with add and correct actions.
 * There is no delete. After every change the data is fetched again from the backend.
 */
@Component({
  selector: 'app-salary-page',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    MoneyPipe,
    BusinessDatePipe,
    EmploymentStatusPipe,
    StateMessageComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './salary-page.component.html',
  styleUrl: './salary-page.component.scss',
})
export class SalaryPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly employees = inject(EmployeeService);
  private readonly salaries = inject(SalaryService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly columns = ['effectiveDate', 'amount', 'status', 'actions'];

  readonly employeeId = signal(0);
  readonly employee = signal<Employee | null>(null);
  readonly current = signal<SalaryRecord | null>(null);
  readonly history = signal<SalaryRecord[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly rows = computed<HistoryRow[]>(() =>
    this.history().map((record) => {
      const status = salaryTimelineStatus(record, this.current());
      return { record, status, statusLabel: SALARY_TIMELINE_LABELS[status] };
    }),
  );
  readonly hasFutureRecords = computed(() => this.rows().some((row) => row.status === 'future'));
  readonly terminated = computed(() => this.employee()?.employmentStatus === 'TERMINATED');

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.employeeId.set(Number(params.get('id')));
      this.load();
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const id = this.employeeId();
    forkJoin({
      employee: this.employees.getById(id),
      current: this.salaries.currentSalary(id),
      history: this.salaries.history(id),
    }).subscribe({
      next: ({ employee, current, history }) => {
        this.employee.set(employee);
        this.current.set(current);
        this.history.set(history);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(describeHttpError(error, { notFound: 'Employee not found.' }));
        this.loading.set(false);
      },
    });
  }

  addSalary(): void {
    const employee = this.employee();
    if (employee === null) {
      return;
    }
    this.openDialog(
      {
        mode: 'create',
        employeeId: employee.id,
        employeeName: fullName(employee),
        suggestedCurrency: this.current()?.currency ?? this.history()[0]?.currency ?? null,
      },
      'Salary record added.',
    );
  }

  correct(record: SalaryRecord): void {
    const employee = this.employee();
    if (employee === null) {
      return;
    }
    this.openDialog({ mode: 'correct', employeeId: employee.id, employeeName: fullName(employee), record }, 'Salary record corrected.');
  }

  private openDialog(data: SalaryFormDialogData, successMessage: string): void {
    this.dialog
      .open<SalaryFormDialogComponent, SalaryFormDialogData, SalaryRecord>(SalaryFormDialogComponent, {
        data,
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.notifications.success(successMessage);
          // Re-read from the backend rather than patching the page locally.
          this.load();
        }
      });
  }
}

function fullName(employee: Employee): string {
  return `${employee.firstName} ${employee.lastName} (${employee.employeeCode})`;
}
