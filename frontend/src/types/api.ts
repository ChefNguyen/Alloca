// TypeScript models generated directly from docs/api/openapi.yaml contract

export type Role = 'EMPLOYEE' | 'MANAGER' | 'ADMIN';

export type ResourceType = 'ROOM' | 'DESK' | 'EQUIPMENT';

export type ResourceStatus = 'AVAILABLE' | 'SUSPENDED' | 'DELETED' | 'PARTIAL' | 'BOOKED' | 'OCCUPIED';

export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export type NotificationType =
  | 'BOOKING_CONFIRMED'
  | 'BOOKING_REMINDER'
  | 'BOOKING_CANCELLED'
  | 'UTILIZATION_ALERT';

export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface DepartmentSummary {
  id: string;
  name: string;
}

export interface Department extends DepartmentSummary {
  createdAt: string;
}

export interface UserSummary {
  id: string;
  username: string;
  fullName: string;
  role: Role;
}

export interface User extends UserSummary {
  email: string;
  isActive: boolean;
  department: DepartmentSummary | null;
  createdAt: string;
}

export interface ZoneSummary {
  id: string;
  name: string;
  floor: string;
  building: string;
}

export interface Zone extends ZoneSummary {
  floorMapUrl: string | null;
  floorMapWidth?: number;
  floorMapHeight?: number;
}

export interface MapPosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ResourceSchedule {
  id: string;
  dayOfWeek: DayOfWeek;
  startTime: string; // "08:00:00"
  endTime: string;   // "18:00:00"
}

export interface Resource {
  id: string;
  name: string;
  type: ResourceType;
  capacity: number;
  status: ResourceStatus;
  description: string | null;
  zone: ZoneSummary | null;
  department: DepartmentSummary | null;
  utilizationThreshold: number;
  mapPosition: MapPosition | null;
}

export interface ResourceDetail extends Resource {
  schedules: ResourceSchedule[];
}

export interface Booking {
  id: string;
  resource: Resource;
  user: UserSummary;
  createdBy: UserSummary;
  startTime: string; // ISO 8601 UTC
  endTime: string;   // ISO 8601 UTC
  status: BookingStatus;
  purpose: string | null;
  reminderSentAt?: string | null;
  createdAt: string;
}

export interface BookingStatusHistory {
  id: string;
  oldStatus: BookingStatus | null;
  newStatus: BookingStatus;
  changedBy: UserSummary | null;
  reason: string | null;
  changedAt: string;
}

export interface BookingDetail extends Booking {
  statusHistory: BookingStatusHistory[];
}

export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  bookingId: string | null;
  resourceId: string | null;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationSettings {
  reminderMinutesBefore: number;
}

export interface UtilizationMetric {
  resource: Resource;
  scheduledMinutes: number;
  operatingMinutes: number;
  utilizationPercent: number | null;
}

export interface UtilizationReport {
  from: string;
  to: string;
  timezone: 'Asia/Ho_Chi_Minh';
  metrics: UtilizationMetric[];
}

export interface BookingTrendPoint {
  date: string;
  confirmedCount: number;
  completedCount: number;
  cancelledCount: number;
}

export interface UtilizationAlert {
  id: string;
  resource: Resource;
  status: AlertStatus;
  windowStart: string;
  windowEnd: string;
  utilizationPercent: number;
  threshold: number;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  resolutionReason: string | null;
  createdAt: string;
}
