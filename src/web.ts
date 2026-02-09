import { WebPlugin } from '@capacitor/core';

import type {
  HealthPlugin,
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

  async requestPermissions(_options?: PermissionRequestOptions): Promise<PermissionStatusResult> {
    throw this.unimplemented('requestPermissions is not implemented on web.');
  }

  async checkPermissions(): Promise<PermissionStatusResult> {
    throw this.unimplemented('checkPermissions is not implemented on web.');
  }

  async openSettings(): Promise<{ opened: boolean }> {
    throw this.unimplemented('openSettings is not implemented on web.');
  }

  async readSamples(_options: ReadSamplesOptions): Promise<ReadSamplesResult> {
    throw this.unimplemented('readSamples is not implemented on web.');
  }

  async writeSamples(_options: WriteSamplesOptions): Promise<WriteSamplesResult> {
    throw this.unimplemented('writeSamples is not implemented on web.');
  }
}
