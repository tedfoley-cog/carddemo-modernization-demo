export interface LegacyError {
  message: string;
  field?: string | null;
  legacyParagraph?: string | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly field?: string | null;
  readonly legacyParagraph?: string | null;

  constructor(status: number, body: LegacyError) {
    super(body.message);
    this.status = status;
    this.field = body.field;
    this.legacyParagraph = body.legacyParagraph;
  }
}

const TOKEN_KEY = 'carddemo.session';

export interface Session {
  token: string;
  userId: string;
  userType: string;
  displayName: string;
  nextProgram: string;
}

export function getSession(): Session | null {
  const raw = sessionStorage.getItem(TOKEN_KEY);
  return raw ? (JSON.parse(raw) as Session) : null;
}

export function setSession(s: Session | null): void {
  if (s) sessionStorage.setItem(TOKEN_KEY, JSON.stringify(s));
  else sessionStorage.removeItem(TOKEN_KEY);
}

type Query = Record<string, string | number | boolean | undefined | null>;

function url(path: string, query?: Query): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null) params.set(k, String(v));
  });
  const qs = params.toString();
  return `/api/v1${path}${qs ? `?${qs}` : ''}`;
}

async function request<T>(method: string, path: string, opts: { query?: Query; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const session = getSession();
  if (session) headers.Authorization = `Bearer ${session.token}`;
  const res = await fetch(url(path, opts.query), {
    method,
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    if (res.status === 401 && session && path !== '/auth/signon') {
      setSession(null);
      window.location.assign('/signon');
    }
    throw new ApiError(res.status, data ?? { message: res.statusText });
  }
  return data as T;
}

export const api = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown, query?: Query) => request<T>('POST', path, { body, query }),
  put: <T>(path: string, body?: unknown, query?: Query) => request<T>('PUT', path, { body, query }),
  del: <T>(path: string, query?: Query) => request<T>('DELETE', path, { query }),
};
