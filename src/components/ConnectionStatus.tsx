
interface ConnectionStatusProps {
  isConnected: boolean;
}

export function ConnectionStatus({ isConnected }: ConnectionStatusProps) {
  return (
    <div className="flex items-center justify-between px-4 py-2 bg-gray-50 rounded-md">
      <span className="text-sm font-medium text-gray-700">Connection Status</span>
      <div className="flex items-center">
        <div className={`w-3 h-3 rounded-full mr-2 ${
          isConnected ? 'bg-green-500' : 'bg-gray-300'
        }`} />
        <span className="text-sm text-gray-600">
          {isConnected ? 'Connected' : 'Disconnected'}
        </span>
      </div>
    </div>
  );
}
