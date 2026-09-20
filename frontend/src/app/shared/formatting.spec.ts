import { countryName, employmentStatusLabel, formatBusinessDate, formatMoney } from './formatting';

describe('formatting', () => {
  describe('formatMoney', () => {
    it('always shows the currency code next to the amount', () => {
      expect(formatMoney(2759634.33, 'INR')).toBe('₹2,759,634.33 INR');
      expect(formatMoney(126293.86, 'USD')).toBe('$126,293.86 USD');
      expect(formatMoney(139545.9, 'AUD')).toBe('A$139,545.90 AUD');
    });

    it('does not repeat the code for currencies without a symbol', () => {
      expect(formatMoney(118263, 'SGD')).toBe('118,263.00 SGD');
    });

    it('keeps two decimal places, including for the largest DECIMAL(15,2) amount', () => {
      expect(formatMoney(100.5, 'EUR')).toBe('€100.50 EUR');
      expect(formatMoney(9999999999999.99, 'USD')).toBe('$9,999,999,999,999.99 USD');
    });
  });

  describe('formatBusinessDate', () => {
    it('formats the backend date string without any time-zone conversion', () => {
      expect(formatBusinessDate('2026-09-19')).toBe('19 Sep 2026');
      expect(formatBusinessDate('2027-01-01')).toBe('1 Jan 2027');
    });

    it('returns anything unexpected unchanged', () => {
      expect(formatBusinessDate('not-a-date')).toBe('not-a-date');
    });
  });

  it('names countries and statuses', () => {
    expect(countryName('IN')).toBe('India');
    expect(employmentStatusLabel('ON_LEAVE')).toBe('On leave');
  });
});
