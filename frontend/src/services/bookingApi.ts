import { apiRequest } from '../lib/api.ts';
import type { Booking, BookingStatusHistory, BookingStatus, Resource, UserSummary } from '../types/api.ts';

export interface BookingRecord extends Booking {
  cancelledBy: UserSummary | null;
  cancelReason: string | null;
}
export interface BookingDetails extends BookingRecord { statusHistory: BookingStatusHistory[] }
export interface BookingPage<T = BookingRecord> {
  items: T[];
  page: { number: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean };
}
export interface BookingFilters {
  page?: number; size?: number; sort?: string; from?: string; to?: string;
  resourceId?: string; userId?: string; departmentId?: string; status?: BookingStatus;
}
export interface Availability {
  resourceId: string; date: string; timezone: string; resourceStatus: string;
  operatingWindows: TimeSlot[]; availableSlots: TimeSlot[];
}
export interface TimeSlot { startTime: string; endTime: string }
export interface CreateBookingInput {
  resourceId: string; userId?: string; startTime: string; endTime: string; purpose?: string;
}
const query = (params: object) => {
  const result = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') result.set(key, String(value));
  });
  return result.toString();
};
export const listBookings = (filters: BookingFilters = {}) =>
  apiRequest<BookingPage>(`/api/v1/bookings?${query(filters)}`);
export const getBooking = (id: string) => apiRequest<BookingDetails>(`/api/v1/bookings/${encodeURIComponent(id)}`);
export const getBookingHistory = (id: string) =>
  apiRequest<BookingStatusHistory[]>(`/api/v1/bookings/${encodeURIComponent(id)}/history`);
export const getAvailability = (resourceId: string, date: string) =>
  apiRequest<Availability>(`/api/v1/resources/${encodeURIComponent(resourceId)}/availability?${query({ date, slotMinutes: 30 })}`);
export const createBooking = (input: CreateBookingInput) =>
  apiRequest<BookingRecord>('/api/v1/bookings', { method: 'POST', body: JSON.stringify(input) });
export const cancelBooking = (id: string, reason?: string) =>
  apiRequest<void>(`/api/v1/bookings/${encodeURIComponent(id)}/cancel`, {
    method: 'POST', ...(reason ? { body: JSON.stringify({ reason }) } : {}),
  });

// The global booking picker pages real resources rather than using the dashboard's demo catalog.
export const searchBookingResources = (q = '', page = 0) =>
  apiRequest<BookingPage<Resource>>(`/api/v1/resources?${query({ q, page, size: 25, sort: 'name,asc', status: 'AVAILABLE' })}`);

export interface BookingOwner extends UserSummary { isActive: boolean }
export const searchBookingOwners = (q = '', page = 0) =>
  apiRequest<BookingPage<BookingOwner>>(`/api/v1/users?${query({ q, page, size: 25, sort: 'username,asc', isActive: true })}`);
