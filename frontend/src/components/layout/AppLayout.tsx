import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { BookingModal } from '@/components/booking/BookingModal';
import { useSidebar } from '@/context/SidebarContext';
import { mockResources, mockBookings } from '@/mock/data';
import { Booking } from '@/types/api';

export const AppLayout: React.FC = () => {
  const [isBookingOpen, setIsBookingOpen] = useState<boolean>(false);
  const [bookings, setBookings] = useState<Booking[]>(mockBookings);
  const { sidebarWidth } = useSidebar();

  const handleBookingSuccess = (newBooking: Booking) => {
    setBookings((prev) => [newBooking, ...prev]);
  };

  return (
    <div className="min-h-screen bg-[#F0EDE8] text-[#1A1A1A] flex flex-col font-sans selection:bg-[#E8622A] selection:text-white">
      {/* Supabase-style Sidebar */}
      <Sidebar />

      {/* Main Content Area with Dynamic Padding */}
      <div
        style={{ paddingLeft: `${sidebarWidth}px` }}
        className="min-h-screen flex flex-col transition-all duration-200"
      >
        {/* Top Header */}
        <Header onOpenBookingModal={() => setIsBookingOpen(true)} />

        {/* Working Canvas */}
        <main className="flex-1 w-full pt-16 bg-[#F0EDE8]">
          <Outlet context={{ bookings, onOpenBookingModal: () => setIsBookingOpen(true) }} />
        </main>
      </div>

      {/* Global Booking Modal */}
      <BookingModal
        isOpen={isBookingOpen}
        onClose={() => setIsBookingOpen(false)}
        resources={mockResources}
        onBookingSuccess={handleBookingSuccess}
      />
    </div>
  );
};
