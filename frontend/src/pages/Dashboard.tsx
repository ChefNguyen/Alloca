import React, { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { mockResources } from '@/mock/data';
import { Booking, Resource } from '@/types/api';

export const Dashboard: React.FC = () => {
  const { onOpenBookingModal } = useOutletContext<{
    bookings: Booking[];
    onOpenBookingModal: () => void;
  }>();

  const [selectedSpaceId, setSelectedSpaceId] = useState<string>('res-301');
  const [selectedDuration, setSelectedDuration] = useState<string>('1h');
  const [quickDate, setQuickDate] = useState<string>('Today');
  const [quickTime, setQuickTime] = useState<string>('14:00');
  const [filterType, setFilterType] = useState<string>('ALL');

  const selectedSpace =
    mockResources.find((r) => r.id === selectedSpaceId) || mockResources[0];

  const filteredResources = mockResources.filter((r) => {
    if (filterType === 'ALL') return true;
    if (filterType === 'POD') {
      return (
        r.name.toLowerCase().includes('pod') ||
        r.name.toLowerCase().includes('booth')
      );
    }
    return r.type === filterType;
  });

  const handleBookNow = (r: Resource, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedSpaceId(r.id);
    onOpenBookingModal();
  };

  const formatResourceType = (type: string) => {
    switch (type) {
      case 'MEETING_ROOM':
        return 'Meeting Room';
      case 'POD':
        return 'Phone Pod';
      case 'DESK':
        return 'Work Desk';
      case 'EQUIPMENT':
        return 'Media Studio';
      default:
        return type.replace(/_/g, ' ');
    }
  };

  const getZoneInfo = (resource: Resource) => {
    const floor = resource.zone?.floor ? resource.zone.floor.replace('Level ', 'L') : 'L3';
    return `West Wing · ${floor}`;
  };

  // Status Badges - restrained pastel pill with status dot
  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'AVAILABLE':
        return {
          bg: 'bg-[#E8F5EE]',
          text: 'text-[#2E7D32]',
          dot: 'bg-[#4CAF7D]',
          label: 'Available',
        };
      case 'PARTIAL':
        return {
          bg: 'bg-[#FFF3E0]',
          text: 'text-[#E65100]',
          dot: 'bg-[#E8A020]',
          label: 'Partial',
        };
      case 'BOOKED':
      case 'OCCUPIED':
      default:
        return {
          bg: 'bg-[#FFEBEE]',
          text: 'text-[#C62828]',
          dot: 'bg-[#E05252]',
          label: 'Booked',
        };
    }
  };

  // 6. SPACE TELEMETRY DATA (Replaced TEMP & ACOUSTICS)
  const getSpaceTelemetry = (spaceId: string) => {
    const telemetryData: Record<
      string,
      { nextBooking: string; todayCount: number; lastBooked: string }
    > = {
      'res-301': {
        nextBooking: '14:00 – 15:30 · Sarah Chen',
        todayCount: 4,
        lastBooked: '2 hours ago',
      },
      'res-302': {
        nextBooking: '16:00 – 17:00 · David Miller',
        todayCount: 2,
        lastBooked: '45 mins ago',
      },
      'res-pod-a': {
        nextBooking: '15:15 – 16:15 · Alex Kim',
        todayCount: 5,
        lastBooked: '10 mins ago',
      },
      'res-pod-b': {
        nextBooking: 'Now – 17:00 · Lisa Wang',
        todayCount: 3,
        lastBooked: 'Just now',
      },
      'res-studio': {
        nextBooking: '16:30 – 18:00 · Creative Team',
        todayCount: 2,
        lastBooked: '3 hours ago',
      },
      'res-desk-12': {
        nextBooking: '14:30 – 18:00 · Elena Rostova',
        todayCount: 1,
        lastBooked: '1 hour ago',
      },
      'res-desk-13': {
        nextBooking: 'Tomorrow 09:00 · Open Desk',
        todayCount: 0,
        lastBooked: 'Yesterday',
      },
      'res-desk-14': {
        nextBooking: 'All day · Dev Team Sprint',
        todayCount: 2,
        lastBooked: '30 mins ago',
      },
    };
    return (
      telemetryData[spaceId] || {
        nextBooking: '15:00 – 16:00 · Team Sync',
        todayCount: 3,
        lastBooked: '2 hours ago',
      }
    );
  };

  const currentTelemetry = getSpaceTelemetry(selectedSpace.id);

  return (
    <div className="w-full min-h-screen bg-[#F0EDE8] px-8 py-6 space-y-6">
      {/* Top Editorial Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 pb-4 border-b border-[#E5E0D8]/60">
        <div>
          <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em] block mb-1">
            Spatial Management & Architectural Grid
          </span>
          <h1 className="text-[48px] font-[300] text-[#242424] tracking-[0.06em] leading-none font-editorial">
            Office space
          </h1>
        </div>

        {/* Date & Location Badges */}
        <div className="flex items-center gap-3 text-[13px] font-[400] text-[#6B6560]">
          <span className="bg-[#FAFAF8] px-3.5 py-1.5 rounded-[4px] shadow-sm text-[#242424] font-medium border-0">
            Tuesday, Oct 14 · 10:45 AM
          </span>
          <span className="bg-[#FAFAF8] px-3.5 py-1.5 rounded-[4px] shadow-sm text-[#242424] font-medium border-0">
            West Wing · Level 03
          </span>
        </div>
      </div>

      {/* Asymmetric 3-Panel Layout: LEFT (w-[320px] / ~28%), CENTER (flex-1 / ~44%), RIGHT (w-[320px] / ~28%) - Section Gap 24px (gap-6) */}
      <div className="flex flex-col xl:flex-row items-stretch gap-6 w-full">
        {/* ========================================================= */}
        {/* LEFT PANEL: Booking form card, stacked metric cards       */}
        {/* ========================================================= */}
        <div className="w-full xl:w-[320px] 2xl:w-[340px] shrink-0 flex flex-col gap-6">
          {/* 1. Quick Booking Form Card (Card padding 16px: p-4 sm:p-6) */}
          <div className="bg-[#FAFAF8] rounded-[8px] p-4 sm:p-5 shadow-sm space-y-4 border-0">
            <div className="flex items-center justify-between">
              <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em]">
                Quick Reservation
              </span>
              <span className="w-2 h-2 rounded-[2px] bg-[#A8A29E]" />
            </div>

            <div className="space-y-3.5">
              {/* Space Selector */}
              <div>
                <label className="block text-[11px] font-[400] text-[#6B6560] mb-1">
                  Selected Space
                </label>
                <select
                  value={selectedSpaceId}
                  onChange={(e) => setSelectedSpaceId(e.target.value)}
                  className="w-full h-10 px-3 rounded-[6px] bg-[#F0EDE8]/60 text-xs font-semibold text-[#242424] border-0 focus:ring-1 focus:ring-[#8A8070] focus:outline-none"
                >
                  {mockResources.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name} ({r.type} · Cap {r.capacity})
                    </option>
                  ))}
                </select>
              </div>

              {/* Day & Time Row */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-[400] text-[#6B6560] mb-1">
                    Day
                  </label>
                  <select
                    value={quickDate}
                    onChange={(e) => setQuickDate(e.target.value)}
                    className="w-full h-10 px-2.5 rounded-[6px] bg-[#F0EDE8]/60 text-xs font-medium text-[#242424] border-0 focus:ring-1 focus:ring-[#8A8070] focus:outline-none"
                  >
                    <option value="Today">Today (Oct 14)</option>
                    <option value="Tomorrow">Tomorrow (Oct 15)</option>
                    <option value="Thursday">Thursday (Oct 16)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] font-[400] text-[#6B6560] mb-1">
                    Time Window
                  </label>
                  <select
                    value={quickTime}
                    onChange={(e) => setQuickTime(e.target.value)}
                    className="w-full h-10 px-2.5 rounded-[6px] bg-[#F0EDE8]/60 text-xs font-medium text-[#242424] border-0 focus:ring-1 focus:ring-[#8A8070] focus:outline-none"
                  >
                    <option value="09:00">09:00 AM</option>
                    <option value="11:00">11:00 AM</option>
                    <option value="14:00">02:00 PM</option>
                    <option value="16:00">04:00 PM</option>
                  </select>
                </div>
              </div>

              {/* Duration Buttons */}
              <div>
                <label className="block text-[11px] font-[400] text-[#6B6560] mb-1.5">
                  Duration
                </label>
                <div className="grid grid-cols-4 gap-2">
                  {['30m', '1h', '2h', '4h'].map((d) => (
                    <button
                      key={d}
                      type="button"
                      onClick={() => setSelectedDuration(d)}
                      className={`py-1.5 rounded-[6px] text-xs font-semibold transition-all cursor-pointer ${
                        selectedDuration === d
                          ? 'bg-[#242424] text-white shadow-xs'
                          : 'bg-[#F0EDE8]/60 text-[#6B6560] hover:text-[#242424]'
                      }`}
                    >
                      {d}
                    </button>
                  ))}
                </div>
              </div>

              {/* 2. CONFIRM ALLOCATION BUTTON: fit-content, centered, px-8, pill shape, #D97757, 500 weight */}
              <div className="flex justify-center pt-2">
                <button
                  type="button"
                  onClick={onOpenBookingModal}
                  className="w-auto px-8 py-2.5 rounded-full bg-[#D97757] text-white text-xs font-[500] hover:bg-[#C56546] shadow-sm hover:shadow transition-all cursor-pointer"
                >
                  Confirm Allocation
                </button>
              </div>
            </div>
          </div>

          {/* 2. Stacked Metric Card: Available Spaces (p-4 sm:p-5) */}
          <div className="bg-[#FAFAF8] rounded-[8px] p-4 sm:p-5 shadow-sm space-y-3 border-0">
            <div className="flex items-center justify-between">
              <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em]">
                Available Spaces
              </span>
              <span className="text-[13px] font-[400] text-[#6B6560]">70% ready</span>
            </div>

            <div className="flex items-baseline gap-2">
              <span className="text-[56px] font-bold text-[#242424] leading-none tracking-tight">
                84
              </span>
              <span className="text-[13px] font-[400] text-[#6B6560]">/ 120 workspaces</span>
            </div>

            {/* Warm Gray Progress Bar: #D4CFC9 */}
            <div className="pt-2">
              <div className="w-full h-2 bg-[#F0EDE8] rounded-[4px] overflow-hidden flex">
                <div
                  className="h-full bg-[#D4CFC9] rounded-[4px] transition-all duration-500"
                  style={{ width: '70%' }}
                />
              </div>
              <div className="flex items-center justify-between text-[13px] font-[400] text-[#6B6560] mt-2">
                <span>Free: 84 units</span>
                <span>Active Holds: 36 units</span>
              </div>
            </div>
          </div>

          {/* 3. Stacked Metric Card: Utilization Rate (p-4 sm:p-5) */}
          <div className="bg-[#FAFAF8] rounded-[8px] p-4 sm:p-5 shadow-sm flex items-center justify-between border-0">
            <div className="space-y-2">
              <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em] block">
                Utilization Rate
              </span>
              <div className="text-[52px] font-bold text-[#242424] leading-none tracking-tight">
                72%
              </div>
              <p className="text-[13px] font-[400] text-[#6B6560] flex items-center gap-1">
                <span>↑ +14%</span>
                <span>vs past 7 days</span>
              </p>
            </div>

            {/* Donut Ring Chart with Warm Brown #A89880 */}
            <div className="w-20 h-20 relative flex items-center justify-center shrink-0">
              <svg className="w-full h-full -rotate-90" viewBox="0 0 36 36">
                <path
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  fill="none"
                  stroke="#F0EDE8"
                  strokeWidth="3.5"
                />
                <path
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  fill="none"
                  stroke="#A89880"
                  strokeDasharray="72, 100"
                  strokeLinecap="round"
                  strokeWidth="3.8"
                />
              </svg>
              <span className="absolute text-[11px] font-bold text-[#242424]">L03</span>
            </div>
          </div>
        </div>

        {/* ========================================================= */}
        {/* CENTER PANEL: REFINED RESOURCE CARD DIRECTORY             */}
        {/* ========================================================= */}
        <div className="w-full xl:flex-1 min-w-0 shrink-0 flex flex-col">
          <div className="bg-[#FAFAF8] rounded-[8px] p-4 sm:p-5 shadow-sm h-full flex flex-col justify-between border-0 min-h-[660px] space-y-4">
            {/* Header: Title and Type Filter Segmented Control */}
            <div>
              <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
                <div>
                  <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em] block">
                    Workplace Directory
                  </span>
                  <h2 className="text-xl font-light text-[#242424] tracking-wide mt-0.5 font-editorial">
                    Available Workspaces
                  </h2>
                </div>

                <span className="text-[12px] font-[500] text-[#6B6560] bg-[#F0EDE8] px-2.5 py-1 rounded-[6px]">
                  {filteredResources.length} {filteredResources.length === 1 ? 'space' : 'spaces'}
                </span>
              </div>

              {/* Segmented Filter Control: Restrained neutral aesthetic */}
              <div className="pt-3">
                <div className="inline-flex p-1 bg-[#F0EDE8] rounded-[8px] gap-1">
                  {[
                    { key: 'ALL', label: 'All Spaces' },
                    { key: 'MEETING_ROOM', label: 'Meeting Rooms' },
                    { key: 'POD', label: 'Pods' },
                    { key: 'DESK', label: 'Desks' },
                  ].map((tab) => (
                    <button
                      key={tab.key}
                      onClick={() => setFilterType(tab.key)}
                      className={`px-3 py-1.5 rounded-[6px] text-xs font-medium whitespace-nowrap transition-all cursor-pointer ${
                        filterType === tab.key
                          ? 'bg-white text-[#242424] shadow-xs'
                          : 'text-[#6B6560] hover:text-[#242424] hover:bg-white/50'
                      }`}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* 2-Column Resource Card Grid: Subtle border, clean typography */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 flex-1 content-start py-1">
              {filteredResources.map((resource) => {
                const isSelected = selectedSpaceId === resource.id;
                const badge = getStatusBadge(resource.status);
                const isBooked = resource.status === 'BOOKED' || resource.status === 'OCCUPIED';

                return (
                  <div
                    key={resource.id}
                    onClick={() => setSelectedSpaceId(resource.id)}
                    className={`p-4 min-w-[280px] min-h-[145px] rounded-[8px] bg-white transition-all duration-150 flex flex-col justify-between border cursor-pointer ${
                      isSelected
                        ? 'border-[#242424] ring-1 ring-[#242424] shadow-sm'
                        : 'border-[#E5E0D8]/80 hover:border-[#C5C0B8] hover:shadow-xs'
                    }`}
                  >
                    <div className="space-y-2">
                      {/* Name & Status Badge */}
                      <div className="flex items-start justify-between gap-2 min-w-0">
                        <h3
                          title={resource.name}
                          className="text-[14px] font-[600] text-[#242424] leading-snug line-clamp-2 break-words flex-1 min-w-0"
                        >
                          {resource.name}
                        </h3>
                        {/* Refined Status Pill with Status Dot */}
                        <span
                          className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-[500] shrink-0 tracking-wide ${badge.bg} ${badge.text}`}
                        >
                          <span className={`w-1.5 h-1.5 rounded-full ${badge.dot}`} />
                          {badge.label}
                        </span>
                      </div>

                      {/* Capacity & Zone Specs */}
                      <div className="flex items-center gap-2 text-[12px] font-[400] text-[#6B6560]">
                        <span className="material-symbols-outlined text-[15px] text-[#A8A29E]">group</span>
                        <span>
                          {resource.capacity} {resource.capacity === 1 ? 'seat' : 'seats'}
                        </span>
                        <span className="text-[#D4CFC9]">·</span>
                        <span className="truncate">{resource.zone?.floor || 'Level 03'}</span>
                      </div>

                      {/* Type Label */}
                      <div className="text-[12px] font-[400] text-[#6B6560]">
                        Type: <span className="text-[#242424] font-medium">{formatResourceType(resource.type)}</span>
                      </div>
                    </div>

                    {/* Card Action Row: Restrained monochromatic button, orange accent only on selected */}
                    <div className="pt-3 mt-2 border-t border-[#E5E0D8]/40 flex items-center justify-between">
                      <span className="text-[11px] text-[#A8A29E] font-normal">
                        {getZoneInfo(resource)}
                      </span>
                      {isBooked ? (
                        <button
                          disabled
                          onClick={(e) => e.stopPropagation()}
                          className="px-3.5 py-1.5 rounded-[6px] bg-[#F5F2ED] text-[#A8A29E] border border-[#E5E0D8]/60 text-xs font-normal cursor-not-allowed"
                        >
                          Booked
                        </button>
                      ) : isSelected ? (
                        <button
                          onClick={(e) => handleBookNow(resource, e)}
                          className="px-3.5 py-1.5 rounded-[6px] bg-[#D97757] text-white hover:bg-[#C56546] text-xs font-medium transition-all shadow-xs cursor-pointer"
                        >
                          Book now
                        </button>
                      ) : (
                        <button
                          onClick={(e) => handleBookNow(resource, e)}
                          className="px-3.5 py-1.5 rounded-[6px] bg-white hover:bg-[#242424] text-[#242424] hover:text-white border border-[#E5E0D8] hover:border-[#242424] text-xs font-medium transition-all shadow-xs cursor-pointer"
                        >
                          Book now
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Bottom Summary Bar */}
            <div className="pt-3 border-t border-[#E5E0D8]/60 flex items-center justify-between text-[12px] font-[400] text-[#6B6560]">
              <div className="flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-[#242424]"></span>
                <span>
                  Selected: <strong className="text-[#242424] font-medium">{selectedSpace.name}</strong>
                </span>
              </div>
              <span>
                Capacity: {selectedSpace.capacity} {selectedSpace.capacity === 1 ? 'seat' : 'seats'}
              </span>
            </div>
          </div>
        </div>

        {/* ========================================================= */}
        {/* RIGHT PANEL: People count, space details, frequency       */}
        {/* ========================================================= */}
        <div className="w-full xl:w-[320px] 2xl:w-[340px] shrink-0 flex flex-col gap-6">
          {/* 1. People Count Card (Card padding 16px: p-4 sm:p-5) */}
          <div className="bg-[#FAFAF8] rounded-[8px] p-4 sm:p-5 shadow-sm space-y-2 border-0">
            <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em] block">
              People Count · Live
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-[56px] font-bold text-[#242424] leading-none tracking-tight">
                142
              </span>
              <span className="text-[13px] font-[400] text-[#6B6560]">occupants</span>
            </div>
            <p className="text-[13px] font-[400] text-[#6B6560]">
              Peak activity anticipated at <span className="font-bold text-[#242424]">14:00</span> (88% capacity)
            </p>
            {/* Warm Gray Capacity Gauge: #D4CFC9 */}
            <div className="pt-2">
              <div className="w-full h-1.5 bg-[#F0EDE8] rounded-[4px] overflow-hidden">
                <div
                  className="h-full bg-[#D4CFC9] rounded-[4px]"
                  style={{ width: '74%' }}
                />
              </div>
            </div>
          </div>

          {/* 2. Space Telemetry Panel (Replaced Temp & Acoustics with operational metrics) */}
          <div className="bg-[#FAFAF8] rounded-[8px] p-4 sm:p-5 shadow-sm space-y-3.5 border-0">
            <div className="flex items-center justify-between pb-2 border-b border-[#E5E0D8]/60">
              <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em]">
                Space Telemetry
              </span>
              <span className="text-xs font-semibold text-[#242424] px-2.5 py-0.5 rounded-[4px] bg-[#F0EDE8]">
                {selectedSpace.type}
              </span>
            </div>

            <div>
              <h3 className="text-[15px] font-[600] text-[#242424]">{selectedSpace.name}</h3>
              <p className="text-[13px] font-[400] text-[#6B6560] mt-0.5">
                Capacity: {selectedSpace.capacity} {selectedSpace.capacity === 1 ? 'seat' : 'seats'} · West Wing
              </p>
            </div>

            {/* 6. Operational Telemetry Fields: Next booking, Today's bookings count, Last booked */}
            <div className="space-y-2 pt-1">
              {/* Next Booking */}
              <div className="p-3 rounded-[6px] bg-[#F0EDE8]/60">
                <span className="text-[11px] font-[400] text-[#6B6560] block">
                  Next booking
                </span>
                <span className="text-[13px] font-[500] text-[#242424] mt-0.5 block truncate">
                  {currentTelemetry.nextBooking}
                </span>
              </div>

              {/* Row: Today's bookings count & Last booked */}
              <div className="grid grid-cols-2 gap-2">
                <div className="p-3 rounded-[6px] bg-[#F0EDE8]/60">
                  <span className="text-[11px] font-[400] text-[#6B6560] block">
                    Today's bookings
                  </span>
                  <span className="text-[13px] font-[600] text-[#242424] mt-0.5 block">
                    {currentTelemetry.todayCount} reservations
                  </span>
                </div>
                <div className="p-3 rounded-[6px] bg-[#F0EDE8]/60">
                  <span className="text-[11px] font-[400] text-[#6B6560] block">
                    Last booked
                  </span>
                  <span className="text-[13px] font-[500] text-[#242424] mt-0.5 block truncate">
                    {currentTelemetry.lastBooked}
                  </span>
                </div>
              </div>
            </div>

            {/* 6. Book this Space Button: full-width, outline style with #D97757 */}
            <button
              onClick={onOpenBookingModal}
              className="w-full py-2.5 rounded-[6px] border border-[#D97757] text-[#D97757] bg-transparent hover:bg-[#D97757] hover:text-white transition-all text-xs font-[500] cursor-pointer shadow-xs"
            >
              Book this Space
            </button>
          </div>

          {/* 3. Frequency Chart: Warm Brown Chart Line #A89880 */}
          <div className="bg-[#FAFAF8] rounded-[8px] p-4 sm:p-5 shadow-sm space-y-3 border-0">
            <div className="flex items-center justify-between">
              <span className="text-[10px] uppercase font-[500] text-[#A8A29E] tracking-[0.08em]">
                Weekly Frequency
              </span>
              <span className="text-[13px] font-[400] text-[#6B6560]">Avg 68%</span>
            </div>

            {/* Smooth SVG Line Chart with Warm Brown #A89880 */}
            <div className="h-32 w-full pt-2">
              <svg viewBox="0 0 220 85" className="w-full h-full overflow-visible">
                <defs>
                  <linearGradient id="warmBrownGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                    <stop offset="0%" stopColor="#A89880" stopOpacity="0.25" />
                    <stop offset="100%" stopColor="#A89880" stopOpacity="0.0" />
                  </linearGradient>
                </defs>
                {/* Area under curve */}
                <path
                  d="M 10 70 C 40 70, 45 25, 75 35 C 105 45, 115 15, 145 15 C 175 15, 185 45, 210 40 L 210 80 L 10 80 Z"
                  fill="url(#warmBrownGrad)"
                />
                {/* Smooth Warm Brown Curve */}
                <path
                  d="M 10 70 C 40 70, 45 25, 75 35 C 105 45, 115 15, 145 15 C 175 15, 185 45, 210 40"
                  fill="none"
                  stroke="#A89880"
                  strokeWidth="2.8"
                  strokeLinecap="round"
                />
                {/* Peak Point Accent */}
                <circle cx="145" cy="15" r="4" fill="#A89880" stroke="#FAFAF8" strokeWidth="2" />
              </svg>
            </div>

            <div className="flex items-center justify-between text-[10px] uppercase font-bold text-[#8A8070] pt-2 border-t border-[#E5E0D8]/40 tracking-wider">
              <span>Mon</span>
              <span>Tue</span>
              <span>Wed</span>
              <span>Thu</span>
              <span>Fri</span>
              <span>Sat</span>
              <span>Sun</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
