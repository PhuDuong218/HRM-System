/** Display the offset from the assigned shift, without changing attendance/payroll policy. */
export function clockTiming(planned: string | null | undefined, actual: string | null | undefined, side: 'in' | 'out'): string {
  if (!planned || !actual) return '';
  // API LocalDateTime values are business-local wall clock times. Compare both on
  // the same fixed axis, including their date, so overnight shifts remain correct.
  const parse = (value: string) => Date.parse(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : value + 'Z');
  const delta = parse(actual) - parse(planned);
  if (!Number.isFinite(delta)) return '';
  const label = side === 'in' ? 'Check-in' : 'Check-out';
  if (delta === 0) return `${label} đúng giờ`;
  const seconds = Math.floor(Math.abs(delta) / 1000);
  const minutes = Math.floor(seconds / 60), rest = seconds % 60;
  const amount = seconds >= 3600 ? `${Math.floor(seconds / 3600)} giờ ${minutes % 60} phút ${rest} giây` : minutes ? `${minutes} phút${rest ? ` ${rest} giây` : ''}` : seconds ? `${seconds} giây` : 'dưới 1 giây';
  return `${label} ${delta > 0 ? 'muộn' : 'sớm'} ${amount}`;
}
