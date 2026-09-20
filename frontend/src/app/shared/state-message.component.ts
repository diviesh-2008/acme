import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** A centred icon, title and message for empty and error states, with an optional action. */
@Component({
  selector: 'app-state-message',
  imports: [MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="state" [class.error]="tone() === 'error'" [attr.role]="tone() === 'error' ? 'alert' : 'status'">
      <mat-icon class="icon" aria-hidden="true">{{ icon() }}</mat-icon>
      <h2>{{ title() }}</h2>
      @if (message()) {
        <p>{{ message() }}</p>
      }
      @if (actionLabel()) {
        <button mat-stroked-button type="button" (click)="action.emit()">{{ actionLabel() }}</button>
      }
    </div>
  `,
  styles: `
    .state {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: 40px 16px;
      color: var(--mat-sys-on-surface-variant);
    }
    .icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      margin-bottom: 8px;
    }
    .error .icon {
      color: var(--mat-sys-error);
    }
    h2 {
      margin: 0 0 4px;
      font: var(--mat-sys-title-medium);
      color: var(--mat-sys-on-surface);
    }
    p {
      margin: 0 0 16px;
      max-width: 480px;
    }
  `,
})
export class StateMessageComponent {
  readonly icon = input('info');
  readonly title = input.required<string>();
  readonly message = input<string | null>(null);
  readonly tone = input<'info' | 'error'>('info');
  readonly actionLabel = input<string | null>(null);
  readonly action = output<void>();
}
