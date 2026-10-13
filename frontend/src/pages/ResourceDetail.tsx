import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { mockResources, mockBookings } from '@/mock/data';
import { BookingModal } from '@/components/booking/BookingModal';

export const ResourceDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [isBookingOpen, setIsBookingOpen] = useState(false);

  const resource = mockResources.find((r) => r.id === id) || mockResources[0];
  const resourceBookings = mockBookings.filter((b) => b.resource.id === resource.id);

  const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

  return (
    <div className="flex flex-col w-full p-6 lg:p-8 space-y-6 bg-[#F0EDE8] min-h-screen">
      {/* Header with Back Button */}
      <div className="flex items-center justify-between pb-4 border-b border-[#E5E0D8]/60">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="w-9 h-9 rounded-[6px] bg-[#FAFAF8] shadow-card flex items-center justify-center hover:bg-white transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[20px] text-[#242424]">arrow_back</span>
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-light text-[#242424] font-editorial">{resource.name}</h1>
              <span
                className={`text-xs font-bold px-2 py-0.5 rounded-[4px] ${
                  resource.status === 'AVAILABLE'
                    ? 'bg-[#22C55E]/10 text-[#22C55E]'
                    : 'bg-[#F59E0B]/15 text-[#B45309]'
                }`}
              >
                {resource.status}
              </span>
            </div>
            <p className="text-xs text-[#8A8070] mt-0.5">
              {resource.zone?.building} · {resource.zone?.floor} · {resource.type}
            </p>
          </div>
        </div>

        <button
          disabled={resource.status === 'SUSPENDED'}
          onClick={() => setIsBookingOpen(true)}
          className={`px-4 py-2 rounded-[6px] font-semibold text-xs flex items-center gap-1.5 transition-all shadow-card cursor-pointer ${
            resource.status === 'SUSPENDED'
              ? 'bg-[#F0EDE8] text-[#8A8070] cursor-not-allowed'
              : 'bg-[#242424] text-white hover:bg-black'
          }`}
        >
          <span className="material-symbols-outlined text-[18px]">add_circle</span>
          <span>Book this Resource</span>
        </button>
      </div>

      {/* Main Details Grid */}
      <div className="grid grid-cols-12 gap-6 items-start">
        {/* Left Column (8 cols): Specifications & Operating Hours */}
        <div className="col-span-12 lg:col-span-8 space-y-6">
          {/* Card 1: Overview & Amenities */}
          <div className="bg-[#FAFAF8] p-6 rounded-[8px] shadow-card space-y-4 border-0">
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Resource Overview
            </span>
            <p className="text-xs text-[#242424]/90 leading-relaxed bg-[#F0EDE8]/60 p-4 rounded-[6px]">
              {resource.description}
            </p>

            <div className="grid grid-cols-3 gap-3">
              <div className="p-3.5 rounded-[6px] bg-[#F0EDE8]/60">
                <span className="text-[10px] font-semibold text-[#8A8070] uppercase block">Capacity</span>
                <span className="text-sm font-bold text-[#242424] mt-0.5 block">{resource.capacity} seats</span>
              </div>
              <div className="p-3.5 rounded-[6px] bg-[#F0EDE8]/60">
                <span className="text-[10px] font-semibold text-[#8A8070] uppercase block">Department Scope</span>
                <span className="text-sm font-bold text-[#242424] mt-0.5 block truncate">
                  {resource.department?.name || 'Shared'}
                </span>
              </div>
              <div className="p-3.5 rounded-[6px] bg-[#F0EDE8]/60">
                <span className="text-[10px] font-semibold text-[#8A8070] uppercase block">Alert Threshold</span>
                <span className="text-sm font-bold text-[#242424] mt-0.5 block">
                  {resource.utilizationThreshold}%
                </span>
              </div>
            </div>
          </div>

          {/* Card 2: Operating Schedule Windows */}
          <div className="bg-[#FAFAF8] p-6 rounded-[8px] shadow-card space-y-4 border-0">
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Weekly Operating Windows
            </span>
            <p className="text-xs text-[#8A8070]">
              Bookings must fall strictly within operating windows and follow 30-minute slot intervals.
            </p>

            <div className="divide-y divide-[#E5E0D8]/40 border border-[#E5E0D8]/60 rounded-[6px] overflow-hidden">
              {days.map((dayName, idx) => {
                const dayNum = (idx + 1) as any;
                const schedule = resource.schedules.find((s) => s.dayOfWeek === dayNum);
                return (
                  <div key={dayName} className="p-3 flex items-center justify-between text-xs hover:bg-[#F0EDE8]/40">
                    <span className="font-semibold text-[#242424]">{dayName}</span>
                    {schedule ? (
                      <span className="font-medium text-[#242424] bg-[#F0EDE8] px-2.5 py-1 rounded-[4px]">
                        {schedule.startTime} – {schedule.endTime}
                      </span>
                    ) : (
                      <span className="text-[#8A8070] font-medium bg-[#F0EDE8] px-2.5 py-1 rounded-[4px]">
                        Closed
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Column (4 cols): Active Bookings Timeline on this Resource */}
        <div className="col-span-12 lg:col-span-4 space-y-6">
          <div className="bg-[#FAFAF8] p-6 rounded-[8px] shadow-card space-y-4 border-0">
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Scheduled Reservations
            </span>

            {resourceBookings.length === 0 ? (
              <p className="text-xs text-[#8A8070] py-6 text-center">No active reservations recorded.</p>
            ) : (
              <div className="space-y-3">
                {resourceBookings.map((b) => (
                  <div
                    key={b.id}
                    className="p-3.5 rounded-[6px] bg-[#F0EDE8]/50 space-y-1.5"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-[#242424]">{b.purpose}</span>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-[4px] bg-[#22C55E]/10 text-[#22C55E]">
                        {b.status}
                      </span>
                    </div>
                    <div className="text-[11px] text-[#8A8070]">
                      <span>
                        {new Date(b.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                        {new Date(b.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                      <span className="block mt-0.5">Booked by: {b.user.fullName}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <BookingModal
        isOpen={isBookingOpen}
        onClose={() => setIsBookingOpen(false)}
        resources={mockResources}
        initialResourceId={resource.id}
        onBookingSuccess={() => {
          setIsBookingOpen(false);
          navigate('/my-bookings');
        }}
      />
    </div>
  );
};
