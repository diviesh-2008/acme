import { SalaryRecord } from '../../core/models/salary.models';

export type SalaryTimelineStatus = 'current' | 'future' | 'past';

/**
 * Labels a history record relative to the backend's current salary. The UI never decides
 * "today" itself: the record the backend returned as current is CURRENT, later dates are
 * FUTURE and earlier ones PAST. With no current salary, every record is still to come.
 */
export function salaryTimelineStatus(record: SalaryRecord, current: SalaryRecord | null): SalaryTimelineStatus {
  if (current === null) {
    return 'future';
  }
  if (record.id === current.id) {
    return 'current';
  }
  // ISO dates ("YYYY-MM-DD") compare correctly as strings.
  return record.effectiveDate > current.effectiveDate ? 'future' : 'past';
}

export const SALARY_TIMELINE_LABELS: Record<SalaryTimelineStatus, string> = {
  current: 'Current',
  future: 'Future',
  past: 'Past',
};
