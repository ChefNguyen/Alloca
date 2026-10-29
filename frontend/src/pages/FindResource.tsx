import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { mockResources } from '@/mock/data';
import { BookingModal } from '@/components/booking/BookingModal';
import { ApiError } from '@/lib/api';
import { listResources, Page } from '@/services/resourceApi';
import { Resource, ResourceType } from '@/types/api';

const PAGE_SIZE = 10;

type TypeFilter = 'ALL' | ResourceType;

// The page and filter a response belongs to; while it differs from the current one, the list is loading.
interface ListResult {
  key: string;
  page: Page<Resource> | null;
  error: string | null;
}

export const FindResource: React.FC = () => {
  const navigate = useNavigate();

  const [selectedLevel, setSelectedLevel] = useState<string>('Level 3');
  // The 2D map still shows demo data (floor map stories); the list reads the API.
  const [viewMode, setViewMode] = useState<'map' | 'list'>('list');
  const [selectedResourceId, setSelectedResourceId] = useState<string>('res-301');
  const [zoomLevel, setZoomLevel] = useState<number>(1);
  const [isBookingOpen, setIsBookingOpen] = useState<boolean>(false);
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
  const [pageNumber, setPageNumber] = useState(0);
  const [listResult, setListResult] = useState<ListResult | null>(null);

  const selectedResource =
    mockResources.find((r) => r.id === selectedResourceId) || mockResources[0];

  const listKey = `${typeFilter}:${pageNumber}`;
  const listLoading = listResult?.key !== listKey;
  const page = listLoading ? null : listResult.page;
  const listError = listLoading ? null : listResult.error;
  const listedResources = page?.items ?? [];

  useEffect(() => {
    let cancelled = false;
    const key = `${typeFilter}:${pageNumber}`;
    listResources({
      page: pageNumber,
      size: PAGE_SIZE,
      sort: 'name,asc',
      type: typeFilter === 'ALL' ? undefined : typeFilter,
    })
      .then((loaded) => {
        if (!cancelled) setListResult({ key, page: loaded, error: null });
      })
      .catch((error) => {
        if (cancelled) return;
        const message = error instanceof ApiError ? error.message : 'Could not load resources.';
        setListResult({ key, page: null, error: message });
      });
    return () => {
      cancelled = true;
    };
  }, [typeFilter, pageNumber]);

  return (
    <div className="flex flex-col w-full p-6 lg:p-8 space-y-6 bg-[#F0EDE8] min-h-screen">
      {/* Top Navigation Toolbar */}
      <section className="bg-[#FAFAF8] p-6 flex flex-wrap items-center justify-between gap-4 shadow-card rounded-[8px] border-0">
        {/* Left: Building & Level Selector */}
        <div className="flex flex-wrap items-center gap-4">
          <div>
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Architectural Plane
            </span>
            <div className="flex items-center gap-1.5 text-sm font-bold text-[#242424] mt-0.5 font-editorial">
              <span>Acme Space · Tower A</span>
            </div>
          </div>

          <div className="h-6 w-px bg-[#E5E0D8] hidden md:block" />

          {/* Level Buttons */}
          <div className="inline-flex p-1 bg-[#F0EDE8] rounded-[6px] gap-1">
            {['Level 1', 'Level 2', 'Level 3', 'Level 4'].map((lvl) => (
              <button
                key={lvl}
                onClick={() => setSelectedLevel(lvl)}
                className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-all cursor-pointer ${
                  selectedLevel === lvl
                    ? 'bg-[#242424] text-white shadow-xs'
                    : 'text-[#8A8070] hover:text-[#242424]'
                }`}
                type="button"
              >
                {lvl}
              </button>
            ))}
          </div>
        </div>

        {/* Center: Time Window & Date Selector */}
        <div className="flex items-center gap-2 bg-[#F0EDE8] px-3 py-1.5 rounded-[4px] text-xs">
          <span className="text-[#242424] font-semibold">Today, Oct 14</span>
          <span className="text-[#8A8070]">•</span>
          <span className="text-[#242424] font-medium">09:00 – 18:00 Window</span>
        </div>

        {/* Right: Legend & View Switcher */}
        <div className="flex items-center gap-4">
          <div className="hidden xl:flex items-center gap-3 text-xs text-[#8A8070]">
            <span className="flex items-center gap-1.5 text-[#242424] font-medium">
              <span className="w-2.5 h-2.5 rounded-[2px] bg-[#F59E0B]" /> Occupied
            </span>
            <span className="flex items-center gap-1.5 text-[#242424] font-medium">
              <span className="w-2.5 h-2.5 rounded-[2px] bg-[#F59E0B]" /> Reserved
            </span>
            <span className="flex items-center gap-1.5 text-[#8A8070] font-medium">
              <span className="w-2.5 h-2.5 rounded-[2px] bg-[#22C55E]" /> Available
            </span>
          </div>

          {/* View Segmented Switch */}
          <div className="inline-flex p-1 bg-[#F0EDE8] rounded-[6px] gap-1">
            <button
              onClick={() => setViewMode('map')}
              className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-all cursor-pointer ${
                viewMode === 'map'
                  ? 'bg-[#FAFAF8] text-[#242424] shadow-card'
                  : 'text-[#8A8070] hover:text-[#242424]'
              }`}
              type="button"
            >
              2D Schematic
            </button>
            <button
              onClick={() => setViewMode('list')}
              className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-all cursor-pointer ${
                viewMode === 'list'
                  ? 'bg-[#FAFAF8] text-[#242424] shadow-card'
                  : 'text-[#8A8070] hover:text-[#242424]'
              }`}
              type="button"
            >
              List View
            </button>
          </div>
        </div>
      </section>

      {/* Main Workspace Split */}
      {viewMode === 'map' ? (
        <div className="grid grid-cols-12 gap-6 items-start">
          {/* Architectural 2D Blueprint Stage (8 of 12 columns) */}
          <div className="col-span-12 lg:col-span-8 space-y-4">
            <div className="bg-[#FAFAF8] p-6 rounded-[8px] shadow-card space-y-4 border-0">
              <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
                <div>
                  <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                    Orthographic Projection
                  </span>
                  <h2 className="text-xl font-light text-[#242424] tracking-wide font-editorial">
                    Level 03 Architectural Schematic
                  </h2>
                </div>

                {/* Zoom Controls */}
                <div className="flex items-center gap-1 bg-[#F0EDE8] p-1 rounded-[6px]">
                  <button
                    onClick={() => setZoomLevel((z) => Math.min(z + 0.1, 1.4))}
                    className="w-7 h-7 rounded-[4px] flex items-center justify-center text-[#242424] hover:bg-white cursor-pointer"
                  >
                    +
                  </button>
                  <button
                    onClick={() => setZoomLevel((z) => Math.max(z - 0.1, 0.8))}
                    className="w-7 h-7 rounded-[4px] flex items-center justify-center text-[#242424] hover:bg-white cursor-pointer"
                  >
                    -
                  </button>
                  <button
                    onClick={() => setZoomLevel(1)}
                    className="px-2.5 h-7 rounded-[4px] text-[11px] font-semibold text-[#8A8070] hover:bg-white cursor-pointer"
                  >
                    100%
                  </button>
                </div>
              </div>

              {/* Vector Architectural Canvas */}
              <div className="relative w-full aspect-[16/10] bg-[#F0EDE8]/60 rounded-[8px] overflow-hidden flex items-center justify-center p-4 select-none">
                <svg className="absolute inset-0 w-full h-full text-[#E5E0D8]" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <pattern id="archGridWarm" width="32" height="32" patternUnits="userSpaceOnUse">
                      <path d="M 32 0 L 0 0 0 32" fill="none" stroke="currentColor" strokeDasharray="2 2" strokeWidth="0.75" />
                    </pattern>
                  </defs>
                  <rect width="100%" height="100%" fill="url(#archGridWarm)" />
                </svg>

                <div
                  style={{ transform: `scale(${zoomLevel})` }}
                  className="relative z-10 w-full h-full transition-transform duration-200"
                >
                  {/* Outer Perimeter */}
                  <div className="absolute inset-2 border-2 border-[#8A8070]/30 rounded-[6px] pointer-events-none" />

                  {/* Room 1: Boardroom West 301 */}
                  <div
                    onClick={() => setSelectedResourceId('res-301')}
                    className={`absolute left-6 top-6 w-56 h-40 rounded-[8px] p-4 flex flex-col justify-between cursor-pointer transition-all ${
                      selectedResourceId === 'res-301'
                        ? 'ring-2 ring-[#242424] bg-[#FAFAF8] shadow-hero'
                        : 'border border-[#A89880] bg-[#FAFAF8]/90 hover:bg-[#FAFAF8]'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-xs text-[#242424]">Boardroom West 301</span>
                      <span className="w-2.5 h-2.5 rounded-[2px] bg-[#F59E0B]" />
                    </div>
                    <div className="text-[11px] text-[#8A8070]">
                      <p>Capacity: 12 persons</p>
                      <p className="text-[#B45309] font-medium mt-0.5">Occupied: 09:00 - 10:30</p>
                    </div>
                    <div className="text-[10px] uppercase tracking-wider font-bold text-[#242424] pt-1 border-t border-[#E5E0D8]/60">
                      Select Space →
                    </div>
                  </div>

                  {/* Room 2: Meeting Pod A */}
                  <div
                    onClick={() => setSelectedResourceId('res-pod-a')}
                    className={`absolute left-68 top-6 w-32 h-32 rounded-[8px] p-3 flex flex-col justify-between cursor-pointer transition-all ${
                      selectedResourceId === 'res-pod-a'
                        ? 'ring-2 ring-[#242424] bg-[#FAFAF8] shadow-hero'
                        : 'border border-[#E5E0D8] bg-[#FAFAF8]/90 hover:bg-[#FAFAF8]'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-xs text-[#242424]">Pod A</span>
                      <span className="w-2.5 h-2.5 rounded-[2px] bg-[#22C55E]" />
                    </div>
                    <p className="text-[11px] text-[#8A8070]">Cap: 4</p>
                    <span className="text-[10px] text-[#22C55E] font-bold">Available</span>
                  </div>

                  {/* Room 3: Meeting Pod B */}
                  <div
                    onClick={() => setSelectedResourceId('res-pod-b')}
                    className={`absolute left-104 top-6 w-32 h-32 rounded-[8px] p-3 flex flex-col justify-between cursor-pointer transition-all ${
                      selectedResourceId === 'res-pod-b'
                        ? 'ring-2 ring-[#242424] bg-[#FAFAF8] shadow-hero'
                        : 'border border-[#E5E0D8] bg-[#FAFAF8]/90 hover:bg-[#FAFAF8]'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-xs text-[#242424]">Pod B</span>
                      <span className="w-2.5 h-2.5 rounded-[2px] bg-[#22C55E]" />
                    </div>
                    <p className="text-[11px] text-[#8A8070]">Cap: 4</p>
                    <span className="text-[10px] text-[#22C55E] font-bold">Available</span>
                  </div>

                  {/* Focus Desks Row */}
                  <div className="absolute left-6 bottom-6 right-6 h-28 bg-[#FAFAF8]/80 rounded-[8px] p-3 flex items-center justify-around shadow-card">
                    <span className="text-[10px] font-bold text-[#8A8070] uppercase tracking-widest">
                      Focus Desks:
                    </span>
                    {mockResources.filter((r) => r.type === 'DESK').map((desk) => (
                      <div
                        key={desk.id}
                        onClick={() => setSelectedResourceId(desk.id)}
                        className={`p-2 rounded-[6px] flex flex-col items-center gap-1 cursor-pointer transition-all ${
                          selectedResourceId === desk.id
                            ? 'ring-2 ring-[#242424] bg-[#242424]/10'
                            : 'bg-[#F0EDE8] hover:scale-105'
                        }`}
                      >
                        <span className="text-xs font-bold text-[#242424]">{desk.name.replace('Focus Desk ', 'D-')}</span>
                        <span className="text-[9px] text-[#22C55E] font-semibold">{desk.status}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Companion Sidebar (4 of 12 columns) */}
          <div className="col-span-12 lg:col-span-4 space-y-4">
            <div className="bg-[#FAFAF8] rounded-[8px] p-6 shadow-card space-y-4 border-0">
              <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                Space Specification
              </span>

              <div>
                <h3 className="text-xl font-bold text-[#242424] font-editorial">{selectedResource.name}</h3>
                <p className="text-xs text-[#8A8070] mt-0.5">
                  {selectedResource.zone?.name} · {selectedResource.type}
                </p>
              </div>

              <p className="text-xs text-[#242424]/80 leading-relaxed bg-[#F0EDE8]/60 p-4 rounded-[6px]">
                {selectedResource.description}
              </p>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="p-3 rounded-[6px] bg-[#F0EDE8]/60">
                  <span className="text-[10px] text-[#8A8070] uppercase font-bold block">Capacity</span>
                  <span className="font-bold text-[#242424] mt-0.5 block">{selectedResource.capacity} seats</span>
                </div>
                <div className="p-3 rounded-[6px] bg-[#F0EDE8]/60">
                  <span className="text-[10px] text-[#8A8070] uppercase font-bold block">Threshold</span>
                  <span className="font-bold text-[#242424] mt-0.5 block">{selectedResource.utilizationThreshold}%</span>
                </div>
              </div>

              <div className="space-y-2 pt-2">
                <button
                  disabled={selectedResource.status === 'SUSPENDED'}
                  onClick={() => setIsBookingOpen(true)}
                  className="w-full py-2.5 rounded-[6px] bg-[#242424] hover:bg-black text-white text-xs font-semibold shadow-card transition-colors cursor-pointer"
                >
                  Reserve This Space
                </button>
                <button
                  onClick={() => navigate(`/resources/${selectedResource.id}`)}
                  className="w-full py-2 rounded-[6px] bg-[#FAFAF8] text-[#242424] border border-[#D5D0C8] hover:bg-black hover:text-white hover:border-black text-xs font-semibold transition-colors cursor-pointer"
                >
                  Full Technical Profile →
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : (
        /* List View */
        <div className="bg-[#FAFAF8] rounded-[8px] p-6 shadow-card space-y-4 border-0">
          <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
            <div>
              <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                Resource Catalog
              </span>
              <h2 className="text-base font-bold text-[#242424] font-editorial">Tabular Directory</h2>
            </div>

            <select
              value={typeFilter}
              onChange={(e) => {
                setTypeFilter(e.target.value as TypeFilter);
                setPageNumber(0);
              }}
              className="h-9 px-3 rounded-[6px] bg-[#F0EDE8]/60 text-xs font-semibold border-0 text-[#242424]"
            >
              <option value="ALL">All Spaces</option>
              <option value="ROOM">Meeting Rooms</option>
              <option value="DESK">Desks</option>
              <option value="EQUIPMENT">Equipment</option>
            </select>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#E5E0D8] text-[#8A8070] uppercase font-bold text-[10px] tracking-wider">
                  <th className="py-3 px-3">Name</th>
                  <th className="py-3 px-3">Type</th>
                  <th className="py-3 px-3">Location</th>
                  <th className="py-3 px-3">Capacity</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E5E0D8]/40">
                {listLoading && (
                  <tr>
                    <td colSpan={6} className="py-8 px-3 text-center text-[#8A8070]">
                      Loading resources…
                    </td>
                  </tr>
                )}
                {listError && (
                  <tr>
                    <td colSpan={6} className="py-8 px-3 text-center text-[#C62828] font-semibold" role="alert">
                      Could not load resources: {listError}
                    </td>
                  </tr>
                )}
                {page && listedResources.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-8 px-3 text-center text-[#8A8070]">
                      No resources match this filter.
                    </td>
                  </tr>
                )}
                {listedResources.map((res) => (
                  <tr
                    key={res.id}
                    onClick={() => navigate(`/resources/${res.id}`)}
                    className="hover:bg-[#F0EDE8]/40 cursor-pointer"
                  >
                    <td className="py-3 px-3 font-bold text-[#242424]">{res.name}</td>
                    <td className="py-3 px-3 text-[#8A8070]">{res.type}</td>
                    <td className="py-3 px-3 text-[#8A8070]">
                      {res.zone ? `${res.zone.building} · ${res.zone.floor} · ${res.zone.name}` : '—'}
                    </td>
                    <td className="py-3 px-3">{res.capacity} seats</td>
                    <td className="py-3 px-3">
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-[4px] ${
                          res.status === 'AVAILABLE'
                            ? 'bg-[#22C55E]/10 text-[#22C55E]'
                            : 'bg-[#F59E0B]/15 text-[#B45309]'
                        }`}
                      >
                        {res.status}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-right">
                      <button
                        disabled={res.status !== 'AVAILABLE'}
                        title={res.status !== 'AVAILABLE' ? 'Suspended resources cannot be booked' : undefined}
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedResourceId(res.id);
                          setIsBookingOpen(true);
                        }}
                        className="px-3 py-1.5 rounded-[6px] bg-[#FAFAF8] text-[#242424] border border-[#D5D0C8] hover:bg-black hover:text-white hover:border-black text-xs font-semibold transition-all cursor-pointer shadow-xs disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-[#FAFAF8] disabled:hover:text-[#242424] disabled:hover:border-[#D5D0C8]"
                      >
                        Reserve
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {page && page.page.totalElements > 0 && (
            <div className="flex items-center justify-between pt-2 text-xs text-[#8A8070]">
              <span>
                Showing {page.page.number * page.page.size + 1}–
                {page.page.number * page.page.size + listedResources.length} of {page.page.totalElements}
              </span>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={page.page.first}
                  onClick={() => setPageNumber((n) => n - 1)}
                  className="px-3 py-1.5 rounded-[6px] border border-[#D5D0C8] font-semibold text-[#242424] cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  ← Previous
                </button>
                <span>
                  Page {page.page.number + 1} / {page.page.totalPages}
                </span>
                <button
                  type="button"
                  disabled={page.page.last}
                  onClick={() => setPageNumber((n) => n + 1)}
                  className="px-3 py-1.5 rounded-[6px] border border-[#D5D0C8] font-semibold text-[#242424] cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Next →
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      <BookingModal
        isOpen={isBookingOpen}
        onClose={() => setIsBookingOpen(false)}
        resources={viewMode === 'list' ? listedResources : mockResources}
        initialResourceId={selectedResourceId}
        onBookingSuccess={() => {
          setIsBookingOpen(false);
          navigate('/my-bookings');
        }}
      />
    </div>
  );
};
