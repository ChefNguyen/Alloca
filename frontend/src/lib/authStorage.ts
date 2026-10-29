import type { User } from '../types/api.ts';

export interface StoredSession {
  accessToken: string;
  user: User | null;
}

// In-memory store observed by React. Never persist credentials in Web Storage.
let session: StoredSession | null = null;
let version = 0;
const listeners = new Set<() => void>();
export const loadSession = (): StoredSession | null => session;
export const getSessionVersion = (): number => version;
export const subscribeSession = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
};

export const saveSession = (next: StoredSession): void => {
  session = next;
  listeners.forEach((listener) => listener());
};

export const removeLegacySession = (): void => {
  try { localStorage.removeItem('alloca.session'); } catch { /* Storage may be disabled. */ }
};

export const clearSession = (): void => {
  version += 1; // Prevent an older in-flight refresh from restoring a logged-out session.
  session = null;
  removeLegacySession();
  listeners.forEach((listener) => listener());
};
