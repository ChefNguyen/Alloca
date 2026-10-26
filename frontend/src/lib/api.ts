import { clearSession, loadSession, saveSession, SESSION_EXPIRED_EVENT } from '@/lib/authStorage';

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

const PUBLIC_PATHS = ['/api/v1/auth/login', '/api/v1/auth/refresh'];

const send = async (path: string, init: RequestInit, accessToken: string | null): Promise<Response> => {
  try {
    return await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...init.headers,
      },
    });
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: 'Cannot reach the server. Is the backend running?' });
  }
};

// One refresh at a time, even when several requests get a 401 together.
let refreshing: Promise<string | null> | null = null;

const refreshAccessToken = (): Promise<string | null> => {
  refreshing ??= (async () => {
    const session = loadSession();
    if (!session) return null;
    try {
      const response = await send(
        '/api/v1/auth/refresh',
        { method: 'POST', body: JSON.stringify({ refreshToken: session.refreshToken }) },
        null,
      );
      if (!response.ok) return null;
      const { accessToken } = (await response.json()) as { accessToken: string };
      saveSession({ ...session, accessToken });
      return accessToken;
    } catch {
      return null;
    }
  })().finally(() => {
    refreshing = null;
  });
  return refreshing;
};

export const apiRequest = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  let response = await send(path, init, loadSession()?.accessToken ?? null);

  if (response.status === 401 && !PUBLIC_PATHS.includes(path)) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await send(path, init, refreshed);
    } else {
      clearSession();
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
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
