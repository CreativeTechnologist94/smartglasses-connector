
interface IPInputProps {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

export function IPInput({ value, onChange, disabled }: IPInputProps) {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value.trim().replace(/[^0-9.]/g, '');
    onChange(newValue);
  };

  return (
    <div className="space-y-2">
      <label htmlFor="ip-input" className="text-sm font-medium text-gray-700">
        HMD IP Address
      </label>
      <input
        id="ip-input"
        type="text"
        value={value}
        onChange={handleChange}
        disabled={disabled}
        placeholder="192.168.0.1"
        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
      />
    </div>
  );
}
