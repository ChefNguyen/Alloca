import type { TimeSlot } from '../services/bookingApi.ts';

export const BUSINESS_ZONE = 'Asia/Ho_Chi_Minh';
export const businessDate = (instant: Date = new Date()): string => {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: BUSINESS_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })
    .formatToParts(instant);
  const part = (name: string) => parts.find(p => p.type === name)!.value;
  return `${part('year')}-${part('month')}-${part('day')}`;
};
export const businessTime = (instant: string) => new Intl.DateTimeFormat('en-GB', {
  timeZone: BUSINESS_ZONE, hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
}).format(new Date(instant));
export const formatBookingDate = (instant: string) => new Intl.DateTimeFormat('en-GB', {
  timeZone: BUSINESS_ZONE, day: '2-digit', month: 'short', year: 'numeric',
}).format(new Date(instant));
export const dateAfter = (date: string, days: number) => {
  const value = new Date(`${date}T00:00:00+07:00`);
  value.setTime(value.getTime() + days * 86400000);
  return businessDate(value);
};
export const localBookingInstant = (date: string, time: string): string => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^([01]\d|2[0-3]):(00|30)$/.test(time)) {
    throw new Error('Choose a date and a time on a 30-minute boundary.');
  }
  const value = new Date(`${date}T${time}:00+07:00`);
  if (Number.isNaN(value.getTime()) || businessDate(value) !== date) throw new Error('Choose a valid date.');
  return value.toISOString();
};

/** End choices must follow uninterrupted free half-hour slots; a gap cannot be booked across. */
export const availableEnds = (slots: TimeSlot[], startTime: string): string[] => {
  const start = Date.parse(startTime);
  const byStart = new Map(slots.map(slot => [Date.parse(slot.startTime), Date.parse(slot.endTime)]));
  const ends: string[] = [];
  let cursor = start;
  while (byStart.has(cursor) && ends.length < 16) {
    const next = byStart.get(cursor)!;
    if (next - cursor !== 1800000 || next - start > 8 * 3600000) break;
    ends.push(new Date(next).toISOString());
    cursor = next;
  }
  return ends;
};
