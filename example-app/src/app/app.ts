import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Health, HealthDataType, PermissionStatusResult } from 'health-demo';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class App {
  protected readonly inputValue = signal('');
  protected readonly echoValue = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly isBusy = signal(false);
  protected readonly isChecking = signal(false);
  protected readonly availability = signal<{
    available: boolean;
    platform: 'ios' | 'android' | 'web';
  } | null>(null);

  protected readonly isRequestingAuth = signal(false);
  protected readonly isCheckingAuth = signal(false);
  protected readonly authStatus = signal<PermissionStatusResult | null>(null);

  protected readonly canEcho = computed(() => {
    return this.inputValue().trim().length > 0 && !this.isBusy();
  });

  protected readonly canCheck = computed(() => !this.isChecking());
  protected readonly canRequestAuth = computed(() => !this.isRequestingAuth());
  protected readonly canCheckAuth = computed(() => !this.isCheckingAuth());

  protected onInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.inputValue.set(target?.value ?? '');
  }

  protected async runEcho(): Promise<void> {
    const value = this.inputValue().trim();
    if (!value || this.isBusy()) {
      return;
    }

    this.isBusy.set(true);
    this.errorMessage.set(null);
    this.echoValue.set(null);

    try {
      const result = await Health.echo({ value });
      this.echoValue.set(result.value);
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    } finally {
      this.isBusy.set(false);
    }
  }

  protected async checkAvailability(): Promise<void> {
    if (this.isChecking()) {
      return;
    }

    this.isChecking.set(true);
    this.errorMessage.set(null);

    try {
      const result = await Health.isAvailable();
      this.availability.set(result);
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    } finally {
      this.isChecking.set(false);
    }
  }

  protected async requestAuth(): Promise<void> {
    if (this.isRequestingAuth()) {
      return;
    }

    this.isRequestingAuth.set(true);
    this.errorMessage.set(null);

    try {
      const result = await Health.requestAuthorization({
        read: ['steps', 'heart_rate', 'distance', 'calories', 'sleep'],
        write: ['steps']
      });
      this.authStatus.set(result);
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    } finally {
      this.isRequestingAuth.set(false);
    }
  }

  protected async checkAuth(): Promise<void> {
    if (this.isCheckingAuth()) {
      return;
    }

    this.isCheckingAuth.set(true);
    this.errorMessage.set(null);

    try {
      const result = await Health.checkAuthorizationStatus();
      this.authStatus.set(result);
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    } finally {
      this.isCheckingAuth.set(false);
    }
  }

  protected getAuthStatusKeys(): HealthDataType[] {
    const status = this.authStatus();
    if (!status) return [];
    return Object.keys(status.read) as HealthDataType[];
  }

  private formatError(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    return String(error);
  }
}
