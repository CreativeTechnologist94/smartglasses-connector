
import { useEffect, useState } from 'react';
import { DeviceInfo, SensorData } from '@/types/device';
import { ConnectionStatus } from '@/components/ConnectionStatus';
import { IPInput } from '@/components/IPInput';
import { ConnectButton } from '@/components/ConnectButton';
import { useToast } from '@/hooks/use-toast';

const Index = () => {
  const { toast } = useToast();
  const [deviceInfo, setDeviceInfo] = useState<DeviceInfo>({
    id: 1,
    address: "192.168.0.1",
    deviceName: "Smart Glasses Simulator",
    size_px: { x: 1920, y: 1080 },
    size_m: { x: 0.152, y: 0.085 }
  });

  const [isConnected, setIsConnected] = useState(false);
  const [hmdIPAddress, setHmdIPAddress] = useState("192.168.0.1");
  const [sensorData, setSensorData] = useState<SensorData>({
    accelerometer: { x: 0, y: 0 },
    gravity: { x: 0, y: 0 },
    gyroscope: { x: 0, y: 0 },
    magneticField: { x: 0, y: 0 },
    proximity: 0,
    light: 0,
    temperature: 20
  });

  const handleConnect = () => {
    // Simulate connection establishment
    setIsConnected(true);
    toast({
      title: "Connected",
      description: `Connected to device at ${hmdIPAddress}`
    });

    // Start sending simulated sensor data
    startSensorSimulation();
  };

  const startSensorSimulation = () => {
    // Simulate sensor data updates every second
    const interval = setInterval(() => {
      setSensorData(prev => ({
        ...prev,
        accelerometer: {
          x: Math.random() * 2 - 1,
          y: Math.random() * 2 - 1
        },
        gyroscope: {
          x: Math.random() * 360,
          y: Math.random() * 360
        },
        light: Math.random() * 1000,
        temperature: 20 + Math.random() * 5
      }));
    }, 1000);

    return () => clearInterval(interval);
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-semibold text-gray-900">Smart Glasses Simulator</h1>
          <p className="text-sm text-gray-500 mt-2">Simulate a smart glasses device for testing</p>
        </div>
        
        <div className="bg-white p-6 rounded-lg shadow-sm space-y-4">
          <ConnectionStatus isConnected={isConnected} />
          
          <IPInput 
            value={hmdIPAddress} 
            onChange={setHmdIPAddress}
            disabled={isConnected}
          />
          
          <ConnectButton 
            isConnected={isConnected}
            onClick={handleConnect}
            disabled={!hmdIPAddress}
          />

          {isConnected && (
            <div className="mt-6 space-y-4">
              <div className="bg-gray-50 p-4 rounded-md">
                <h3 className="text-sm font-medium text-gray-700 mb-2">Device Info</h3>
                <pre className="text-xs text-gray-600 overflow-auto">
                  {JSON.stringify(deviceInfo, null, 2)}
                </pre>
              </div>

              <div className="bg-gray-50 p-4 rounded-md">
                <h3 className="text-sm font-medium text-gray-700 mb-2">Sensor Data</h3>
                <pre className="text-xs text-gray-600 overflow-auto">
                  {JSON.stringify(sensorData, null, 2)}
                </pre>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Index;
