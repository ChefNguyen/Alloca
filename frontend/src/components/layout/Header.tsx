import React from 'react';
import { useSidebar } from '@/context/SidebarContext';

interface HeaderProps {
  onOpenBookingModal: () => void;
}

export const Header: React.FC<HeaderProps> = ({ onOpenBookingModal }) => {
  const { sidebarWidth } = useSidebar();

  return (
    <header
      style={{ left: `${sidebarWidth}px` }}
      className="fixed top-0 right-0 h-16 bg-[#F0EDE8]/90 backdrop-blur-md z-40 px-8 flex items-center justify-between gap-6 border-b border-[#E5E0D8]/60 transition-all duration-200"
    >
      {/* Left: Location Breadcrumb */}
      <div className="flex items-center gap-3">
        <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em]">
          Location
        </span>
        <span className="text-xs font-medium text-[#242424] bg-[#FAFAF8] px-3.5 py-1.5 rounded-[4px] shadow-sm">
          Acme Space · West Wing Level 03
        </span>
      </div>

      {/* Center: Search Field */}
      <div className="flex-1 max-w-md relative">
        <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-[18px] text-[#8A8070]">
          search
        </span>
        <input
          className="w-full h-9 pl-10 pr-4 rounded-[6px] bg-[#FAFAF8] text-xs text-[#242424] placeholder:text-[#8A8070] shadow-sm focus:outline-none focus:ring-1 focus:ring-[#8A8070] transition-all border-0"
          placeholder="Search desks, private booths, studios..."
          type="text"
        />
      </div>

      {/* Right: + Reserve Space CTA Button only (NO avatar, NO bell) */}
      <div className="flex items-center gap-4">
        <button
          onClick={onOpenBookingModal}
          className="px-5 py-2 rounded-[20px] bg-[#D97757] text-white text-xs font-[500] hover:bg-[#C56546] shadow-sm hover:shadow transition-all cursor-pointer active:scale-98 flex items-center gap-1.5"
        >
          <span className="material-symbols-outlined text-[16px]">add</span>
          <span>Reserve Space</span>
        </button>
      </div>
    </header>
  );
};
