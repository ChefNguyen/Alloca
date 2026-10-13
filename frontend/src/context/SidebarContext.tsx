import React, { createContext, useContext, useState } from 'react';

interface SidebarContextType {
  isExpanded: boolean;
  toggleCollapse: () => void;
  sidebarWidth: number;
}

const SidebarContext = createContext<SidebarContextType | undefined>(undefined);

export const SidebarProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Default state: always Expanded (240px)
  const [isExpanded, setIsExpanded] = useState<boolean>(() => {
    const saved = localStorage.getItem('alloca_sidebar_expanded');
    return saved !== null ? saved === 'true' : true;
  });

  const toggleCollapse = () => {
    setIsExpanded((prev) => {
      const next = !prev;
      localStorage.setItem('alloca_sidebar_expanded', String(next));
      return next;
    });
  };

  const sidebarWidth = isExpanded ? 240 : 56;

  return (
    <SidebarContext.Provider
      value={{
        isExpanded,
        toggleCollapse,
        sidebarWidth,
      }}
    >
      {children}
    </SidebarContext.Provider>
  );
};

export const useSidebar = (): SidebarContextType => {
  const context = useContext(SidebarContext);
  if (!context) {
    throw new Error('useSidebar must be used within a SidebarProvider');
  }
  return context;
};
