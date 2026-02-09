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
      if (!this.mockValues.has(type)) {
        this.mockValues.set(type, 0);
      }

      const interval = setInterval(() => {
        let delta = 0;
        if (type === 'steps') delta = Math.floor(Math.random() * 5);
        if (type === 'heart_rate') delta = Math.floor(Math.random() * 20) - 10; // Simple drift

        const currentVal = this.mockValues.get(type) || 0;
        let newVal = currentVal + delta;
        if (type === 'heart_rate') {
          if (newVal < 60) newVal = 60;
          if (newVal > 180) newVal = 180;
        }

        this.mockValues.set(type, newVal);
        this.notifyListeners('monitoringUpdate', {
          type,
          value: newVal
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
