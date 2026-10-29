import { clearSession, loadSession, saveSession, getSessionVersion } from './authStorage.ts';

export interface ApiFieldError {
  field: string;
  message: string;
}

// Mirrors ErrorResponse in docs/api/openapi.yaml
interface ErrorBody {
  code?: string;
  message?: string;
  fieldErrors?: ApiFieldError[] | null;
  details?: Record<string, unknown> | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ApiFieldError[];
  readonly details: Record<string, unknown>;

  constructor(status: number, body: ErrorBody) {
    super(body.message || `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? 'UNKNOWN_ERROR';
    this.fieldErrors = body.fieldErrors ?? [];
    this.details = body.details ?? {};
  }
}

const PUBLIC_PATHS = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout'];

const send = async (path: string, init: RequestInit, accessToken: string | null): Promise<Response> => {
  try {
    return await fetch(path, {
      ...init,
      credentials: 'include',
      cache: 'no-store',
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...init.headers,
        ...(PUBLIC_PATHS.includes(path) ? { 'X-Requested-With': 'Alloca' } : {}),
      },
    });
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: 'Cannot reach the server. Is the backend running?' });
  }
};

// One refresh at a time, even when several requests get a 401 together.
let refreshing: { version: number; promise: Promise<string | null> } | null = null;

export const refreshAccessToken = (): Promise<string | null> => {
  const version = getSessionVersion();
  if (refreshing?.version === version) return refreshing.promise;
  const promise = (async () => {
      const response = await send(
        '/api/v1/auth/refresh',
        { method: 'POST' },
        null,
      );
      if (getSessionVersion() !== version) return null;
      if (response.status === 401) {
        clearSession();
        return null;
      }
      if (!response.ok) throw new ApiError(response.status, await response.json().catch(() => ({})));
      const { accessToken } = (await response.json()) as { accessToken: string };
      if (getSessionVersion() !== version) return null;
      saveSession({ accessToken, user: loadSession()?.user ?? null });
      return accessToken;
  })().finally(() => {
    if (refreshing?.promise === promise) refreshing = null;
  });
  refreshing = { version, promise };
  return promise;
};

export const apiRequest = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const version = getSessionVersion();
  const token = PUBLIC_PATHS.includes(path) ? null : loadSession()?.accessToken ?? null;
  let response = await send(path, init, token);

  if (response.status === 401 && token && getSessionVersion() === version) {
    const current = loadSession()?.accessToken;
    const refreshed = current && current !== token ? current : await refreshAccessToken();
    if (refreshed) {
      response = await send(path, init, refreshed);
    }
  }

  if (response.status === 401 && !PUBLIC_PATHS.includes(path) && getSessionVersion() === version) {
    clearSession();
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, body ?? {});
  }
  return body as T;
};
