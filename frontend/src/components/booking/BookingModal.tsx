import React, { useEffect, useRef, useState } from 'react';
import type { Resource, Booking } from '@/types/api';
import { useAuth } from '@/context/AuthContext';
import { ApiError } from '@/lib/api';
import { availableEnds, businessDate, businessTime, dateAfter } from '@/lib/bookingTime';
import { createBooking, getAvailability, searchBookingOwners, searchBookingResources } from '@/services/bookingApi';
import type { Availability, BookingOwner } from '@/services/bookingApi';

interface BookingModalProps {
  isOpen: boolean;
  onClose: () => void;
  resources?: Resource[];
  initialResourceId?: string;
  onBookingSuccess: (newBooking: Booking) => void;
}
export const BookingModal: React.FC<BookingModalProps> = props => {
  const { user } = useAuth();
  return props.isOpen ? <BookingForm key={`${user?.id}/${user?.role}/${user?.department?.id}`} {...props} /> : null;
};
const inputClass = 'w-full h-11 px-3 rounded-md bg-[#F0EDE8] text-[#242424] text-sm border border-[#E5E0D8] focus:outline-none focus:ring-1 focus:ring-[#D97757] disabled:opacity-50';
const message = (error: unknown) => error instanceof Error ? error.message : 'Could not reach the server.';

const BookingForm: React.FC<BookingModalProps> = ({ onClose, resources, initialResourceId, onBookingSuccess }) => {
  const { user } = useAuth();
  const canDelegate = user?.role === 'ADMIN' || (user?.role === 'MANAGER' && !!user.department);
  const [options, setOptions] = useState<Resource[]>(resources ?? []);
  const [resourceId, setResourceId] = useState(initialResourceId || resources?.find(r => r.status === 'AVAILABLE')?.id || '');
  const [resourceQuery, setResourceQuery] = useState('');
  const [resourcePage, setResourcePage] = useState(0);
  const [moreResources, setMoreResources] = useState(false);
  const [catalogRevision, setCatalogRevision] = useState(0);
  const catalogKey = `${resourceQuery}/${resourcePage}/${catalogRevision}`;
  const [catalogResult, setCatalogResult] = useState<{ key: string; error?: string } | null>(null);
  const resourcesLoading = !resources && catalogResult?.key !== catalogKey;
  const catalogError = catalogResult?.key === catalogKey ? catalogResult.error : null;
  const [date, setDate] = useState(() => businessDate());
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [purpose, setPurpose] = useState('');
  const [ownerId, setOwnerId] = useState('');
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerPage, setOwnerPage] = useState(0);
  const [owners, setOwners] = useState<BookingOwner[]>([]);
  const [moreOwners, setMoreOwners] = useState(false);
  const ownerKey = `${ownerQuery}/${ownerPage}`;
  const [ownerResult, setOwnerResult] = useState<{ key: string; error?: string } | null>(null);
  const ownerError = ownerResult?.key === ownerKey ? ownerResult.error : null;
  const ownersLoading = canDelegate && ownerResult?.key !== ownerKey;
  const [refresh, setRefresh] = useState(0);
  const [availability, setAvailability] = useState<{ key: string; value?: Availability; error?: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const key = `${resourceId}/${date}/${refresh}`;

  useEffect(() => {
    if (resources) return;
    let active = true;
    const timer = setTimeout(() => {
      searchBookingResources(resourceQuery, resourcePage).then(result => {
        if (!active) return;
        setOptions(previous => resourcePage === 0 ? result.items : [...previous, ...result.items]);
        setMoreResources(!result.page.last);
        setCatalogResult({ key: catalogKey });
      }).catch(e => { if (active) setCatalogResult({ key: catalogKey, error: message(e) }); });
    }, 200);
    return () => { active = false; clearTimeout(timer); };
  }, [resources, resourceQuery, resourcePage, catalogKey]);
  useEffect(() => {
    if (!canDelegate) return;
    let active = true;
    const timer = setTimeout(() => {
      searchBookingOwners(ownerQuery, ownerPage).then(result => {
        if (!active) return;
        setOwners(previous => ownerPage === 0 ? result.items : [...previous, ...result.items]);
        setMoreOwners(!result.page.last);
        setOwnerResult({ key: ownerKey });
      }).catch(e => { if (active) setOwnerResult({ key: ownerKey, error: message(e) }); });
    }, 200);
    return () => { active = false; clearTimeout(timer); };
  }, [canDelegate, ownerQuery, ownerPage, ownerKey]);
  useEffect(() => {
    if (!resourceId || !date) return;
    let active = true;
    getAvailability(resourceId, date).then(value => {
      if (active) setAvailability({ key, value });
    }).catch(e => { if (active) setAvailability({ key, error: message(e) }); });
    return () => { active = false; };
  }, [key, resourceId, date]);

  const current = availability?.key === key ? availability : null;
  const slots = current?.value?.availableSlots ?? [];
  const ends = availableEnds(slots, start);
  const ready = current?.value?.resourceStatus === 'AVAILABLE' && slots.some(slot => slot.startTime === start) && ends.includes(end);
  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submittingRef.current || !ready) return;
    submittingRef.current = true; setSubmitting(true); setError(null);
    try {
      const booking = await createBooking({ resourceId, ...(canDelegate && ownerId ? { userId: ownerId } : {}),
        startTime: start, endTime: end, ...(purpose.trim() ? { purpose: purpose.trim() } : {}) });
      onBookingSuccess(booking); onClose();
    } catch (e) {
      setError(e instanceof ApiError && e.status === 409
        ? 'This time was just booked by someone else. Availability has been refreshed; choose another slot.' : message(e));
      setStart(''); setEnd(''); setRefresh(value => value + 1);
    } finally { submittingRef.current = false; setSubmitting(false); }
  };

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
    <section role="dialog" aria-modal="true" aria-label="New reservation" className="w-full max-w-lg max-h-[90vh] overflow-y-auto bg-[#FAFAF8] rounded-lg shadow-xl">
      <div className="flex items-center justify-between p-5 border-b border-[#E5E0D8]">
        <div><h2 className="text-xl text-[#242424] font-editorial">New reservation</h2><p className="text-xs text-[#8A8070]">All times are in Asia/Ho_Chi_Minh (UTC+7).</p></div>
        <button type="button" aria-label="Close reservation" disabled={submitting} onClick={onClose} className="p-2">✕</button>
      </div>
      <form onSubmit={handleSubmit} className="p-5 space-y-4">
        {error && <p role="alert" className="p-3 text-sm bg-[#FFEBEE] text-[#C62828] rounded">{error}</p>}
        {!resources && <label className="block text-xs">Find resource<input aria-label="Find resource" className={inputClass} value={resourceQuery} disabled={submitting}
          onChange={e => { setResourceQuery(e.target.value); setResourcePage(0); setResourceId(''); setStart(''); setEnd(''); }} /></label>}
        <label className="block text-xs">Resource<select aria-label="Resource" className={inputClass} value={resourceId} disabled={submitting || resourcesLoading} required onChange={e => { setResourceId(e.target.value); setStart(''); setEnd(''); }}>
          <option value="">Select a resource</option>
          {options.map(resource => <option key={resource.id} value={resource.id} disabled={resource.status !== 'AVAILABLE'}>{resource.name} · {resource.type}{resource.status !== 'AVAILABLE' ? ` (${resource.status})` : ''}</option>)}
        </select></label>
        {resourcesLoading && <p role="status" className="text-xs">Loading resources…</p>}
        {catalogError && <p role="alert">{catalogError} <button type="button" onClick={() => setCatalogRevision(r => r + 1)}>Retry resources</button></p>}
        {!resources && moreResources && <button type="button" disabled={resourcesLoading} onClick={() => setResourcePage(p => p + 1)} className="text-xs underline">Load more resources</button>}
        {!resourcesLoading && !catalogError && options.length === 0 && <p role="status" className="text-xs">No available resources found.</p>}
        {canDelegate && <fieldset className="space-y-2" disabled={submitting}>
          <label className="block text-xs">Find a person<input aria-label="Find a person" className={inputClass} value={ownerQuery}
            onChange={e => { setOwnerQuery(e.target.value); setOwnerPage(0); setOwnerId(''); }} /></label>
          <label className="block text-xs">Book for<select aria-label="Book for" className={inputClass} value={ownerId} disabled={ownersLoading} onChange={e => setOwnerId(e.target.value)}>
            <option value="">Myself</option>{owners.filter(owner => owner.id !== user?.id).map(owner => <option key={owner.id} value={owner.id}>{owner.username}</option>)}
          </select></label>
          {ownersLoading && <p role="status" className="text-xs">Loading people…</p>}
          {ownerError && <p role="alert">{ownerError} You can still book for yourself.</p>}
          {moreOwners && <button type="button" disabled={ownersLoading} onClick={() => setOwnerPage(p => p + 1)} className="text-xs underline">Load more people</button>}
        </fieldset>}
        <label className="block text-xs">Date<input aria-label="Date" type="date" required className={inputClass} value={date} min={businessDate()} max={dateAfter(businessDate(), 30)} disabled={submitting} onChange={e => { setDate(e.target.value); setStart(''); setEnd(''); }} /></label>
        {resourceId && !current && <p role="status" className="text-sm">Loading availability…</p>}
        {current?.error && <div role="alert" className="text-sm text-[#C62828]">{current.error} <button type="button" onClick={() => setRefresh(r => r + 1)} className="underline">Retry availability</button></div>}
        {current?.value && slots.length === 0 && <p role="status" className="text-sm">No bookable slots on this date. Try another date or resource.</p>}
        <div className="grid grid-cols-2 gap-3">
          <label className="block text-xs">Start time<select aria-label="Start time" required className={inputClass} value={start} disabled={submitting || !slots.length} onChange={e => { setStart(e.target.value); setEnd(''); }}>
            <option value="">Select start</option>{slots.map(slot => <option key={slot.startTime} value={slot.startTime}>{businessTime(slot.startTime)}</option>)}
          </select></label>
          <label className="block text-xs">End time<select aria-label="End time" required className={inputClass} value={end} disabled={submitting || !ends.length} onChange={e => setEnd(e.target.value)}>
            <option value="">Select end</option>{ends.map(value => <option key={value} value={value}>{businessTime(value)}</option>)}
          </select></label>
        </div>
        <label className="block text-xs">Purpose<input aria-label="Purpose" className={inputClass} value={purpose} maxLength={500} disabled={submitting} onChange={e => setPurpose(e.target.value)} /></label>
        <p className="text-xs text-[#8A8070]">Slots are not held until confirmation. Reservations last 30 minutes to 8 hours.</p>
        <div className="flex justify-end gap-3 border-t border-[#E5E0D8] pt-4">
          <button type="button" disabled={submitting} onClick={onClose}>Close</button>
          <button type="submit" disabled={!ready || submitting} className="px-4 py-2 bg-[#D97757] text-white rounded-md disabled:opacity-50">{submitting ? 'Confirming…' : 'Confirm reservation'}</button>
        </div>
      </form>
    </section>
  </div>;
};
