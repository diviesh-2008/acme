import { Pipe, PipeTransform } from '@angular/core';

import { EmploymentStatus } from '../core/models/employee.models';
import { countryName, employmentStatusLabel, formatBusinessDate, formatCount, formatMoney } from './formatting';

/** `{{ 9377 | count }}` → "9,377". */
@Pipe({ name: 'count' })
export class CountPipe implements PipeTransform {
  transform(count: number): string {
    return formatCount(count);
  }
}

/** `{{ amount | money: currency }}` → "₹2,759,634.33 INR". */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number, currency: string): string {
    return formatMoney(amount, currency);
  }
}

/** `{{ '2026-09-19' | businessDate }}` → "19 Sep 2026", independent of the viewer's time zone. */
@Pipe({ name: 'businessDate' })
export class BusinessDatePipe implements PipeTransform {
  transform(isoDate: string): string {
    return formatBusinessDate(isoDate);
  }
}

/** `{{ 'IN' | countryName }}` → "India". */
@Pipe({ name: 'countryName' })
export class CountryNamePipe implements PipeTransform {
  transform(code: string): string {
    return countryName(code);
  }
}

/** `{{ 'ON_LEAVE' | employmentStatus }}` → "On leave". */
@Pipe({ name: 'employmentStatus' })
export class EmploymentStatusPipe implements PipeTransform {
  transform(status: EmploymentStatus): string {
    return employmentStatusLabel(status);
  }
}
