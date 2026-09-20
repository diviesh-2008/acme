import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  forkJoin,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';

import { AnalyticsService } from '../../core/api/analytics.service';
import { EmployeeService } from '../../core/api/employee.service';
import { describeHttpError } from '../../core/http/http-errors';
import {
  DEFAULT_EMPLOYEE_CRITERIA,
  EMPLOYMENT_STATUSES,
  Employee,
  EmployeeSearchCriteria,
  EmploymentStatus,
  MAX_PAGE_SIZE,
} from '../../core/models/employee.models';
import { PageResponse } from '../../core/models/page.models';
import { BusinessDatePipe, CountPipe, CountryNamePipe, EmploymentStatusPipe } from '../../shared/pipes';
import { StateMessageComponent } from '../../shared/state-message.component';
import { EmployeeListState } from './employee-list-state.service';

/** How long typing must pause before a search is sent. */
export const SEARCH_DEBOUNCE_MS = 400;

type LoadResult = { page: PageResponse<Employee> } | { error: string };

/**
 * The employee directory. Every search, filter and page change is a new server request
 * (GET /api/employees); the browser only ever holds one page. Rows keep the backend's
 * order (last name, first name, id); there is no client-side sorting.
 */
@Component({
  selector: 'app-employee-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    BusinessDatePipe,
    CountPipe,
    CountryNamePipe,
    EmploymentStatusPipe,
    StateMessageComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.scss',
})
export class EmployeeListComponent implements OnInit {
  private readonly employees = inject(EmployeeService);
  private readonly analytics = inject(AnalyticsService);
  private readonly state = inject(EmployeeListState);
  private readonly destroyRef = inject(DestroyRef);

  readonly columns = ['employeeCode', 'name', 'email', 'jobTitle', 'department', 'country', 'status', 'hireDate', 'actions'];
  readonly pageSizeOptions = [10, 20, 50, MAX_PAGE_SIZE];
  readonly statuses = EMPLOYMENT_STATUSES;

  readonly filters = inject(NonNullableFormBuilder).group({
    search: this.state.criteria.search,
    country: this.state.criteria.country,
    department: this.state.criteria.department,
    status: this.state.criteria.status as EmploymentStatus | '',
  });

  readonly page = signal<PageResponse<Employee> | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly countries = signal<string[]>([]);
  readonly departments = signal<string[]>([]);

  private readonly criteria$ = new BehaviorSubject<EmployeeSearchCriteria>(this.state.criteria);

  get criteria(): EmployeeSearchCriteria {
    return this.criteria$.value;
  }

  ngOnInit(): void {
    this.criteria$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        // A newer request cancels one still in flight, so results never arrive out of order.
        switchMap((criteria) =>
          this.employees.search(criteria).pipe(
            map((page): LoadResult => ({ page })),
            catchError((error: unknown) => of<LoadResult>({ error: describeHttpError(error) })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if ('page' in result) {
          this.page.set(result.page);
        } else {
          this.error.set(result.error);
        }
      });

    const controls = this.filters.controls;
    controls.search.valueChanges
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        map((value) => value.trim()),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((search) => this.update({ search, page: 0 }));
    controls.country.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((country) => this.update({ country, page: 0 }));
    controls.department.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((department) => this.update({ department, page: 0 }));
    controls.status.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.update({ status, page: 0 }));

    this.loadFilterOptions();
  }

  onPage(event: PageEvent): void {
    this.update({ page: event.pageIndex, size: event.pageSize });
  }

  hasFilters(): boolean {
    const { search, country, department, status } = this.criteria;
    return search !== '' || country !== '' || department !== '' || status !== '';
  }

  clearFilters(): void {
    this.filters.reset(
      { search: '', country: '', department: '', status: '' },
      { emitEvent: false },
    );
    this.update({ ...DEFAULT_EMPLOYEE_CRITERIA, size: this.criteria.size });
  }

  retry(): void {
    this.criteria$.next(this.criteria);
  }

  private update(change: Partial<EmployeeSearchCriteria>): void {
    const next = { ...this.criteria, ...change };
    this.state.criteria = next;
    this.criteria$.next(next);
  }

  /**
   * Dropdown values come from the existing analytics endpoints, since there is no
   * filter-options endpoint. If they fail, the filters simply offer fewer choices.
   */
  private loadFilterOptions(): void {
    forkJoin({ byCountry: this.analytics.byCountry(), byDepartment: this.analytics.byDepartment() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ byCountry, byDepartment }) => {
          this.countries.set(uniqueSorted([...byCountry.map((row) => row.country), this.criteria.country]));
          this.departments.set(uniqueSorted([...byDepartment.map((row) => row.department), this.criteria.department]));
        },
        error: () => {
          this.countries.set(uniqueSorted([this.criteria.country]));
          this.departments.set(uniqueSorted([this.criteria.department]));
        },
      });
  }
}

function uniqueSorted(values: string[]): string[] {
  return [...new Set(values.filter((value) => value !== ''))].sort((a, b) => a.localeCompare(b));
}
