import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guard';

/** Everything except /login needs a signed-in HR Manager. The shell and every page load lazily. */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · ACME Salary Management',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · ACME Salary Management',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'employees',
        title: 'Employees · ACME Salary Management',
        loadComponent: () =>
          import('./features/employees/employee-list.component').then((m) => m.EmployeeListComponent),
      },
      {
        path: 'employees/:id',
        title: 'Employee · ACME Salary Management',
        loadComponent: () =>
          import('./features/employees/employee-detail.component').then((m) => m.EmployeeDetailComponent),
      },
      {
        path: 'employees/:id/salary',
        title: 'Salary · ACME Salary Management',
        loadComponent: () => import('./features/salary/salary-page.component').then((m) => m.SalaryPageComponent),
      },
      {
        path: 'analytics',
        title: 'Analytics · ACME Salary Management',
        loadComponent: () =>
          import('./features/analytics/analytics-page.component').then((m) => m.AnalyticsPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
