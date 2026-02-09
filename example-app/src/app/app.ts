import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Health, HealthDataType, PermissionStatusResult, ReadSamplesResult } from 'health-demo';

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

  protected readonly isWriting = signal(false);
  protected readonly writeStatus = signal<string | null>(null);
  protected readonly isReading = signal(false);
  protected readonly readResults = signal<ReadSamplesResult | null>(null);

  protected readonly canEcho = computed(() => {
    return this.inputValue().trim().length > 0 && !this.isBusy();
  });

  protected readonly canCheck = computed(() => !this.isChecking());
  protected readonly canRequestAuth = computed(() => !this.isRequestingAuth());
  protected readonly canCheckAuth = computed(() => !this.isCheckingAuth());
  protected readonly canWrite = computed(() => !this.isWriting());
  protected readonly canRead = computed(() => !this.isReading());

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

  protected async writeSteps(): Promise<void> {
    if (this.isWriting()) {
      return;
    }

    this.isWriting.set(true);
    this.errorMessage.set(null);
    this.writeStatus.set(null);

    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);

    try {
      await Health.writeSamples({
        samples: [
          {
            type: 'steps',
            value: 100,
            unit: 'count',
            startDate: oneHourAgo.toISOString(),
            endDate: now.toISOString(),
            sourceName: 'Example App',
            metadata: {
              'my-key': 'my-value',
            },
          },
        ],
      });
      this.writeStatus.set('Successfully wrote 100 steps');
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    } finally {
      this.isWriting.set(false);
    }
  }

  protected async readSteps(): Promise<void> {
    if (this.isReading()) {
      return;
    }

    this.isReading.set(true);
    this.errorMessage.set(null);
    this.readResults.set(null);

    const now = new Date();
    const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);

    try {
      const result = await Health.readSamples({
        types: ['steps'],
        startDate: oneDayAgo.toISOString(),
        endDate: now.toISOString(),
      });
      this.readResults.set(result);
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    } finally {
      this.isReading.set(false);
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
