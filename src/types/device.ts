
export interface Vector2 {
  x: number;
  y: number;
}

export interface DeviceInfo {
  id: number;
  address: string;
  deviceName: string;
  size_px: Vector2;
  size_m: Vector2;
}

export interface TouchEvent {
  id: number;
  position: Vector2;
  delta: Vector2;
  size: number;
  pressure: number;
}

export interface SensorData {
  accelerometer: Vector2;
  gravity: Vector2;
  gyroscope: Vector2;
  magneticField: Vector2;
  proximity: number;
  light: number;
  temperature: number;
}
