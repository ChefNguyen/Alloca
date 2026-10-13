import React, { useState } from 'react';
import { Resource, Booking } from '@/types/api';
import { useAuth } from '@/context/AuthContext';
import { mockBookings } from '@/mock/data';

interface BookingModalProps {
  isOpen: boolean;
  onClose: () => void;
  resources: Resource[];
  initialResourceId?: string;
  onBookingSuccess: (newBooking: Booking) => void;
}

export const BookingModal: React.FC<BookingModalProps> = ({
  isOpen,
  onClose,
  resources,
  initialResourceId,
  onBookingSuccess,
}) => {
  const { user } = useAuth();

  const [resourceId, setResourceId] = useState<string>(
    initialResourceId || resources[0]?.id || ''
  );
  const [date, setDate] = useState<string>('2026-10-14');
  const [startTime, setStartTime] = useState<string>('09:00');
  const [endTime, setEndTime] = useState<string>('10:30');
  const [purpose, setPurpose] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<boolean>(false);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const selectedResource = resources.find((r) => r.id === resourceId);
    if (!selectedResource) {
      setError('Please select a valid resource.');
      return;
    }

    if (selectedResource.status === 'SUSPENDED') {
      setError('This resource is currently suspended for maintenance.');
      return;
    }

    const [startHour, startMin] = startTime.split(':').map(Number);
    const [endHour, endMin] = endTime.split(':').map(Number);

    if (startMin % 30 !== 0 || endMin % 30 !== 0) {
      setError('Start and end times must align to 30-minute boundaries (:00 or :30).');
      return;
    }

    const durationMinutes = endHour * 60 + endMin - (startHour * 60 + startMin);
    if (durationMinutes < 30) {
      setError('Minimum booking duration is 30 minutes.');
      return;
    }
    if (durationMinutes > 480) {
      setError('Maximum booking duration is 8 hours (480 minutes).');
      return;
    }

    const newStart = new Date(`${date}T${startTime}:00Z`).toISOString();
    const newEnd = new Date(`${date}T${endTime}:00Z`).toISOString();

    const hasConflict = mockBookings.some((b) => {
      if (b.resource.id !== resourceId || b.status !== 'CONFIRMED') return false;
      const existingStart = new Date(b.startTime).getTime();
      const existingEnd = new Date(b.endTime).getTime();
      const reqStart = new Date(newStart).getTime();
      const reqEnd = new Date(newEnd).getTime();
      return reqStart < existingEnd && reqEnd > existingStart;
    });

    if (hasConflict) {
      setError(
        '409 Conflict: The selected slot overlaps with an existing confirmed reservation on this resource.'
      );
      return;
    }

    const newBooking: Booking = {
      id: `bk-${Date.now()}`,
      resource: selectedResource,
      user: {
        id: user?.id || 'user-1',
        username: user?.username || 'user',
        fullName: user?.fullName || 'Current User',
        role: user?.role || 'EMPLOYEE',
      },
      createdBy: {
        id: user?.id || 'user-1',
        username: user?.username || 'user',
        fullName: user?.fullName || 'Current User',
        role: user?.role || 'EMPLOYEE',
      },
      startTime: newStart,
      endTime: newEnd,
      status: 'CONFIRMED',
      purpose: purpose || 'Workspace Reservation',
      createdAt: new Date().toISOString(),
    };

    setSuccess(true);
    setTimeout(() => {
      onBookingSuccess(newBooking);
      setSuccess(false);
      onClose();
    }, 900);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="w-full max-w-lg bg-[#FAFAF8] rounded-[8px] shadow-hero overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-[#E5E0D8]/60 flex items-center justify-between bg-[#F0EDE8]/60">
          <div>
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Reservation Terminal
            </span>
            <h2 className="text-base font-bold text-[#242424] font-editorial">
              Book a Workspace Resource
            </h2>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-[4px] flex items-center justify-center text-[#8A8070] hover:text-[#242424] hover:bg-[#EAE5DC] transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 rounded-[6px] bg-red-50 border border-red-200 text-red-800 text-xs flex items-start gap-2">
              <span className="material-symbols-outlined text-[18px] shrink-0 text-red-600">error</span>
              <span className="leading-relaxed">{error}</span>
            </div>
          )}

          {success && (
            <div className="p-3 rounded-[6px] bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px] text-[#22C55E]">check_circle</span>
              <span>Reservation confirmed! Syncing timeline...</span>
            </div>
          )}

          {/* Resource Selector */}
          <div>
            <label className="block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1">
              Resource
            </label>
            <select
              value={resourceId}
              onChange={(e) => setResourceId(e.target.value)}
              className="w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0"
            >
              {resources.map((r) => (
                <option key={r.id} value={r.id} disabled={r.status === 'SUSPENDED'}>
                  {r.name} ({r.type} · Cap: {r.capacity}){' '}
                  {r.status === 'SUSPENDED' ? '— [SUSPENDED]' : ''}
                </option>
              ))}
            </select>
          </div>

          {/* Date Picker */}
          <div>
            <label className="block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1">
              Date (Within 30 Days)
            </label>
            <input
              type="date"
              value={date}
              min="2026-10-13"
              max="2026-11-12"
              onChange={(e) => setDate(e.target.value)}
              className="w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0"
              required
            />
          </div>

          {/* Start and End Time (30-min Grid) */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1">
                Start Time
              </label>
              <select
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
                className="w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0"
              >
                {[
                  '08:00', '08:30', '09:00', '09:30', '10:00', '10:30',
                  '11:00', '11:30', '12:00', '12:30', '13:00', '13:30',
                  '14:00', '14:30', '15:00', '15:30', '16:00', '16:30',
                  '17:00', '17:30'
                ].map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1">
                End Time
              </label>
              <select
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
                className="w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0"
              >
                {[
                  '08:30', '09:00', '09:30', '10:00', '10:30',
                  '11:00', '11:30', '12:00', '12:30', '13:00', '13:30',
                  '14:00', '14:30', '15:00', '15:30', '16:00', '16:30',
                  '17:00', '17:30', '18:00'
                ].map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Purpose */}
          <div>
            <label className="block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1">
              Purpose / Meeting Subject
            </label>
            <input
              type="text"
              placeholder="e.g. Architectural Planning Sync"
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              className="w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0"
            />
          </div>

          {/* Modal Footer */}
          <div className="pt-3 border-t border-[#E5E0D8]/60 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-[6px] text-[#8A8070] hover:text-[#242424] hover:bg-[#F0EDE8] font-semibold text-xs transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={success}
              className="px-5 py-2.5 rounded-[6px] bg-[#E8622A] text-white font-semibold text-xs hover:opacity-95 shadow-card transition-all cursor-pointer"
            >
              Confirm Allocation
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
