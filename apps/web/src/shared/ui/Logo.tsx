/** Original IPP mark: an abstract gate with a shield. Not an official emblem (see GAPS G-01). */
export function Logo({ size = 40 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="IPP">
      <rect x="4" y="10" width="10" height="46" rx="2" fill="#d6c49c" />
      <rect x="50" y="10" width="10" height="46" rx="2" fill="#d6c49c" />
      <path d="M4 14 Q32 -2 60 14 L60 20 Q32 6 4 20 Z" fill="#6b7f4e" />
      <path d="M32 22 L46 27 V38 C46 46 39 51 32 54 C25 51 18 46 18 38 V27 Z" fill="#1f3a5f" stroke="#f6f1e6"
        strokeWidth="2" />
      <path d="M26 38 L30.5 42.5 L39 33" fill="none" stroke="#f6f1e6" strokeWidth="3" strokeLinecap="round"
        strokeLinejoin="round" />
    </svg>
  );
}
