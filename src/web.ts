import { WebPlugin } from '@capacitor/core';

import type {
  HealthPlugin,
  HealthDataType,
  PermissionRequestOptions,
  PermissionStatusResult,
  ReadSamplesOptions,
  ReadSamplesResult,
  WriteSamplesOptions,
  WriteSamplesResult,
} from './definitions';

export class HealthWeb extends WebPlugin implements HealthPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async isAvailable(): Promise<{ available: boolean; platform: "ios" | "android" | "web" }> {
    return { available: false, platform: "web" };
  }

  async requestAuthorization(_options?: PermissionRequestOptions): Promise<PermissionStatusResult> {
    throw this.unimplemented('requestAuthorization is not implemented on web.');
  }

  async checkAuthorizationStatus(): Promise<PermissionStatusResult> {
    throw this.unimplemented('checkAuthorizationStatus is not implemented on web.');
  }

  async openSettings(): Promise<{ opened: boolean }> {
    throw this.unimplemented('openSettings is not implemented on web.');
  }

  private monitoringIntervals: Map<HealthDataType, any> = new Map();
  private mockValues: Map<HealthDataType, number> = new Map();

  async startMonitoring(options: { types: HealthDataType[] }): Promise<void> {
    this.stopMonitoring();
    options.types.forEach(type => {
      // Only initialize if we don't have a value yet (from a previous session)
      if (!this.mockValues.has(type)) {
        const baseline = type === 'steps' ? 5000 : 75;
        this.mockValues.set(type, baseline);
      }

      const initialVal = this.mockValues.get(type)!;

      // Trigger an immediate update so the UI doesn't show 0/empty initially
      this.notifyListeners('monitoringUpdate', {
        type,
        value: initialVal,
      });

      const interval = setInterval(() => {
        let newVal = this.mockValues.get(type) || 0;
        if (type === 'steps') {
          newVal += Math.floor(Math.random() * 3) + 1; // 1-3 steps increment
        } else if (type === 'heart_rate') {
          // Random walk for heart rate
          const drift = Math.floor(Math.random() * 5) - 2;
          newVal = Math.max(60, Math.min(180, newVal + drift));
        }

        this.mockValues.set(type, newVal);
        this.notifyListeners('monitoringUpdate', {
          type,
          value: newVal,
        });
      }, 1000);
      this.monitoringIntervals.set(type, interval);
    });
  }

  async stopMonitoring(): Promise<void> {
    this.monitoringIntervals.forEach(interval => clearInterval(interval));
    this.monitoringIntervals.clear();
  }

  async readSamples(_options: ReadSamplesOptions): Promise<ReadSamplesResult> {
    throw this.unimplemented('readSamples is not implemented on web.');
  }

  async writeSamples(_options: WriteSamplesOptions): Promise<WriteSamplesResult> {
    throw this.unimplemented('writeSamples is not implemented on web.');
  }
}
