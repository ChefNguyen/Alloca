import React, { createContext, useCallback, useContext, useEffect, useState, useSyncExternalStore } from 'react';
import type { Role, User } from '@/types/api';
import { loadSession, subscribeSession, removeLegacySession } from '@/lib/authStorage';
import { login as loginRequest, logout as logoutRequest, restoreSession } from '@/services/authApi';

interface AuthContextType {
  user: User | null;
  role: Role;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  unreadCount: number;
  setUnreadCount: React.Dispatch<React.SetStateAction<number>>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const session = useSyncExternalStore(subscribeSession, loadSession, () => null);
  const user = session?.user ?? null;
  const [initializing, setInitializing] = useState(true);
  const [sessionError, setSessionError] = useState<'restore' | 'logout' | null>(null);
  const [unreadCount, setUnreadCount] = useState(2);

  const login = useCallback(async (username: string, password: string) => {
    await loginRequest(username, password);
  }, []);

  const logout = useCallback(() => {
    setInitializing(true);
    setSessionError(null);
    logoutRequest()
      .catch(() => setSessionError('logout'))
      .finally(() => setInitializing(false));
  }, []);

  const retryRestore = () => {
    setInitializing(true);
    setSessionError(null);
    restoreSession()
      .catch(() => setSessionError('restore'))
      .finally(() => setInitializing(false));
  };

  useEffect(() => {
    let cancelled = false;
    removeLegacySession();
    restoreSession()
      .catch(() => { if (!cancelled) setSessionError('restore'); })
      .finally(() => { if (!cancelled) setInitializing(false); });
    return () => { cancelled = true; };
  }, []);

  // Delay routing until /refresh + /me finish, so reload preserves protected URLs.
  if (initializing) return <p role="status" className="p-6">Loading session…</p>;
  if (sessionError) return (
    <div role="alert" className="p-6">
      <p>{sessionError === 'logout' ? 'Sign out could not finish. Please retry.' : 'Cannot restore your session. Please retry.'}</p>
      <button type="button" onClick={sessionError === 'logout' ? logout : retryRestore}>Retry</button>
    </div>
  );

  return (
    <AuthContext.Provider value={{ user, role: user?.role ?? 'EMPLOYEE', isAuthenticated: !!user,
      login, logout, unreadCount, setUnreadCount }}>
      {children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
