import assert from 'node:assert/strict';
import { login } from '../src/services/authApi.ts';
import { loadSession } from '../src/lib/authStorage.ts';
import { businessDate, dateAfter } from '../src/lib/bookingTime.ts';
import { createBooking, listBookings, getBooking, getBookingHistory, getAvailability, cancelBooking, searchBookingResources } from '../src/services/bookingApi.ts';

// Run only through scripts/test-booking-live.ps1, which owns the isolated database/server.
const base = process.env.ALLOCA_SMOKE_BASE_URL;
if (!base || !['127.0.0.1', 'localhost'].includes(new URL(base).hostname)) throw new Error('A loopback test server is required.');
const nativeFetch = globalThis.fetch;
globalThis.fetch = (path, init = {}) => nativeFetch(new URL(String(path), base), {
  ...init, headers: { ...Object.fromEntries(new Headers(init.headers)), Origin: 'http://localhost:5173' },
});

await login('booking-smoke-owner', 'Only-Smoke-123!');
const ownerId = loadSession()?.user?.id;
assert.equal(ownerId, '00000000-0000-0000-0000-000000000101');
const resources = await searchBookingResources('Live smoke');
assert.equal(resources.items.length, 1);
const resourceId = resources.items[0].id;
const free = await getAvailability(resourceId, dateAfter(businessDate(), 1));
assert.ok(free.availableSlots.length > 0);
const slot = free.availableSlots[0];
const saved = await createBooking({ resourceId, startTime: slot.startTime, endTime: slot.endTime, purpose: 'Live frontend API smoke' });
assert.equal(saved.status, 'CONFIRMED');
assert.equal(saved.user.id, ownerId);
assert.equal(saved.createdBy.id, ownerId);
const listed = await listBookings({ userId: ownerId, size: 10 });
assert.equal(listed.page.totalElements, 1);
assert.equal(listed.items[0].id, saved.id);
const details = await getBooking(saved.id);
assert.equal(details.statusHistory.length, 1);
assert.equal(details.statusHistory[0].newStatus, 'CONFIRMED');
await cancelBooking(saved.id, 'Live smoke finished');
assert.equal((await getBooking(saved.id)).status, 'CANCELLED');
const history = await getBookingHistory(saved.id);
assert.equal(history.length, 2);
assert.equal(history[1].newStatus, 'CANCELLED');
assert.equal(history[1].reason, 'Live smoke finished');
const cancelled = await listBookings({ userId: ownerId, status: 'CANCELLED' });
assert.equal(cancelled.page.totalElements, 1);
const availableAgain = await getAvailability(resourceId, free.date);
assert.ok(availableAgain.availableSlots.some(value => value.startTime === slot.startTime));
console.log('PASS: real frontend modules -> HTTP/JWT -> PostgreSQL: login, resources, availability, create, list, detail, cancel, history, slot release.');
