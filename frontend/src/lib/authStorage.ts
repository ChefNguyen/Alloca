import { User } from '@/types/api';

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  user: User;
}

const KEY = 'alloca.session';

// localStorage can throw (private mode, blocked site data), so every access is guarded.
export const loadSession = (): StoredSession | null => {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch {
    return null;
  }
};

export const saveSession = (session: StoredSession): void => {
  try {
    localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // Session stays in memory only; the user will have to sign in again after a reload.
  }
};

export const clearSession = (): void => {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // Nothing to clear.
  }
};

/** Fired when the session can no longer be refreshed, so the UI can return to the login page. */
export const SESSION_EXPIRED_EVENT = 'alloca:session-expired';
