import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

/** Layout for every signed-in page: toolbar, navigation and the current user. */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.currentUser;

  /** Phones and small tablets get an overlay menu instead of a fixed side bar. */
  readonly compact = toSignal(
    inject(BreakpointObserver)
      .observe([Breakpoints.XSmall, Breakpoints.Small])
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  readonly navItems: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/employees', label: 'Employees', icon: 'people' },
    { path: '/analytics', label: 'Analytics', icon: 'insights' },
  ];

  ngOnInit(): void {
    // After a page reload the token is restored but the user details are not.
    if (this.user() === null) {
      this.auth.loadCurrentUser().subscribe({
        error: () => {
          // A 401 is handled by the interceptor; the toolbar simply shows no name otherwise.
        },
      });
    }
  }

  /** "HR_MANAGER" → "HR Manager". Display only; the backend enforces the role. */
  roleLabel(role: string): string {
    return role === 'HR_MANAGER' ? 'HR Manager' : role;
  }

  closeIfCompact(sidenav: MatSidenav): void {
    if (this.compact()) {
      void sidenav.close();
    }
  }

  logout(): void {
    this.auth.logout();
  }
}
