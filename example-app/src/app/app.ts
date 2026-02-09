import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { Health } from 'health-demo';

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

  protected readonly canEcho = computed(() => {
    return this.inputValue().trim().length > 0 && !this.isBusy();
  });

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

  private formatError(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    return String(error);
  }
}
