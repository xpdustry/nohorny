// The NoHorny JSON API.

export type Rating = 'SAFE' | 'WARN' | 'NSFW';
export type Bucket = 'safe' | 'warn' | 'nsfw' | 'failed';
export type Ratings = Record<Bucket, number>;

export const BUCKETS: readonly Bucket[] = ['safe', 'warn', 'nsfw', 'failed'];

export interface Stats {
  total: number;
  ratings: Ratings;
  last24Hours: { total: number; ratings: Ratings };
  updatedAt: string;
}

export type HistoryRange = '24h' | '7d' | '30d' | '90d';

export interface History {
  range: HistoryRange;
  slot: 'hour' | 'day';
  slots: { start: string; ratings: Ratings }[];
}

/** The requests per listed Mindustry network without an account. The rest of `total` comes from users and anonymous clients. */
export interface Networks {
  range: HistoryRange;
  total: number;
  networks: { name: string; count: number }[];
}

export interface Retention {
  imageMillis: number;
  requestMillis: number;
}

export interface Step {
  classifier: string;
  rating: Rating | null;
  confidence: number | null;
  durationMillis: number;
  /** The details of a failure, only sent to the administrators. */
  error?: FailureDetails;
}

/** Why a classification failed. Only the administrators see it, it reveals the internals of the server. */
export interface FailureDetails {
  /** The name of the exception class. */
  type: string;
  stackTrace: string;
}

export interface Requester {
  type: 'user' | 'mindustry-network' | 'anonymous';
  /** The network of a listed Mindustry server if it has one, the username of a user for the administrators only. */
  name: string | null;
}

export interface Request {
  id: string;
  createdAt: string;
  durationMillis: number;
  successful: boolean;
  rating: Rating | null;
  confidence: number | null;
  classifier: string | null;
  version: string | null;
  requester: Requester;
  image: { state: 'none' | 'stored' | 'expired' | 'purged'; url?: string };
  steps: Step[];
  /** Only sent to the administrators. */
  remoteAddress?: string;
  /** The details of a failure, only sent to the administrators with `remoteAddress`. */
  error?: FailureDetails | null;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

export interface Session {
  username: string;
  admin: boolean;
  bootstrap: boolean;
}

export interface User {
  username: string;
  admin: boolean;
  /** Classifications per minute, shared by all the addresses of the account. */
  rateLimit: number;
  createdAt: string;
  bootstrap: boolean;
}

/** Thrown by api() for a non 2xx response. `status` is 0 when the request did not reach the server. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly serverMessage: string | null = null,
  ) {
    super(serverMessage ?? (status === 0 ? 'Could not reach the server.' : `HTTP ${status}`));
    this.name = 'ApiError';
  }
}

export function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null;
}

/** The `X-XSRF-TOKEN` header with the value of the `XSRF-TOKEN` cookie. It is empty without the cookie. */
function csrfHeaders(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

export function hasCsrfToken(): boolean {
  return 'X-XSRF-TOKEN' in csrfHeaders();
}

export interface ApiOptions extends Omit<RequestInit, 'body'> {
  /** Serialized as the JSON body. */
  json?: unknown;
  body?: BodyInit;
}

/** Fetches an API path and adds the CSRF header to unsafe methods. Resolves to the parsed body, or null if empty. */
export async function api<T = unknown>(path: string, options: ApiOptions = {}): Promise<T> {
  const { json, headers, ...rest } = options;
  const method = (rest.method ?? 'GET').toUpperCase();
  const init: RequestInit = {
    credentials: 'same-origin',
    ...rest,
    method,
    headers: {
      Accept: 'application/json',
      ...(method === 'GET' || method === 'HEAD' ? {} : csrfHeaders()),
      ...(json === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
  };
  if (json !== undefined) init.body = JSON.stringify(json);

  let response: Response;
  try {
    response = await fetch(path, init);
  } catch {
    throw new ApiError(0);
  }
  if (!response.ok) {
    let message: string | null = null;
    try {
      const body = await response.json();
      if (typeof body?.message === 'string' && body.message) message = body.message;
    } catch {
      // Not JSON.
    }
    throw new ApiError(response.status, message);
  }
  const text = await response.text();
  if (!text) return null as T;
  return ((response.headers.get('Content-Type') ?? '').includes('json') ? JSON.parse(text) : text) as T;
}

/**
 * The message to show for a failed call. It is `unreachable` when the request did not reach the server,
 * the message of the API when it sent one, and `fallback` with the HTTP status otherwise.
 */
export function describe(error: unknown, fallback: string, unreachable = 'Could not reach the server.'): string {
  if (!(error instanceof ApiError)) return 'Unexpected error, reload the page.';
  if (error.status === 0) return unreachable;
  if (error.status === 429) return 'Too many requests from your address, wait a moment and retry.';
  return error.serverMessage || `${fallback} (HTTP ${error.status}).`;
}

/** Returns the GET /api/session payload, or null when signed out. The response also sets the XSRF-TOKEN cookie. */
export async function probeSession(): Promise<Session | null> {
  try {
    return await api<Session>('/api/session');
  } catch (error) {
    if (statusOf(error) === 401) return null;
    throw error;
  }
}

export const requestPath = (id: string) => `/api/requests/${encodeURIComponent(id)}`;
export const userPath = (username: string) => `/api/users/${encodeURIComponent(username)}`;
