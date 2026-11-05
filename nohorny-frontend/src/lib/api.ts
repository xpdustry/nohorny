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

/**
 * The requests per listed Mindustry network and from the local addresses without an account. The rest of `total`
 * comes from users, unnamed listed servers and anonymous clients.
 */
export interface Networks {
  range: HistoryRange;
  total: number;
  networks: { name: string; count: number }[];
  /**
   * The requests not from a listed network, by requester type. `mindustry-network` counts the listed servers without
   * a network, UNRECORDED_REQUESTER the requests counted before the requesters were recorded.
   */
  requesters: { name: RequesterType | typeof UNRECORDED_REQUESTER; count: number }[];
}

/** The requester type of the requests counted before the requesters were recorded. */
export const UNRECORDED_REQUESTER = '' as const;

/** The requests per plugin version, the most used first. The rest of `total` comes from the clients sending none. */
export interface Versions {
  range: HistoryRange;
  total: number;
  versions: { name: string; count: number }[];
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

export type RequesterType = 'user' | 'mindustry-network' | 'local' | 'anonymous';

/**
 * Lists the requests of one requester. `name` is null for any name, empty for the requesters without one, like the
 * listed servers without a network, or the name itself. `label` describes it in the filters.
 */
export interface RequesterFilter {
  type: RequesterType;
  name: string | null;
  label: string;
}

/** Lists the requests of one plugin version, or of the clients sending none when `value` is empty. */
export interface VersionFilter {
  value: string;
  label: string;
}

export interface Requester {
  type: RequesterType;
  /** The network of a listed Mindustry server if it has one, the username of a user for the administrators only. */
  name: string | null;
}

/** The kind of buildings an image was made with, see ArtKind in nohorny-common. */
export type ArtKind = 'logic-display' | 'canvas' | 'sorter' | 'illuminator';

export const ART_KINDS: Record<ArtKind, string> = {
  'logic-display': 'Logic display',
  canvas: 'Canvas',
  sorter: 'Sorter',
  illuminator: 'Illuminator',
};

export interface Request {
  id: string;
  createdAt: string;
  durationMillis: number;
  successful: boolean;
  rating: Rating | null;
  confidence: number | null;
  classifier: string | null;
  version: string | null;
  /** The kind of buildings the image was made with, `null` for the older plugins. */
  kind: ArtKind | null;
  requester: Requester;
  image: { state: 'none' | 'stored' | 'expired' | 'deleted'; url?: string };
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

/** Gives the clients of an address range another requester when they send no account. The most specific range wins. */
export interface Mapping {
  id: string;
  /** The normalized CIDR notation, such as `172.16.0.0/12`. */
  range: string;
  type: RequesterType;
  /** The username of a user or the network name, null for the other types. */
  name: string | null;
  createdAt: string;
}

/** Thrown by api() for a non 2xx response. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly serverMessage: string | null = null,
  ) {
    super(serverMessage ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

/** Thrown by api() when the request did not reach the server. */
export class NetworkError extends Error {
  constructor() {
    super('Could not reach the server.');
    this.name = 'NetworkError';
  }
}

export function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null;
}

function csrfHeaders(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

export function hasCsrfToken(): boolean {
  return 'X-XSRF-TOKEN' in csrfHeaders();
}

export interface ApiOptions extends Omit<RequestInit, 'body'> {
  json?: unknown;
  body?: BodyInit;
}

/** Fetches an API path and resolves to its parsed body, which the endpoint must send. */
export async function api<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const body = await fetchBody(path, options);
  if (body === null) throw new Error(`${path} answered without a body.`);
  return body as T;
}

/** Fetches an API path whose response has no body, such as a 204, and ignores any it has. */
export async function apiEmpty(path: string, options: ApiOptions = {}): Promise<void> {
  await fetchBody(path, options);
}

/** Adds the CSRF header to unsafe methods. Resolves to the parsed body, or null if empty. */
async function fetchBody(path: string, options: ApiOptions): Promise<unknown> {
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
    throw new NetworkError();
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
  if (!text) return null;
  return (response.headers.get('Content-Type') ?? '').includes('json') ? JSON.parse(text) : text;
}

export function describe(error: unknown, fallback: string, unreachable = 'Could not reach the server.'): string {
  if (error instanceof NetworkError) return unreachable;
  if (!(error instanceof ApiError)) return 'Unexpected error, reload the page.';
  if (error.status === 429) return 'Too many requests from your address, wait a moment and retry.';
  return error.serverMessage || `${fallback} (HTTP ${error.status}).`;
}

/** The response also sets the XSRF-TOKEN cookie. */
export async function probeSession(): Promise<Session | null> {
  try {
    return await api<Session>('/api/session');
  } catch (error) {
    if (statusOf(error) === 401) return null;
    throw error;
  }
}

/** Fetches a fresh XSRF-TOKEN cookie, any GET request sets it. */
export async function refreshCsrfToken(): Promise<void> {
  await probeSession();
}

export const requestPath = (id: string) => `/api/requests/${encodeURIComponent(id)}`;
export const userPath = (username: string) => `/api/users/${encodeURIComponent(username)}`;
export const mappingPath = (id: string) => `/api/mappings/${encodeURIComponent(id)}`;
