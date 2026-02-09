export interface HealthPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  isAvailable(): Promise<{ available: boolean; platform: "ios" | "android" | "web" }>;
  requestAuthorization(options?: PermissionRequestOptions): Promise<PermissionStatusResult>;
  checkAuthorizationStatus(): Promise<PermissionStatusResult>;
  openSettings(): Promise<{ opened: boolean }>;
  readSamples(options: ReadSamplesOptions): Promise<ReadSamplesResult>;
  writeSamples(options: WriteSamplesOptions): Promise<WriteSamplesResult>;
}

export type HealthDataType =
  | "steps"
  | "distance"
  | "calories"
  | "heart_rate"
  | "sleep";

export type PermissionStatus = "granted" | "denied" | "prompt" | "unsupported";

export interface PermissionRequestOptions {
  read?: HealthDataType[];
  write?: HealthDataType[];
}

export interface PermissionStatusResult {
  read: Record<HealthDataType, PermissionStatus>;
  write: Record<HealthDataType, PermissionStatus>;
}

export interface ReadSamplesOptions {
  types: HealthDataType[];
  startDate: string; // ISO 8601
  endDate: string; // ISO 8601
  limit?: number;
  ascending?: boolean;
  includeMetadata?: boolean;
}

export interface ReadSamplesResult {
  samples: HealthSample[];
}

export interface WriteSamplesOptions {
  samples: HealthSample[];
}

export interface WriteSamplesResult {
  success: boolean;
  writtenCount: number;
}

export interface HealthSample {
  type: HealthDataType;
  value: number;
  unit: HealthUnit;
  startDate: string; // ISO 8601
  endDate?: string; // ISO 8601
  sourceName?: string;
  sourceId?: string;
  metadata?: Record<string, string | number | boolean | null>;
}

export type HealthUnit =
  | "count"
  | "m"
  | "km"
  | "kcal"
  | "bpm"
  | "min"
  | "h";
