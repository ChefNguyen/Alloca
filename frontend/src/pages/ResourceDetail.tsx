import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { BookingModal } from '@/components/booking/BookingModal';
import { ApiError } from '@/lib/api';
import { WEEKDAYS } from '@/lib/weekdays';
import { getResource } from '@/services/resourceApi';
import { ResourceDetail as ResourceDetailModel } from '@/types/api';

// The resource id a response belongs to; while it differs from the route id, the page is loading.
interface DetailResult {
  id: string;
  resource: ResourceDetailModel | null;
  error: string | null;
}

/** "08:00:00" -> "08:00" */
const formatTime = (time: string) => time.slice(0, 5);

export const ResourceDetail: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [isBookingOpen, setIsBookingOpen] = useState(false);
  const [result, setResult] = useState<DetailResult | null>(null);

  useEffect(() => {
    let cancelled = false;
    getResource(id)
      .then((loaded) => {
        if (!cancelled) setResult({ id, resource: loaded, error: null });
      })
      .catch((error) => {
        if (cancelled) return;
        // 404 also covers DELETED resources and ids that are not UUIDs (400).
        const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400);
        const message = notFound
          ? 'This resource does not exist or has been removed.'
          : error instanceof ApiError
            ? error.message
            : 'Could not load this resource.';
        setResult({ id, resource: null, error: message });
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (result?.id !== id || !result.resource) {
    const error = result?.id === id ? result.error : null;
    return (
      <div className="flex flex-col w-full p-6 lg:p-8 space-y-6 bg-[#F0EDE8] min-h-screen">
        <div className="bg-[#FAFAF8] p-8 rounded-[8px] shadow-card text-center space-y-4">
          <p className={`text-sm ${error ? 'text-[#C62828] font-semibold' : 'text-[#8A8070]'}`} role={error ? 'alert' : 'status'}>
            {error ?? 'Loading resource…'}
          </p>
          {error && (
            <button
              onClick={() => navigate('/find-resource')}
              className="px-4 py-2 rounded-[6px] bg-[#242424] text-white text-xs font-semibold hover:bg-black cursor-pointer"
            >
              Back to resource list
            </button>
          )}
        </div>
      </div>
    );
  }

  const resource = result.resource;
  const bookable = resource.status === 'AVAILABLE';

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
              {resource.zone
                ? `${resource.zone.building} · ${resource.zone.floor} · ${resource.zone.name} · `
                : ''}
              {resource.type}
            </p>
          </div>
        </div>

        <button
          disabled={!bookable}
          title={bookable ? undefined : 'This resource is suspended and cannot be booked'}
          onClick={() => setIsBookingOpen(true)}
          className={`px-4 py-2 rounded-[6px] font-semibold text-xs flex items-center gap-1.5 transition-all shadow-card cursor-pointer ${
            !bookable
              ? 'bg-[#F0EDE8] text-[#8A8070] cursor-not-allowed'
              : 'bg-[#242424] text-white hover:bg-black'
          }`}
        >
          <span className="material-symbols-outlined text-[18px]">add_circle</span>
          <span>Book this Resource</span>
        </button>
      </div>

      {!bookable && (
        <div role="status" className="p-4 rounded-[6px] bg-[#F59E0B]/15 text-[#B45309] text-xs font-semibold">
          This resource is temporarily suspended. Existing reservations are kept, but new bookings are not accepted.
        </div>
      )}

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
              {resource.description || 'No description provided.'}
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
              {WEEKDAYS.map((day) => {
                // A day can have several windows, e.g. a lunch break; the API returns them sorted.
                const windows = resource.schedules.filter((s) => s.dayOfWeek === day.key);
                return (
                  <div key={day.key} className="p-3 flex items-center justify-between text-xs hover:bg-[#F0EDE8]/40">
                    <span className="font-semibold text-[#242424]">{day.label}</span>
                    {windows.length > 0 ? (
                      <span className="flex flex-wrap justify-end gap-1.5">
                        {windows.map((w) => (
                          <span key={w.id} className="font-medium text-[#242424] bg-[#F0EDE8] px-2.5 py-1 rounded-[4px]">
                            {formatTime(w.startTime)} – {formatTime(w.endTime)}
                          </span>
                        ))}
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

            <p className="text-xs text-[#8A8070] py-6 text-center">
              Reservations and free slots for this resource will appear here once booking is available.
            </p>
          </div>
        </div>
      </div>

      <BookingModal
        isOpen={isBookingOpen}
        onClose={() => setIsBookingOpen(false)}
        resources={[resource]}
        initialResourceId={resource.id}
        onBookingSuccess={() => {
          setIsBookingOpen(false);
          navigate('/my-bookings');
        }}
      />
    </div>
  );
};
