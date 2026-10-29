import { afterEach, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { apiRequest, ApiError, refreshAccessToken } from '../src/lib/api.ts';
import { clearSession, loadSession, saveSession, subscribeSession, removeLegacySession } from '../src/lib/authStorage.ts';
import { login, logout, restoreSession } from '../src/services/authApi.ts';

const originalFetch = globalThis.fetch;
const user = { id: '1', username: 'test', fullName: 'Test', email: 'test@example.com', role: 'EMPLOYEE' as const,
  isActive: true, department: null, createdAt: '2026-10-28T00:00:00Z' };
const responseUser = { ...user, fullname: user.fullName };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});
beforeEach(() => clearSession());
afterEach(() => { globalThis.fetch = originalFetch; clearSession(); });

test('session is memory-only and removes legacy persisted tokens without reading them', () => {
  const removed: string[] = [];
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: () => { throw new Error('Must not read credentials'); },
    setItem: () => { throw new Error('Must not persist credentials'); },
    removeItem: (key: string) => removed.push(key),
  } });
  let updates = 0;
  const unsubscribe = subscribeSession(() => updates++);
  removeLegacySession();
  saveSession({ accessToken: 'access', user });
  assert.equal(loadSession()?.accessToken, 'access');
  clearSession();
  assert.equal(loadSession(), null);
  assert.equal(updates, 2);
  assert.ok(removed.includes('alloca.session'));
  unsubscribe();
  delete globalThis.localStorage;
});

test('login sends credentials with CSRF header, never sends stale bearer or stores refresh token', async () => {
  saveSession({ accessToken: 'old', user });
  globalThis.fetch = async (path, init) => {
    assert.equal(path, '/api/v1/auth/login');
    assert.equal(init?.credentials, 'include');
    assert.equal(new Headers(init?.headers).get('X-Requested-With'), 'Alloca');
    assert.equal(new Headers(init?.headers).get('Authorization'), null);
    return json({ accessToken: 'new', refreshToken: 'must-not-store', user: responseUser });
  };
  await login('test', 'password');
  assert.equal(loadSession()?.accessToken, 'new');
  assert.equal(loadSession()?.user?.fullName, 'Test');
  assert.equal('refreshToken' in loadSession()!, false);
});

test('reload restores through cookie refresh then /me, concurrent startup shares a request', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (path, init) => {
    calls.push(String(path));
    if (path === '/api/v1/auth/refresh') {
      assert.equal(init?.body, undefined);
      assert.equal(init?.credentials, 'include');
      assert.equal(new Headers(init?.headers).get('X-Requested-With'), 'Alloca');
      return json({ accessToken: 'restored' });
    }
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer restored');
    return json(responseUser);
  };
  await Promise.all([restoreSession(), restoreSession()]);
  assert.deepEqual(calls, ['/api/v1/auth/refresh', '/api/v1/auth/me']);
  assert.equal(loadSession()?.user?.id, '1');
});

test('missing or expired cookie leaves reload signed out without calling /me', async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; return json({ code: 'AUTH_TOKEN_INVALID' }, 401); };
  await restoreSession();
  assert.equal(calls, 1);
  assert.equal(loadSession(), null);
});

test('concurrent expired access tokens perform one refresh and retry with the new token', async () => {
  saveSession({ accessToken: 'expired', user });
  let refreshes = 0;
  globalThis.fetch = async (path, init) => {
    if (path === '/api/v1/auth/refresh') {
      refreshes++;
      await new Promise((resolve) => setTimeout(resolve, 10));
      return json({ accessToken: 'fresh' });
    }
    return new Headers(init?.headers).get('Authorization') === 'Bearer fresh'
      ? json({ ok: true }) : json({}, 401);
  };
  const results = await Promise.all([apiRequest('/api/v1/resources'), apiRequest('/api/v1/users')]);
  assert.deepEqual(results, [{ ok: true }, { ok: true }]);
  assert.equal(refreshes, 1);
});

test('failed refresh clears memory and does not retry the protected request', async () => {
  saveSession({ accessToken: 'expired', user });
  let calls = 0;
  globalThis.fetch = async () => { calls++; return json({}, 401); };
  await assert.rejects(apiRequest('/api/v1/resources'), (error: ApiError) => error.status === 401);
  assert.equal(loadSession(), null);
  assert.equal(calls, 2);
});

test('401 after a successful refresh clears the session without an infinite refresh loop', async () => {
  saveSession({ accessToken: 'expired', user });
  let calls = 0;
  globalThis.fetch = async (path) => {
    calls++;
    return path === '/api/v1/auth/refresh' ? json({ accessToken: 'new' }) : json({}, 401);
  };
  await assert.rejects(apiRequest('/api/v1/resources'));
  assert.equal(loadSession(), null);
  assert.equal(calls, 3);
});

test('logout asks server to expire the cookie without sending an access token', async () => {
  saveSession({ accessToken: 'access', user });
  globalThis.fetch = async (path, init) => {
    assert.equal(path, '/api/v1/auth/logout');
    assert.equal(init?.credentials, 'include');
    assert.equal(new Headers(init?.headers).get('Authorization'), null);
    assert.equal(new Headers(init?.headers).get('X-Requested-With'), 'Alloca');
    assert.equal(loadSession(), null);
    return new Response(null, { status: 204 });
  };
  await logout();
});

test('a late refresh response cannot resurrect a logged-out session', async () => {
  saveSession({ accessToken: 'old', user });
  let finish!: (response: Response) => void;
  globalThis.fetch = async (path) => path === '/api/v1/auth/refresh'
    ? new Promise<Response>((resolve) => { finish = resolve; })
    : new Response(null, { status: 204 });
  const pending = refreshAccessToken();
  await logout();
  finish(json({ accessToken: 'late' }));
  assert.equal(await pending, null);
  assert.equal(loadSession(), null);
});

test('a late refresh cannot overwrite a newly logged-in account', async () => {
  saveSession({ accessToken: 'old', user });
  let finish!: (response: Response) => void;
  globalThis.fetch = async (path) => path === '/api/v1/auth/refresh'
    ? new Promise<Response>((resolve) => { finish = resolve; })
    : json({ accessToken: 'new-account', user: responseUser });
  const pending = refreshAccessToken();
  await login('other', 'password');
  finish(json({ accessToken: 'old-account-refresh' }));
  await pending;
  assert.equal(loadSession()?.accessToken, 'new-account');
});

test('network/server errors are retryable and do not masquerade as expired credentials', async () => {
  saveSession({ accessToken: 'existing', user });
  globalThis.fetch = async () => { throw new Error('offline'); };
  await assert.rejects(refreshAccessToken(), (error: ApiError) => error.code === 'NETWORK_ERROR');
  assert.equal(loadSession()?.accessToken, 'existing');
  globalThis.fetch = async () => json({}, 503);
  await assert.rejects(refreshAccessToken(), (error: ApiError) => error.status === 503);
  assert.equal(loadSession()?.accessToken, 'existing');
});

test('logout failure is reported so UI can retry clearing the HttpOnly cookie', async () => {
  saveSession({ accessToken: 'access', user });
  globalThis.fetch = async () => { throw new Error('offline'); };
  await assert.rejects(logout(), (error: ApiError) => error.code === 'NETWORK_ERROR');
  assert.equal(loadSession(), null);
});

test('a late protected 401 after logout does not use the cookie to sign back in', async () => {
  saveSession({ accessToken: 'old', user });
  let finish!: (response: Response) => void;
  let refreshes = 0;
  globalThis.fetch = async (path) => {
    if (path === '/api/v1/resources') return new Promise<Response>((resolve) => { finish = resolve; });
    if (path === '/api/v1/auth/refresh') refreshes++;
    return new Response(null, { status: 204 });
  };
  const pending = apiRequest('/api/v1/resources');
  await logout();
  finish(json({}, 401));
  await assert.rejects(pending);
  assert.equal(refreshes, 0);
  assert.equal(loadSession(), null);
});

test('deactivation during startup leaves user signed out when /me rejects the new access token', async () => {
  globalThis.fetch = async (path) => path === '/api/v1/auth/refresh'
    ? json({ accessToken: 'new' }) : json({}, 401);
  await restoreSession();
  assert.equal(loadSession(), null);
});
