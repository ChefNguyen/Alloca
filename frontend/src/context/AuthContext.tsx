import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { Role, User } from '@/types/api';
import { clearSession, loadSession, saveSession, SESSION_EXPIRED_EVENT } from '@/lib/authStorage';
import { login as loginRequest } from '@/services/authApi';

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
  const [user, setUser] = useState<User | null>(() => loadSession()?.user ?? null);
  const [unreadCount, setUnreadCount] = useState<number>(2);

  const login = useCallback(async (username: string, password: string) => {
    const session = await loginRequest(username, password);
    saveSession(session);
    setUser(session.user);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  // The API client fires this when a 401 could not be fixed by refreshing the token.
  useEffect(() => {
    window.addEventListener(SESSION_EXPIRED_EVENT, logout);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, logout);
  }, [logout]);

  return (
    <AuthContext.Provider
      value={{
        user,
        role: user?.role || 'EMPLOYEE',
        isAuthenticated: !!user,
        login,
        logout,
        unreadCount,
        setUnreadCount,
      }}
    >
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
