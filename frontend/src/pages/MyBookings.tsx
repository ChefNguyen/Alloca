import React, { useState } from 'react';
import { useOutletContext, useNavigate } from 'react-router-dom';
import { Booking, BookingStatus } from '@/types/api';

export const MyBookings: React.FC = () => {
  const navigate = useNavigate();
  const { bookings: initialBookings, onOpenBookingModal } = useOutletContext<{
    bookings: Booking[];
    onOpenBookingModal: () => void;
  }>();

  const [bookings, setBookings] = useState<Booking[]>(initialBookings);
  const [filter, setFilter] = useState<'ALL' | BookingStatus>('ALL');
  const [cancellingBookingId, setCancellingBookingId] = useState<string | null>(null);
  const [feedbackMsg, setFeedbackMsg] = useState<string | null>(null);

  const filteredBookings = bookings.filter((b) => {
    if (filter === 'ALL') return true;
    return b.status === filter;
  });

  const handleCancelBooking = (bookingId: string) => {
    const target = bookings.find((b) => b.id === bookingId);
    if (!target) return;

    if (target.status !== 'CONFIRMED') {
      setFeedbackMsg('Only CONFIRMED bookings can be cancelled.');
      return;
    }

    setBookings((prev) =>
      prev.map((b) => (b.id === bookingId ? { ...b, status: 'CANCELLED' as BookingStatus } : b))
    );
    setCancellingBookingId(null);
    setFeedbackMsg(`Reservation for ${target.resource.name} was successfully cancelled.`);
    setTimeout(() => setFeedbackMsg(null), 4000);
  };

  return (
    <div className="flex flex-col w-full p-6 lg:p-8 space-y-6 bg-[#F0EDE8] min-h-screen">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 pb-4 border-b border-[#E5E0D8]/60">
        <div>
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block mb-1">
            Personal Allocations & Schedules
          </span>
          <h1 className="text-[48px] font-[300] text-[#242424] tracking-[0.06em] leading-none font-editorial">
            My bookings
          </h1>
        </div>

        <button
          onClick={onOpenBookingModal}
          className="px-4 py-2 rounded-[6px] bg-[#242424] text-white text-xs font-semibold hover:bg-[#2A2A2A] shadow-card transition-all cursor-pointer"
        >
          + New Reservation
        </button>
      </div>

      {feedbackMsg && (
        <div className="p-3 rounded-[6px] bg-amber-50 border border-amber-300 text-amber-900 text-xs">
          {feedbackMsg}
        </div>
      )}

      {/* Segmented Filter Control (Squarer rounded-[6px]) */}
      <div className="flex items-center gap-1.5 p-1 bg-[#FAFAF8] rounded-[6px] self-start shadow-card">
        {(['ALL', 'CONFIRMED', 'COMPLETED', 'CANCELLED'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setFilter(tab)}
            className={`px-3.5 py-1.5 rounded-[4px] text-xs font-semibold transition-all cursor-pointer ${
              filter === tab
                ? 'bg-[#242424] text-white shadow-xs'
                : 'text-[#8A8070] hover:text-[#242424]'
            }`}
          >
            {tab === 'ALL'
              ? 'All'
              : tab === 'CONFIRMED'
              ? 'Upcoming'
              : tab === 'COMPLETED'
              ? 'Completed'
              : 'Cancelled'}
          </button>
        ))}
      </div>

      {/* Bookings Card List */}
      <div className="bg-[#FAFAF8] rounded-[8px] shadow-card p-6 overflow-hidden border-0">
        {filteredBookings.length === 0 ? (
          <div className="py-12 text-center text-[#8A8070] text-xs">No reservations found.</div>
        ) : (
          <div className="divide-y divide-[#E5E0D8]/50">
            {filteredBookings.map((b) => {
              const start = new Date(b.startTime);
              const end = new Date(b.endTime);

              return (
                <div
                  key={b.id}
                  className="py-5 first:pt-0 last:pb-0 flex flex-col md:flex-row md:items-center justify-between gap-4"
                >
                  <div className="flex items-start gap-4">
                    <div className="w-14 h-14 rounded-[6px] bg-[#F0EDE8] flex flex-col items-center justify-center shrink-0">
                      <span className="text-[10px] uppercase font-bold text-[#8A8070]">
                        {start.toLocaleString('en-US', { month: 'short' })}
                      </span>
                      <span className="text-xl font-bold text-[#242424] leading-tight">
                        {start.getDate()}
                      </span>
                    </div>

                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-[#242424]">{b.purpose}</span>
                        <span
                          className={`text-[10px] font-bold px-2 py-0.5 rounded-[4px] ${
                            b.status === 'CONFIRMED'
                              ? 'bg-[#22C55E]/10 text-[#22C55E]'
                              : b.status === 'COMPLETED'
                              ? 'bg-[#D4CFC9]/40 text-[#242424]'
                              : 'bg-neutral-200 text-neutral-600'
                          }`}
                        >
                          {b.status}
                        </span>
                      </div>

                      <p className="text-xs text-[#8A8070]">
                        <span className="font-semibold text-[#242424]">{b.resource.name}</span>
                        {' · '}
                        <span>
                          {start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} –{' '}
                          {end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => navigate(`/resources/${b.resource.id}`)}
                      className="px-3.5 py-1.5 rounded-[6px] bg-[#F0EDE8] hover:bg-[#EAE5DC] text-xs font-semibold text-[#242424] transition-colors cursor-pointer"
                    >
                      Specifications
                    </button>
                    {b.status === 'CONFIRMED' && (
                      <button
                        onClick={() => setCancellingBookingId(b.id)}
                        className="px-3.5 py-1.5 rounded-[6px] text-xs font-semibold text-[#8A8070] hover:text-red-700 hover:bg-red-50 transition-colors cursor-pointer"
                      >
                        Cancel
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Cancel Dialog */}
      {cancellingBookingId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="w-full max-w-md bg-[#FAFAF8] rounded-[8px] shadow-hero p-6 space-y-4">
            <h3 className="text-base font-bold text-[#242424]">Confirm Cancellation</h3>
            <p className="text-xs text-[#8A8070] leading-relaxed">
              Are you sure you want to cancel this booking? This will immediately free up the slot for others.
            </p>
            <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#E5E0D8]/60">
              <button
                onClick={() => setCancellingBookingId(null)}
                className="px-4 py-2 rounded-[6px] text-xs font-semibold text-[#8A8070] hover:text-[#242424] cursor-pointer"
              >
                Keep Booking
              </button>
              <button
                onClick={() => handleCancelBooking(cancellingBookingId)}
                className="px-4 py-2 rounded-[6px] bg-[#242424] text-white text-xs font-bold hover:bg-black cursor-pointer"
              >
                Yes, Cancel Reservation
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
