import { apiRequest } from '../lib/api.ts';
import type {
  AlertStatus,
  BookingTrendReport,
  UtilizationAlert,
  UtilizationReport,
} from '../types/api.ts';

export interface PageResponse<T> {
  items: T[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
  };
}

export interface UtilizationQuery {
  from: string;
  to: string;
  resourceId?: string;
  departmentId?: string;
}

export interface AlertQuery {
  status?: AlertStatus;
  resourceId?: string;
  departmentId?: string;
  page?: number;
  size?: number;
}

export const analyticsApi = {
  getUtilization: (query: UtilizationQuery) => {
    const params = new URLSearchParams();
    params.set('from', query.from);
    params.set('to', query.to);
    if (query.resourceId) params.set('resourceId', query.resourceId);
    if (query.departmentId) params.set('departmentId', query.departmentId);
    return apiRequest<UtilizationReport>(`/api/v1/analytics/utilization?${params.toString()}`);
  },

  getBookingTrends: (query: UtilizationQuery) => {
    const params = new URLSearchParams();
    params.set('from', query.from);
    params.set('to', query.to);
    if (query.resourceId) params.set('resourceId', query.resourceId);
    if (query.departmentId) params.set('departmentId', query.departmentId);
    return apiRequest<BookingTrendReport>(`/api/v1/analytics/trends?${params.toString()}`);
  },

  getAlerts: (query?: AlertQuery) => {
    const params = new URLSearchParams();
    if (query?.status) params.set('status', query.status);
    if (query?.resourceId) params.set('resourceId', query.resourceId);
    if (query?.departmentId) params.set('departmentId', query.departmentId);
    if (query?.page !== undefined) params.set('page', String(query.page));
    if (query?.size !== undefined) params.set('size', String(query.size));
    const qs = params.toString();
    return apiRequest<PageResponse<UtilizationAlert>>(`/api/v1/alerts${qs ? `?${qs}` : ''}`);
  },

  acknowledgeAlert: (alertId: string) =>
    apiRequest<UtilizationAlert>(`/api/v1/alerts/${alertId}/acknowledge`, {
      method: 'POST',
    }),
};
