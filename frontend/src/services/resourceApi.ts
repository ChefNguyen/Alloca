import { apiRequest } from '@/lib/api';
import { Department, Resource, ResourceDetail, ResourceStatus, ResourceType, Zone } from '@/types/api';

export interface Page<T> {
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

// Mirrors ResourceCreateRequest in docs/api/openapi.yaml
export interface ResourceCreateInput {
  name: string;
  type: ResourceType;
  capacity: number;
  description: string | null;
  zoneId: string;
  departmentId: string | null;
  utilizationThreshold: number;
  status: 'AVAILABLE' | 'SUSPENDED';
}

// Mirrors ResourceUpdateRequest; every field is optional and `null` clears nullable fields.
export type ResourceUpdateInput = Partial<Omit<ResourceCreateInput, 'status'>>;

const json = (body: unknown) => ({ body: JSON.stringify(body) });

export const listResources = (params: { page?: number; size?: number; sort?: string } = {}) => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined) query.set(key, String(value));
  });
  return apiRequest<Page<Resource>>(`/api/v1/resources?${query}`);
};

export const createResource = (input: ResourceCreateInput) =>
  apiRequest<Resource>('/api/v1/resources', { method: 'POST', ...json(input) });

export const updateResource = (id: string, input: ResourceUpdateInput) =>
  apiRequest<ResourceDetail>(`/api/v1/resources/${id}`, { method: 'PATCH', ...json(input) });

export const updateResourceStatus = (id: string, status: Exclude<ResourceStatus, 'DELETED'>, reason?: string) =>
  apiRequest<Resource>(`/api/v1/resources/${id}/status`, { method: 'PATCH', ...json({ status, reason }) });

export const deleteResource = (id: string) => apiRequest<void>(`/api/v1/resources/${id}`, { method: 'DELETE' });

export const listZones = () => apiRequest<Zone[]>('/api/v1/zones');

export const listDepartments = () => apiRequest<Department[]>('/api/v1/departments');
