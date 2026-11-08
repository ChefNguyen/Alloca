import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { MyBookings } from '../src/pages/MyBookings';
import { BookingModal } from '../src/components/booking/BookingModal';
import { ApiError } from '../src/lib/api';
import { businessDate, dateAfter } from '../src/lib/bookingTime';
import * as api from '../src/services/bookingApi';

const state = vi.hoisted(() => ({ user: null as any, revision: 0, open: vi.fn() }));
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({ user: state.user }) }));
vi.mock('react-router-dom', async original => ({ ...await original<typeof import('react-router-dom')>(),
  useOutletContext: () => ({ bookingRevision: state.revision, onOpenBookingModal: state.open }),
}));
vi.mock('../src/services/bookingApi', () => ({ listBookings: vi.fn(), getBooking: vi.fn(), cancelBooking: vi.fn(),
  getAvailability: vi.fn(), createBooking: vi.fn(), searchBookingResources: vi.fn(), searchBookingOwners: vi.fn() }));

const owner = { id: 'owner', username: 'owner', fullName: 'Owner', role: 'EMPLOYEE', department: null };
const resource = { id: 'room', name: 'Real room', type: 'ROOM', capacity: 8, status: 'AVAILABLE', zone: null, department: null, description: null, utilizationThreshold: 85, mapPosition: null } as any;
const tomorrow = dateAfter(businessDate(), 1);
const slot = (time: string) => new Date(`${tomorrow}T${time}:00+07:00`).toISOString();
const booking = { id: 'saved-booking', resource, user: owner, createdBy: owner, cancelledBy: null, cancelReason: null,
  purpose: 'Real reservation', status: 'CONFIRMED', startTime: slot('09:00'), endTime: slot('10:00'), createdAt: new Date().toISOString() } as any;
const page = (items = [booking], extra = {}) => ({ items, page: { number: 0, size: 10, totalElements: items.length, totalPages: 1, first: true, last: true, ...extra } });
const availability = { resourceId: 'room', date: tomorrow, timezone: 'Asia/Ho_Chi_Minh', resourceStatus: 'AVAILABLE', operatingWindows: [],
  availableSlots: [{ startTime: slot('09:00'), endTime: slot('09:30') }, { startTime: slot('09:30'), endTime: slot('10:00') }] };

beforeEach(() => {
  vi.resetAllMocks(); state.user = owner; state.revision = 0;
  vi.mocked(api.listBookings).mockResolvedValue(page() as any);
  vi.mocked(api.getAvailability).mockResolvedValue(availability);
  vi.mocked(api.getBooking).mockResolvedValue({ ...booking, statusHistory: [{ id: 'h1', oldStatus: null, newStatus: 'CONFIRMED', changedBy: owner, reason: 'Initial booking', changedAt: slot('08:00') }] });
  vi.mocked(api.searchBookingResources).mockResolvedValue(page([resource]) as any);
  vi.mocked(api.searchBookingOwners).mockResolvedValue(page([{ ...owner, id: 'delegate', username: 'colleague', isActive: true }]) as any);
  vi.mocked(api.createBooking).mockResolvedValue(booking);
  vi.mocked(api.cancelBooking).mockResolvedValue(undefined);
});
afterEach(cleanup);
const renderList = () => render(<MemoryRouter><MyBookings /></MemoryRouter>);
const renderForm = (extra: object = {}) => {
  const onSuccess = vi.fn(); const onClose = vi.fn();
  render(<BookingModal isOpen resources={[resource]} initialResourceId="room" onClose={onClose} onBookingSuccess={onSuccess} {...extra} />);
  fireEvent.change(screen.getByLabelText('Date'), { target: { value: tomorrow } });
  return { onSuccess, onClose };
};
const chooseTime = async () => {
  await waitFor(() => expect(within(screen.getByLabelText('Start time')).getByRole('option', { name: '09:00' })).toBeTruthy());
  fireEvent.change(screen.getByLabelText('Start time'), { target: { value: slot('09:00') } });
  fireEvent.change(screen.getByLabelText('End time'), { target: { value: slot('10:00') } });
};

describe('My Bookings with real asynchronous API commands', () => {
  it('loads authenticated own bookings and requests status/page filters from the server', async () => {
    vi.mocked(api.listBookings).mockResolvedValue(page([booking], { last: false, totalPages: 2, totalElements: 11 }) as any);
    renderList();
    expect(screen.getByRole('status').textContent).toContain('Loading');
    await screen.findByText('Real reservation');
    expect(api.listBookings).toHaveBeenCalledWith(expect.objectContaining({ userId: 'owner', page: 0 }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => expect(api.listBookings).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'CANCELLED' } });
    await waitFor(() => expect(api.listBookings).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, status: 'CANCELLED' })));
  });

  it('supports Manager department scope and applies UTC+7 date boundaries', async () => {
    state.user = { ...owner, role: 'MANAGER', department: { id: 'A' } };
    renderList(); await screen.findByText('Real reservation');
    fireEvent.change(screen.getByLabelText('Scope'), { target: { value: 'permitted' } });
    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2030-06-10' } });
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2030-06-10' } });
    await waitFor(() => expect(api.listBookings).toHaveBeenLastCalledWith(expect.objectContaining({ from: '2030-06-09T17:00:00.000Z', to: '2030-06-10T17:00:00.000Z' })));
    expect(vi.mocked(api.listBookings).mock.calls.at(-1)?.[0]).not.toHaveProperty('userId');
  });

  it('refreshes after creation and shows history fetched from the detail endpoint', async () => {
    const result = renderList(); await screen.findByText('Real reservation');
    await userEvent.click(screen.getByRole('button', { name: 'Details & history' }));
    await screen.findByText('Initial booking'); expect(api.getBooking).toHaveBeenCalledWith(booking.id);
    state.revision = 1; result.rerender(<MemoryRouter><MyBookings /></MemoryRouter>);
    await waitFor(() => expect(api.listBookings).toHaveBeenCalledTimes(2));
  });

  it('waits for successful cancellation, submits reason and refetches server state', async () => {
    vi.mocked(api.listBookings).mockResolvedValueOnce(page() as any).mockResolvedValue(page([{ ...booking, status: 'CANCELLED', cancelReason: 'Changed plans' }]) as any);
    renderList(); await screen.findByText('Real reservation');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel reservation' }));
    fireEvent.change(screen.getByLabelText('Cancellation reason'), { target: { value: 'Changed plans' } });
    await userEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    await screen.findByText('Cancellation reason: Changed plans');
    expect(api.cancelBooking).toHaveBeenCalledWith(booking.id, 'Changed plans');
    expect(api.listBookings).toHaveBeenCalledTimes(2);
  });

  it('does not announce success on a 409 cancellation and shows the error for retry', async () => {
    vi.mocked(api.cancelBooking).mockRejectedValue(new ApiError(409, { message: 'Booking already started' }));
    renderList(); await screen.findByText('Real reservation');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel reservation' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Booking already started');
    expect(screen.queryByText(/was cancelled/)).toBeNull();
  });

  it('hides cancellation after start and renders explicit error/empty states', async () => {
    vi.mocked(api.listBookings).mockResolvedValueOnce(page([{ ...booking, startTime: '2000-01-01T02:00:00Z' }]) as any);
    renderList(); await screen.findByText('Real reservation');
    expect(screen.queryByRole('button', { name: 'Cancel reservation' })).toBeNull();
    vi.mocked(api.listBookings).mockRejectedValueOnce(new ApiError(500, { message: 'Unavailable' }));
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }));
    await screen.findByRole('alert');
    vi.mocked(api.listBookings).mockResolvedValueOnce(page([]) as any);
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await screen.findByText('No reservations found for these filters.');
  });
});

describe('Booking form API integration', () => {
  it('creates a persisted reservation with UTC timestamps and no fabricated owner/creator', async () => {
    const { onSuccess } = renderForm(); await chooseTime();
    fireEvent.change(screen.getByLabelText('Purpose'), { target: { value: 'Planning' } });
    await userEvent.click(screen.getByRole('button', { name: 'Confirm reservation' }));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith(booking));
    expect(api.createBooking).toHaveBeenCalledWith({ resourceId: 'room', startTime: slot('09:00'), endTime: slot('10:00'), purpose: 'Planning' });
    expect(screen.queryByLabelText('Book for')).toBeNull();
  });

  it('refreshes after conflict and never calls the success callback', async () => {
    vi.mocked(api.createBooking).mockRejectedValue(new ApiError(409, { code: 'BOOKING_TIME_CONFLICT' }));
    const { onSuccess } = renderForm(); await chooseTime();
    const calls = vi.mocked(api.getAvailability).mock.calls.length;
    await userEvent.click(screen.getByRole('button', { name: 'Confirm reservation' }));
    expect((await screen.findByRole('alert')).textContent).toContain('just booked');
    await waitFor(() => expect(api.getAvailability).toHaveBeenCalledTimes(calls + 1));
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('supports authorized delegated booking from real user search', async () => {
    state.user = { ...owner, role: 'MANAGER', department: { id: 'A' } };
    renderForm(); await chooseTime();
    const colleague = await screen.findByRole('option', { name: 'colleague' });
    expect(colleague).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Book for'), { target: { value: 'delegate' } });
    await userEvent.click(screen.getByRole('button', { name: 'Confirm reservation' }));
    await waitFor(() => expect(api.createBooking).toHaveBeenCalledWith(expect.objectContaining({ userId: 'delegate' })));
  });

  it('does not let an old availability response replace a newer date selection', async () => {
    let resolveOld!: (value: any) => void;
    vi.mocked(api.getAvailability).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
    renderForm(); await chooseTime();
    resolveOld({ ...availability, availableSlots: [] });
    await waitFor(() => expect(within(screen.getByLabelText('Start time')).getByRole('option', { name: '09:00' })).toBeTruthy());
    expect(screen.queryByText(/No bookable slots/)).toBeNull();
  });

  it('loads global picker resources from the server and handles unavailable days', async () => {
    vi.mocked(api.getAvailability).mockResolvedValue({ ...availability, availableSlots: [] });
    renderForm({ resources: undefined, initialResourceId: undefined });
    await screen.findByRole('option', { name: /Real room/ });
    fireEvent.change(screen.getByLabelText('Resource'), { target: { value: 'room' } });
    await screen.findByText(/No bookable slots/);
    expect((screen.getByRole('button', { name: 'Confirm reservation' }) as HTMLButtonElement).disabled).toBe(true);
    expect(api.createBooking).not.toHaveBeenCalled();
  });

  it('prevents selecting stale picker results while a new search is loading', async () => {
    state.user = { ...owner, role: 'ADMIN' };
    renderForm({ resources: undefined, initialResourceId: undefined });
    await screen.findByRole('option', { name: /Real room/ });
    await screen.findByRole('option', { name: 'colleague' });
    fireEvent.change(screen.getByLabelText('Find resource'), { target: { value: 'New search' } });
    fireEvent.change(screen.getByLabelText('Find a person'), { target: { value: 'Another person' } });
    expect((screen.getByLabelText('Resource') as HTMLSelectElement).disabled).toBe(true);
    expect((screen.getByLabelText('Book for') as HTMLSelectElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Confirm reservation' }) as HTMLButtonElement).disabled).toBe(true);
  });
});
