import { apiRequest } from '@/lib/api';
import { Page } from '@/services/resourceApi';
import { DepartmentSummary, Role, User } from '@/types/api';

export interface UserApiResponse {
  id: string;
  username: string;
  fullname: string;
  email: string;
  role: Role;
  isActive: boolean;
  department: DepartmentSummary | null;
  createdAt: string;
}

export interface UserCreateInput {
  username: string;
  email: string;
  password: string;
  fullName: string;
  role: Role;
  departmentId?: string | null;
  isActive?: boolean;
}

export interface UserUpdateInput {
  email?: string;
  fullName?: string;
  newPassword?: string;
  isActive?: boolean;
}

export interface UserAccessUpdateInput {
  role: Role;
  departmentId?: string | null;
}

const toUser = (item: UserApiResponse): User => ({
  id: item.id,
  username: item.username,
  fullName: item.fullname,
  email: item.email,
  role: item.role,
  isActive: item.isActive,
  department: item.department,
  createdAt: item.createdAt,
});

export const listUsers = async (
  params: {
    page?: number;
    size?: number;
    sort?: string;
    q?: string;
    role?: Role;
    departmentId?: string;
    isActive?: boolean;
  } = {},
): Promise<Page<User>> => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  });
  const res = await apiRequest<Page<UserApiResponse>>(`/api/v1/users?${query}`);
  return {
    items: res.items.map(toUser),
    page: res.page,
  };
};

export const getUser = async (id: string): Promise<User> => {
  const res = await apiRequest<UserApiResponse>(`/api/v1/users/${id}`);
  return toUser(res);
};

export const createUser = async (input: UserCreateInput): Promise<User> => {
  const res = await apiRequest<UserApiResponse>('/api/v1/users', {
    method: 'POST',
    body: JSON.stringify(input),
  });
  return toUser(res);
};

export const updateUser = async (id: string, input: UserUpdateInput): Promise<User> => {
  const res = await apiRequest<UserApiResponse>(`/api/v1/users/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
  return toUser(res);
};

export const updateUserAccess = async (id: string, input: UserAccessUpdateInput): Promise<User> => {
  const res = await apiRequest<UserApiResponse>(`/api/v1/users/${id}/access`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
  return toUser(res);
};

export const deactivateUser = (id: string): Promise<void> =>
  apiRequest<void>(`/api/v1/users/${id}`, { method: 'DELETE' });
