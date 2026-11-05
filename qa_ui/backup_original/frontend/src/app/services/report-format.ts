import type { PayrollReport } from './report.service';

// Quote monetary JSON numbers before parsing to preserve SQL decimal digits.
export function parsePayrollReport(source: string): PayrollReport {
  const amounts = new Set(['grossPayroll', 'allowances', 'bonuses', 'deductions', 'netPayroll']);
  let result = '';
  let index = 0;
  while (index < source.length) {
    if (source[index] !== '"') { result += source[index++]; continue; }
    const start = index++;
    while (index < source.length) {
      if (source[index] === '\\') { index += 2; continue; }
      if (source[index++] === '"') break;
    }
    const token = source.slice(start, index);
    result += token;
    const key: unknown = JSON.parse(token);
    if (typeof key !== 'string' || !amounts.has(key)) continue;
    const match = /^(\s*:\s*)(-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)(?=\s*[,}])/.exec(source.slice(index));
    if (match) {
      result += match[1] + JSON.stringify(match[2]);
      index += match[0].length;
    }
  }
  return JSON.parse(result) as PayrollReport;
}

// Group the integer part without converting the decimal to a JavaScript number.
export function formatMoney(value: string): string {
  const match = /^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(value);
  if (!match) return String(value);
  const exponent = Number(match[4] || 0);
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 1000) return value;
  const digits = match[2] + (match[3] || '');
  const point = match[2].length + exponent;
  let whole: string;
  let fraction: string;
  if (point <= 0) { whole = '0'; fraction = '0'.repeat(-point) + digits; }
  else if (point >= digits.length) { whole = digits + '0'.repeat(point - digits.length); fraction = ''; }
  else { whole = digits.slice(0, point); fraction = digits.slice(point); }
  whole = whole.replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return match[1] + whole + (fraction ? ',' + fraction : '');
}
