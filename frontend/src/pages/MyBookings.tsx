import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useOutletContext, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ApiError } from '@/lib/api';
import { businessTime, dateAfter, formatBookingDate } from '@/lib/bookingTime';
import { cancelBooking, getBooking, listBookings } from '@/services/bookingApi';
import type { BookingDetails, BookingPage, BookingRecord } from '@/services/bookingApi';
import type { BookingStatus } from '@/types/api';

const describeError = (error: unknown) => error instanceof ApiError
  ? [error.message, ...error.fieldErrors.map(field => `${field.field}: ${field.message}`)].join(' ')
  : 'Could not load reservations. Please try again.';
const buttonClass = 'px-3 py-2 rounded-md text-xs font-semibold bg-[#F0EDE8] text-[#242424] disabled:opacity-40';
const inputClass = 'p-2 rounded-md border border-[#E5E0D8] bg-[#FAFAF8] text-sm';
const statusClass = (status: BookingStatus) => status === 'CONFIRMED' ? 'bg-[#E8F5EE] text-[#2E7D32]'
  : status === 'CANCELLED' ? 'bg-[#FFEBEE] text-[#C62828]' : 'bg-[#F0EDE8] text-[#242424]';

export const MyBookings: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { onOpenBookingModal, bookingRevision = 0 } = useOutletContext<{ onOpenBookingModal: () => void; bookingRevision?: number }>();
  const [scope, setScope] = useState('mine');
  const [status, setStatus] = useState<BookingStatus | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [sort, setSort] = useState('startTime,desc');
  const [pagination, setPagination] = useState({ revision: bookingRevision, page: 0 });
  const page = pagination.revision === bookingRevision ? pagination.page : 0;
  const setPage = useCallback((next: number | ((previous: number) => number)) => setPagination(previous => ({
    revision: bookingRevision, page: typeof next === 'function' ? next(previous.revision === bookingRevision ? previous.page : 0) : next,
  })), [bookingRevision]);
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<{ key: string; data?: BookingPage; error?: string } | null>(null);
  const [cancelling, setCancelling] = useState<BookingRecord | null>(null);
  const [reason, setReason] = useState('');
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [detailsId, setDetailsId] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const canSeeScope = user?.role === 'ADMIN' || (user?.role === 'MANAGER' && !!user.department);
  const effectiveScope = canSeeScope ? scope : 'mine';
  const rangeError = from && to && from > to ? 'The end date must not precede the start date.' : null;
  const userId = user?.id;
  const key = JSON.stringify([userId, user?.role, user?.department?.id, effectiveScope, status, from, to, sort, page, reload, bookingRevision]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    if (!userId || rangeError) return;
    let active = true;
    listBookings({ page, size: 10, sort, ...(status ? { status } : {}),
      ...(effectiveScope === 'mine' ? { userId } : {}),
      ...(from ? { from: new Date(`${from}T00:00:00+07:00`).toISOString() } : {}),
      ...(to ? { to: new Date(`${dateAfter(to, 1)}T00:00:00+07:00`).toISOString() } : {}),
    }).then(data => {
      if (!active) return;
      if (!data.items.length && page > 0) { setPage(p => p - 1); return; }
      setResult({ key, data });
    }).catch(error => { if (active) setResult({ key, error: describeError(error) }); });
    return () => { active = false; };
  }, [key, userId, rangeError, page, sort, status, effectiveScope, from, to, setPage]);

  const current = result?.key === key ? result : null;
  const records = current?.data?.items ?? [];
  const metadata = current?.data?.page;
  const handleCancelBooking = async () => {
    if (!cancelling || busyRef.current) return;
    busyRef.current = true; setBusy(true); setCancelError(null);
    try {
      await cancelBooking(cancelling.id, reason.trim() || undefined);
      setFeedback(`Reservation for ${cancelling.resource.name} was cancelled.`);
      setCancelling(null); setReload(r => r + 1);
    } catch (error) {
      setCancelError(describeError(error));
      setReload(r => r + 1); // A 409 may mean another actor changed this booking.
    } finally { busyRef.current = false; setBusy(false); }
  };
  const refreshPage = () => { setReload(r => r + 1); setNow(Date.now()); };

  return <div className="p-6 lg:p-8 space-y-6 bg-[#F0EDE8] min-h-screen text-[#242424]">
    <header className="flex flex-wrap items-end justify-between gap-4 border-b border-[#E5E0D8] pb-4">
      <div><p className="text-xs uppercase tracking-widest text-[#8A8070]">Reservations & history</p>
        <h1 className="text-4xl font-light font-editorial">My bookings</h1>
        <p className="text-xs text-[#8A8070] mt-2">Times shown in Asia/Ho_Chi_Minh (UTC+7).</p></div>
      <div className="flex gap-2"><button className={buttonClass} onClick={refreshPage}>Refresh</button>
        <button className="px-4 py-2 rounded-md bg-[#D97757] text-white text-sm" onClick={onOpenBookingModal}>+ New reservation</button></div>
    </header>
    {feedback && <p role="status" className="p-3 bg-[#E8F5EE] text-[#2E7D32] rounded-md text-sm">{feedback}</p>}
    <div className="flex flex-wrap items-end gap-3">
      {canSeeScope && <label className="text-xs">Scope<select aria-label="Scope" className={`${inputClass} block`} value={scope} onChange={e => { setScope(e.target.value); setPage(0); }}>
        <option value="mine">My bookings</option><option value="permitted">{user?.role === 'ADMIN' ? 'All bookings' : 'My department'}</option>
      </select></label>}
      <label className="text-xs">Status<select aria-label="Status" className={`${inputClass} block`} value={status} onChange={e => { setStatus(e.target.value as BookingStatus | ''); setPage(0); }}>
        <option value="">All statuses</option><option value="CONFIRMED">Confirmed</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option>
      </select></label>
      <label className="text-xs">From date<input aria-label="From date" type="date" className={`${inputClass} block`} value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} /></label>
      <label className="text-xs">To date<input aria-label="To date" type="date" className={`${inputClass} block`} value={to} onChange={e => { setTo(e.target.value); setPage(0); }} /></label>
      <label className="text-xs">Sort<select aria-label="Sort" className={`${inputClass} block`} value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}>
        <option value="startTime,desc">Latest start first</option><option value="startTime,asc">Earliest start first</option><option value="createdAt,desc">Recently created</option>
      </select></label>
    </div>
    {rangeError && <p role="alert" className="text-[#C62828]">{rangeError}</p>}
    {!rangeError && !current && <p role="status">Loading reservations…</p>}
    {current?.error && <div role="alert" className="p-4 rounded-md bg-[#FFEBEE] text-[#C62828]">{current.error} <button className="underline" onClick={refreshPage}>Retry</button></div>}
    {!rangeError && current?.data && <section aria-label="Reservations" className="p-5 bg-[#FAFAF8] rounded-lg shadow-card">
      {!records.length ? <p className="py-10 text-center text-sm text-[#8A8070]">No reservations found for these filters.</p> : <div className="divide-y divide-[#E5E0D8]">
        {records.map(booking => <article key={booking.id} className="py-5 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2"><h2 className="font-semibold">{booking.purpose || 'Resource reservation'}</h2>
              <span className={`text-xs rounded px-2 py-1 ${statusClass(booking.status)}`}>{booking.status}</span></div>
            <p className="text-sm">{booking.resource.name} · {formatBookingDate(booking.startTime)} · {businessTime(booking.startTime)}–{businessTime(booking.endTime)}</p>
            <p className="text-xs text-[#8A8070]">Owner: {booking.user.fullName} · Created by: {booking.createdBy.fullName}</p>
            {booking.cancelReason && <p className="text-xs">Cancellation reason: {booking.cancelReason}</p>}
          </div>
          <div className="flex gap-2 flex-wrap">
            <button className={buttonClass} onClick={() => setDetailsId(booking.id)}>Details & history</button>
            {booking.resource.status !== 'DELETED' && <button className={buttonClass} onClick={() => navigate(`/resources/${booking.resource.id}`)}>Resource</button>}
            {booking.status === 'CONFIRMED' && Date.parse(booking.startTime) > now && <button className="px-3 py-2 rounded-md text-xs text-[#C62828] bg-[#FFEBEE]" onClick={() => {
              setCancelling(booking); setReason(''); setCancelError(null); setFeedback(null);
            }}>Cancel reservation</button>}
          </div>
        </article>)}
      </div>}
      <div className="flex items-center justify-between border-t border-[#E5E0D8] pt-4 text-xs">
        <span>{metadata?.totalElements ?? 0} reservations · Page {(metadata?.number ?? 0) + 1} of {Math.max(metadata?.totalPages ?? 0, 1)}</span>
        <div className="flex gap-2"><button className={buttonClass} disabled={metadata?.first ?? true} onClick={() => setPage(p => p - 1)}>Previous</button>
          <button className={buttonClass} disabled={metadata?.last ?? true} onClick={() => setPage(p => p + 1)}>Next</button></div>
      </div>
    </section>}
    {cancelling && <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
      <section role="dialog" aria-modal="true" aria-label="Cancel reservation" className="w-full max-w-md p-6 bg-[#FAFAF8] rounded-lg space-y-4">
        <h2 className="text-xl font-editorial">Cancel reservation?</h2><p className="text-sm">{cancelling.resource.name} · {formatBookingDate(cancelling.startTime)} · {businessTime(cancelling.startTime)}</p>
        <p className="text-xs">Cancellation is allowed only before the booking starts.</p>
        {cancelError && <p role="alert" className="text-sm text-[#C62828]">{cancelError}</p>}
        <label className="text-xs block">Reason (optional)<textarea aria-label="Cancellation reason" className={`${inputClass} w-full block`} maxLength={500} value={reason} disabled={busy} onChange={e => setReason(e.target.value)} /></label>
        <div className="flex justify-end gap-3"><button disabled={busy} className={buttonClass} onClick={() => setCancelling(null)}>Keep reservation</button>
          <button disabled={busy} className="px-4 py-2 rounded-md text-sm bg-[#242424] text-white disabled:opacity-50" onClick={handleCancelBooking}>{busy ? 'Cancelling…' : 'Confirm cancellation'}</button></div>
      </section>
    </div>}
    {detailsId && <BookingHistoryDialog key={`${detailsId}/${reload}/${user?.id}/${user?.role}/${user?.department?.id}`} id={detailsId} onClose={() => setDetailsId(null)} />}
  </div>;
};

const BookingHistoryDialog = ({ id, onClose }: { id: string; onClose: () => void }) => {
  const [response, setResult] = useState<{ key: string; data?: BookingDetails; error?: string } | null>(null);
  const [retry, setRetry] = useState(0);
  const key = `${id}/${retry}`;
  const result = response?.key === key ? response : null;
  useEffect(() => {
    let active = true;
    getBooking(id).then(data => { if (active) setResult({ key, data }); })
      .catch(error => { if (active) setResult({ key, error: describeError(error) }); });
    return () => { active = false; };
  }, [id, retry, key]);
  return <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
    <section role="dialog" aria-modal="true" aria-label="Booking details and history" className="w-full max-w-lg max-h-[90vh] overflow-y-auto p-6 bg-[#FAFAF8] rounded-lg space-y-4">
      <div className="flex justify-between"><h2 className="font-editorial text-xl">Details & history</h2><button aria-label="Close history" onClick={onClose}>✕</button></div>
      {!result && <p role="status">Loading booking history…</p>}
      {result?.error && <div role="alert">{result.error} <button className="underline" onClick={() => setRetry(r => r + 1)}>Retry history</button></div>}
      {result?.data && <><p>{result.data.resource.name} · {result.data.status}</p>
        <p className="text-xs">Owner: {result.data.user.fullName} · Created by: {result.data.createdBy.fullName}</p>
        {result.data.cancelledBy && <p className="text-xs">Cancelled by: {result.data.cancelledBy.fullName}</p>}
        <ol className="space-y-4">{result.data.statusHistory.map(entry => <li key={entry.id} className="border-l-2 border-[#D97757] pl-3 text-sm">
          <p>{entry.oldStatus ? `${entry.oldStatus} → ` : ''}{entry.newStatus}</p><p className="text-xs text-[#8A8070]">{formatBookingDate(entry.changedAt)} {businessTime(entry.changedAt)} · {entry.changedBy?.fullName ?? 'System'}</p>
          {entry.reason && <p className="text-xs mt-1">{entry.reason}</p>}
        </li>)}</ol>
        {!result.data.statusHistory.length && <p className="text-sm">No history recorded.</p>}
      </>}
    </section>
  </div>;
};
