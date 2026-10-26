import { apiRequest } from '@/lib/api';
import { StoredSession } from '@/lib/authStorage';
import { Role } from '@/types/api';

// Mirrors LoginResponse / UserResponse of the backend (docs/api/openapi.yaml).
interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: {
    id: string;
    username: string;
    fullname: string;
    email: string;
    role: Role;
    isActive: boolean;
    department: { id: string; name: string } | null;
    createdAt: string;
  };
}

export const login = async (username: string, password: string): Promise<StoredSession> => {
  const response = await apiRequest<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  const { user } = response;
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    user: {
      id: user.id,
      username: user.username,
      fullName: user.fullname,
      email: user.email,
      role: user.role,
      isActive: user.isActive,
      department: user.department,
      createdAt: user.createdAt,
    },
  };
};
