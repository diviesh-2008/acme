import { EmploymentStatus } from '../core/models/employee.models';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Formats an amount with its currency symbol and ISO code, e.g. "₹2,759,634.33 INR" or
 * "$126,293.86 USD". The code is always shown, because symbols such as "$" are shared by
 * several currencies and amounts in different currencies must never look comparable.
 */
export function formatMoney(amount: number, currency: string): string {
  let formatted: string;
  try {
    formatted = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
  // Some currencies have no symbol in en-US and format as "SGD 1.00"; avoid "SGD 1.00 SGD".
  return formatted.includes(currency) ? `${formatted.replace(currency, '').trim()} ${currency}` : `${formatted} ${currency}`;
}

const countFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });

/** Head counts with thousands separators: 9377 → "9,377". */
export function formatCount(count: number): string {
  return countFormat.format(count);
}

/**
 * Formats a backend business date ("YYYY-MM-DD") as "19 Sep 2026". The string is split,
 * never parsed through Date, so the viewer's time zone cannot shift the day.
 */
export function formatBusinessDate(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match) {
    return isoDate;
  }
  const [, year, month, day] = match;
  return `${Number(day)} ${MONTHS[Number(month) - 1]} ${year}`;
}

const regionNames = new Intl.DisplayNames(['en'], { type: 'region' });

/** "IN" → "India"; unknown codes are returned unchanged. */
export function countryName(code: string): string {
  try {
    return regionNames.of(code) ?? code;
  } catch {
    return code;
  }
}

const STATUS_LABELS: Record<EmploymentStatus, string> = {
  ACTIVE: 'Active',
  ON_LEAVE: 'On leave',
  TERMINATED: 'Terminated',
};

export function employmentStatusLabel(status: EmploymentStatus): string {
  return STATUS_LABELS[status];
}
