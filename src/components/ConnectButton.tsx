
interface ConnectButtonProps {
  isConnected: boolean;
  onClick: () => void;
  disabled?: boolean;
}

export function ConnectButton({ isConnected, onClick, disabled }: ConnectButtonProps) {
  return (
    <button
      onClick={onClick}
      disabled={disabled || isConnected}
      className={`w-full py-2 px-4 rounded-md font-medium transition-colors 
        ${isConnected 
          ? 'bg-green-500 text-white cursor-not-allowed' 
          : 'bg-blue-500 hover:bg-blue-600 text-white'
        } ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
    >
      {isConnected ? 'Connected' : 'Connect'}
    </button>
  );
}
