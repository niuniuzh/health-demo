import { ChangeDetectionStrategy, Component, computed, OnDestroy, signal } from '@angular/core';
import { Health, HealthDataType, HealthUnit, PermissionStatusResult, ReadSamplesResult } from 'health-demo';
import { PluginListenerHandle } from '@capacitor/core';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class App implements OnDestroy {
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
  protected readonly isMonitoring = signal(false);
  protected readonly liveData = signal<Map<string, number>>(new Map());
  protected readonly writeStatus = signal<string | null>(null);
  protected readonly isReading = signal(false);
  protected readonly readResults = signal<ReadSamplesResult | null>(null);

  private monitoringListener: PluginListenerHandle | null = null;

  ngOnDestroy(): void {
    this.cleanupMonitoring();
  }

  private async cleanupMonitoring(): Promise<void> {
    if (this.monitoringListener) {
      await this.monitoringListener.remove();
      this.monitoringListener = null;
    }
    // ensure native monitoring is also stopped just in case
    try {
      await Health.stopMonitoring();
    } catch {
      // ignore errors during cleanup
    }
  }

  protected readonly selectedType = signal<HealthDataType>('steps');
  protected readonly dataValue = signal('100');
  protected readonly availableTypes: HealthDataType[] = ['steps', 'distance', 'calories', 'heart_rate', 'sleep'];

  protected readonly canEcho = computed(() => {
    return this.inputValue().trim().length > 0 && !this.isBusy();
  });

  protected readonly canCheck = computed(() => !this.isChecking());
  protected readonly canRequestAuth = computed(() => !this.isRequestingAuth());
  protected readonly canCheckAuth = computed(() => !this.isCheckingAuth());
  protected readonly canWrite = computed(() => !this.isWriting() && !isNaN(parseFloat(this.dataValue())));
  protected readonly canRead = computed(() => !this.isReading());

  protected onInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.inputValue.set(target?.value ?? '');
  }

  protected onDataValueInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.dataValue.set(target?.value ?? '');
  }

  protected onTypeChange(event: Event): void {
    const target = event.target as HTMLSelectElement | null;
    this.selectedType.set((target?.value as HealthDataType) ?? 'steps');
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

  protected async openSettings(): Promise<void> {
    this.errorMessage.set(null);
    try {
      const result = await Health.openSettings();
      if (!result.opened) {
        this.errorMessage.set('Could not open settings. Health Connect might not be available.');
      }
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    }
  }

  protected async toggleMonitoring(): Promise<void> {
    console.log('toggleMonitoring called, current status:', this.isMonitoring());
    if (this.isMonitoring()) {
      await this.cleanupMonitoring();
      this.isMonitoring.set(false);
    } else {
      try {
        await this.cleanupMonitoring(); // Clean up any lingering listener first

        // 1. Add listener FIRST to avoid missing the initial baseline event
        this.monitoringListener = await Health.addListener('monitoringUpdate', (data: { type: HealthDataType; value: number }) => {
          console.log('monitoringUpdate received:', JSON.stringify(data));
          this.liveData.update(map => {
            const newMap = new Map(map);
            newMap.set(data.type, data.value);
            return newMap;
          });
        });

        // 2. Start monitoring
        await Health.startMonitoring({
          types: ['steps', 'heart_rate']
        });

        this.isMonitoring.set(true);
        // We don't clear the whole map immediately if we want to show transition, 
        // allow values to persist until updated by the new session.

      } catch (error: unknown) {
        this.errorMessage.set(this.formatError(error));
        this.isMonitoring.set(false);
        await this.cleanupMonitoring();
      }
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
        write: ['steps', 'heart_rate', 'distance', 'calories', 'sleep']
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

  protected async writeData(): Promise<void> {
    if (this.isWriting()) {
      return;
    }

    const type = this.selectedType();
    const value = parseFloat(this.dataValue());
    if (isNaN(value)) {
      this.errorMessage.set('Invalid numeric value');
      return;
    }

    this.isWriting.set(true);
    this.errorMessage.set(null);
    this.writeStatus.set(null);

    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
    const unit = this.getUnitForType(type);

    try {
      await Health.writeSamples({
        samples: [
          {
            type: type,
            value: value,
            unit: unit,
            startDate: oneHourAgo.toISOString(),
            endDate: now.toISOString(),
            sourceName: 'Example App',
          },
        ],
      });
      this.writeStatus.set(`Successfully wrote ${value} ${unit} to ${type}`);
    } catch (error: unknown) {
      this.errorMessage.set(this.formatError(error));
    } finally {
      this.isWriting.set(false);
    }
  }

  protected async readData(): Promise<void> {
    if (this.isReading()) {
      return;
    }

    const type = this.selectedType();
    this.isReading.set(true);
    this.errorMessage.set(null);
    this.readResults.set(null);

    const now = new Date();
    const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);

    try {
      const result = await Health.readSamples({
        types: [type],
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

  private getUnitForType(type: HealthDataType): HealthUnit {
    switch (type) {
      case 'steps':
        return 'count';
      case 'distance':
        return 'm';
      case 'calories':
        return 'kcal';
      case 'heart_rate':
        return 'bpm';
      case 'sleep':
        return 'min';
      default:
        return 'count';
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
