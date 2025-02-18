
import { useEffect, useState } from 'react';
import { DeviceInfo } from '@/types/device';
import { ConnectionStatus } from '@/components/ConnectionStatus';
import { IPInput } from '@/components/IPInput';
import { ConnectButton } from '@/components/ConnectButton';

const Index = () => {
  const [deviceInfo, setDeviceInfo] = useState<DeviceInfo>({
    id: -1,
    address: "",
    deviceName: "",
    size_px: { x: 0, y: 0 },
    size_m: { x: 0, y: 0 }
  });

  const [isConnected, setIsConnected] = useState(false);
  const [hmdIPAddress, setHmdIPAddress] = useState("192.168.0.1");

  const handleConnect = () => {
    // TODO: Implement connection logic
    setIsConnected(true);
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-semibold text-gray-900">Smart Glasses Connector</h1>
          <p className="text-sm text-gray-500 mt-2">Connect and manage your smart glasses device</p>
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
        </div>
      </div>
    </div>
  );
};

export default Index;
