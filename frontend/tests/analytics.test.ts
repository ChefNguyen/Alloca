import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { analyticsApi } from '../src/services/analyticsApi.ts';
import { saveSession, clearSession } from '../src/lib/authStorage.ts';
import { ApiError } from '../src/lib/api.ts';

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
  clearSession();
});

const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

test('analyticsApi.getUtilization formats parameters and uses authorization', async () => {
  saveSession({ accessToken: 'test-token', user: null });
  let requestedUrl = '';

  globalThis.fetch = async (url, init) => {
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer test-token');
    requestedUrl = String(url);
    return json({ from: '2026-10-05', to: '2026-10-12', timezone: 'Asia/Ho_Chi_Minh', metrics: [] });
  };

  await analyticsApi.getUtilization({
    from: '2026-10-05',
    to: '2026-10-12',
    departmentId: 'dept-123',
    resourceId: 'res-456',
  });

  const parsed = new URL(requestedUrl, 'http://localhost');
  assert.equal(parsed.pathname, '/api/v1/analytics/utilization');
  assert.equal(parsed.searchParams.get('from'), '2026-10-05');
  assert.equal(parsed.searchParams.get('to'), '2026-10-12');
  assert.equal(parsed.searchParams.get('departmentId'), 'dept-123');
  assert.equal(parsed.searchParams.get('resourceId'), 'res-456');
});

test('analyticsApi.getBookingTrends passes local start period queries', async () => {
  saveSession({ accessToken: 'test-token', user: null });
  let requestedUrl = '';

  globalThis.fetch = async (url) => {
    requestedUrl = String(url);
    return json({ from: '2026-10-05', to: '2026-10-12', timezone: 'Asia/Ho_Chi_Minh', points: [] });
  };

  await analyticsApi.getBookingTrends({
    from: '2026-10-05',
    to: '2026-10-12',
  });

  const parsed = new URL(requestedUrl, 'http://localhost');
  assert.equal(parsed.pathname, '/api/v1/analytics/trends');
  assert.equal(parsed.searchParams.get('from'), '2026-10-05');
  assert.equal(parsed.searchParams.get('to'), '2026-10-12');
  assert.equal(parsed.searchParams.get('departmentId'), null);
});

test('analyticsApi.getAlerts formats status and department filters', async () => {
  saveSession({ accessToken: 'test-token', user: null });
  let requestedUrl = '';

  globalThis.fetch = async (url) => {
    requestedUrl = String(url);
    return json({ items: [], page: { totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true } });
  };

  await analyticsApi.getAlerts({
    status: 'OPEN',
    departmentId: 'dept-eng',
    page: 0,
    size: 20,
  });

  const parsed = new URL(requestedUrl, 'http://localhost');
  assert.equal(parsed.pathname, '/api/v1/alerts');
  assert.equal(parsed.searchParams.get('status'), 'OPEN');
  assert.equal(parsed.searchParams.get('departmentId'), 'dept-eng');
  assert.equal(parsed.searchParams.get('page'), '0');
  assert.equal(parsed.searchParams.get('size'), '20');
});

test('analyticsApi.acknowledgeAlert sends POST to acknowledge endpoint', async () => {
  saveSession({ accessToken: 'test-token', user: null });
  let requestedUrl = '';
  let requestMethod = '';

  globalThis.fetch = async (url, init) => {
    requestedUrl = String(url);
    requestMethod = init?.method || 'GET';
    return json({ id: 'alert-1', status: 'ACKNOWLEDGED' });
  };

  const res = await analyticsApi.acknowledgeAlert('alert-1');
  assert.equal(requestedUrl, '/api/v1/alerts/alert-1/acknowledge');
  assert.equal(requestMethod, 'POST');
  assert.equal(res.status, 'ACKNOWLEDGED');
});

test('analyticsApi handles 403 ACCESS_DENIED on scoped violations', async () => {
  saveSession({ accessToken: 'test-token', user: null });

  globalThis.fetch = async () =>
    json({ code: 'ACCESS_DENIED', message: 'Managers cannot view reports for other departments' }, 403);

  await assert.rejects(
    async () => {
      await analyticsApi.getUtilization({
        from: '2026-10-05',
        to: '2026-10-12',
        departmentId: 'dept-other',
      });
    },
    (err: unknown) => {
      assert.ok(err instanceof ApiError);
      assert.equal(err.status, 403);
      assert.equal(err.code, 'ACCESS_DENIED');
      return true;
    }
  );
});
