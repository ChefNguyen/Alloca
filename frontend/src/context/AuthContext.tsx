import React, { createContext, useContext, useState } from 'react';
import { Role, User } from '@/types/api';
import { mockUsers } from '@/mock/data';

interface AuthContextType {
  user: User | null;
  role: Role;
  isAuthenticated: boolean;
  login: (username: string, role?: Role) => void;
  logout: () => void;
  switchRole: (role: Role) => void;
  unreadCount: number;
  setUnreadCount: React.Dispatch<React.SetStateAction<number>>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Default to Admin Morgan Vance to match Stitch prototype initially
  const [user, setUser] = useState<User | null>(mockUsers[0]);
  const [unreadCount, setUnreadCount] = useState<number>(2);

  const login = (username: string, role?: Role) => {
    const found = mockUsers.find((u) => u.username === username || (role && u.role === role));
    setUser(found || mockUsers[0]);
  };

  const logout = () => {
    setUser(null);
  };

  const switchRole = (newRole: Role) => {
    const matching = mockUsers.find((u) => u.role === newRole) || {
      ...mockUsers[0],
      role: newRole,
    };
    setUser(matching);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        role: user?.role || 'EMPLOYEE',
        isAuthenticated: !!user,
        login,
        logout,
        switchRole,
        unreadCount,
        setUnreadCount,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
