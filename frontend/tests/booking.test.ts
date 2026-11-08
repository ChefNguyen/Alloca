import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { availableEnds, businessDate, businessTime, dateAfter, localBookingInstant } from '../src/lib/bookingTime.ts';
import { cancelBooking, createBooking, getAvailability, getBooking, getBookingHistory, listBookings, searchBookingResources, searchBookingOwners } from '../src/services/bookingApi.ts';
import { saveSession, clearSession } from '../src/lib/authStorage.ts';
import { ApiError } from '../src/lib/api.ts';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; clearSession(); });
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });

test('business date/time and form timestamps consistently use UTC+7 rather than browser timezone', () => {
  assert.equal(businessDate(new Date('2030-06-09T18:00:00Z')), '2030-06-10');
  assert.equal(businessTime('2030-06-10T02:00:00Z'), '09:00');
  assert.equal(localBookingInstant('2030-06-10', '09:00'), '2030-06-10T02:00:00.000Z');
  assert.equal(dateAfter('2030-06-30', 1), '2030-07-01');
  assert.throws(() => localBookingInstant('2030-06-10', '09:15'));
  assert.throws(() => localBookingInstant('2030-02-30', '09:00'));
});

test('end choices stop at a booked gap and at the eight-hour limit', () => {
  const start = Date.parse('2030-06-10T01:00:00Z');
  const slots = Array.from({ length: 18 }, (_, i) => ({ startTime: new Date(start + i * 1800000).toISOString(), endTime: new Date(start + (i + 1) * 1800000).toISOString() }));
  assert.equal(availableEnds(slots, slots[0].startTime).length, 16);
  assert.deepEqual(availableEnds([slots[0], slots[2]], slots[0].startTime), [slots[0].endTime]);
  assert.deepEqual(availableEnds(slots, ''), []);
});

test('booking reads preserve pagination/filter fields and use the authenticated request layer', async () => {
  saveSession({ accessToken: 'access', user: null });
  const calls: string[] = [];
  globalThis.fetch = async (path, init) => {
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer access');
    calls.push(String(path)); return json({ items: [], page: { totalElements: 0 } });
  };
  await listBookings({ page: 2, size: 10, status: 'CANCELLED', userId: 'owner', from: '2030-06-10T00:00:00+07:00' });
  const params = new URL(calls[0], 'http://test').searchParams;
  assert.equal(params.get('page'), '2'); assert.equal(params.get('userId'), 'owner');
  assert.equal(params.get('from'), '2030-06-10T00:00:00+07:00');
  await getBooking('id'); await getBookingHistory('id'); await getAvailability('resource', '2030-06-10');
  assert.equal(calls[1], '/api/v1/bookings/id'); assert.equal(calls[2], '/api/v1/bookings/id/history');
  assert.ok(calls[3].includes('slotMinutes=30'));
});

test('create sends only command fields and cancellation accepts the real 204 response', async () => {
  const input = { resourceId: 'room', userId: 'delegate', startTime: '2030-06-10T02:00:00Z', endTime: '2030-06-10T03:00:00Z' };
  globalThis.fetch = async (path, init) => {
    assert.equal(init?.method, 'POST');
    if (path === '/api/v1/bookings') { assert.deepEqual(JSON.parse(String(init?.body)), input); return json({ id: 'saved' }, 201); }
    assert.equal(path, '/api/v1/bookings/saved/cancel');
    assert.deepEqual(JSON.parse(String(init?.body)), { reason: 'Changed plans' });
    return new Response(null, { status: 204 });
  };
  assert.equal((await createBooking(input)).id, 'saved');
  assert.equal(await cancelBooking('saved', 'Changed plans'), undefined);
});

test('conflict/permission errors propagate without synthesizing a successful booking', async () => {
  for (const status of [403, 409]) {
    globalThis.fetch = async () => json({ code: status === 409 ? 'BOOKING_TIME_CONFLICT' : 'ACCESS_DENIED', message: 'Rejected' }, status);
    await assert.rejects(createBooking({ resourceId: 'r', startTime: 's', endTime: 'e' }), (error: ApiError) => error.status === status);
    await assert.rejects(cancelBooking('id'), (error: ApiError) => error.status === status);
  }
});

test('resource and delegate pickers request server-filtered pages rather than a mock catalog', async () => {
  const calls: URL[] = [];
  globalThis.fetch = async path => { calls.push(new URL(String(path), 'http://test')); return json({ items: [], page: {} }); };
  await searchBookingResources('Room & desk', 1); await searchBookingOwners('Jane', 2);
  assert.equal(calls[0].searchParams.get('q'), 'Room & desk');
  assert.equal(calls[0].searchParams.get('status'), 'AVAILABLE');
  assert.equal(calls[0].searchParams.get('page'), '1');
  assert.equal(calls[1].searchParams.get('isActive'), 'true'); assert.equal(calls[1].searchParams.get('page'), '2');
});
