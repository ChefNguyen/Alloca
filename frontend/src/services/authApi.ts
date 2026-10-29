import { apiRequest, ApiError, refreshAccessToken } from '../lib/api.ts';
import { clearSession, getSessionVersion, loadSession, saveSession } from '../lib/authStorage.ts';
import type { StoredSession } from '../lib/authStorage.ts';
import type { User } from '../types/api.ts';

interface UserResponse extends Omit<User, 'fullName'> {
  fullname: string;
}

interface LoginResponse {
  accessToken: string;
  user: UserResponse;
}

const toUser = ({ fullname, ...user }: UserResponse): User => ({ ...user, fullName: fullname });

export const login = async (username: string, password: string): Promise<StoredSession> => {
  clearSession();
  const version = getSessionVersion();
  const response = await apiRequest<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  const session = { accessToken: response.accessToken, user: toUser(response.user) };
  if (getSessionVersion() === version) saveSession(session);
  return session;
};

// Shared promise also prevents duplicate startup requests under React StrictMode.
let restoring: Promise<void> | null = null;
export const restoreSession = (): Promise<void> => {
  restoring ??= (async () => {
    const version = getSessionVersion();
    if (!await refreshAccessToken()) return;
    const user = await apiRequest<UserResponse>('/api/v1/auth/me');
    const session = loadSession();
    if (session && getSessionVersion() === version) saveSession({ ...session, user: toUser(user) });
  })().catch((error) => {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }).finally(() => { restoring = null; });
  return restoring;
};

export const logout = async (): Promise<void> => {
  clearSession();
  // Only the server can delete an HttpOnly cookie. Propagate failures for retry.
  await apiRequest<void>('/api/v1/auth/logout', { method: 'POST' });
};
