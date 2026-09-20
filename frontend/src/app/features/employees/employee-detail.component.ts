import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, switchMap, tap } from 'rxjs';

import { EmployeeService } from '../../core/api/employee.service';
import { describeHttpError } from '../../core/http/http-errors';
import { Employee } from '../../core/models/employee.models';
import { BusinessDatePipe, CountryNamePipe, EmploymentStatusPipe } from '../../shared/pipes';
import { StateMessageComponent } from '../../shared/state-message.component';

type LoadResult = { employee: Employee } | { error: string };

/** Read-only employee profile (employees cannot be created, edited or deleted in v1). */
@Component({
  selector: 'app-employee-detail',
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    BusinessDatePipe,
    CountryNamePipe,
    EmploymentStatusPipe,
    StateMessageComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-detail.component.html',
  styleUrl: './employee-detail.component.scss',
})
export class EmployeeDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly employees = inject(EmployeeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly employee = signal<Employee | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        map((params) => Number(params.get('id'))),
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((id) =>
          this.employees.getById(id).pipe(
            map((employee): LoadResult => ({ employee })),
            catchError((error: unknown) =>
              of<LoadResult>({ error: describeHttpError(error, { notFound: 'Employee not found.' }) }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if ('employee' in result) {
          this.employee.set(result.employee);
        } else {
          this.employee.set(null);
          this.error.set(result.error);
        }
      });
  }
}
