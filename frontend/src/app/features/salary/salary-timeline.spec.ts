import { salary } from '../../testing/fixtures';
import { salaryTimelineStatus } from './salary-timeline';

describe('salaryTimelineStatus', () => {
  const past = salary(1, '2025-01-01', 800000);
  const current = salary(2, '2026-01-01', 900000);
  const future = salary(3, '2027-01-01', 1000000);

  it("labels records relative to the backend's current salary", () => {
    expect(salaryTimelineStatus(current, current)).toBe('current');
    expect(salaryTimelineStatus(future, current)).toBe('future');
    expect(salaryTimelineStatus(past, current)).toBe('past');
  });

  it('treats every record as future when no salary is in effect yet', () => {
    expect(salaryTimelineStatus(future, null)).toBe('future');
  });
});
