import { createStore } from 'solid-js';
import {
  type ApiOptions,
  api,
  apiEmpty,
  describe,
  hasCsrfToken,
  probeSession,
  refreshCsrfToken,
  type Session,
  statusOf,
} from './api';
import { config } from './config';

/** Thrown by call() when the session has ended. The sign in form shows by then. */
export class SessionExpired extends Error {}

export const isSessionExpired = (error: unknown): error is SessionExpired => error instanceof SessionExpired;

/** Thrown by login() and logout(), its message is the text to show. */
export class UserMessage extends Error {}

export const messageOf = (error: unknown): string =>
  error instanceof UserMessage ? error.message : describe(error, 'The request failed');

type Status = 'unknown' | 'signed-out' | 'ready' | 'error';

// The server writes the session into /config.js, so the browser knows it from the start. Without it, checkSession()
// asks the server
const [state, setState] = createStore<{
  status: Status;
  session: Session | null;
  error: string | null;
  notice: string | null;
}>({
  status: config ? (config.session ? 'ready' : 'signed-out') : 'unknown',
  session: config?.session ?? null,
  error: null,
  notice: null,
});

export const session = state;

export const isAdmin = () => state.session?.admin === true;

let checked: Promise<Status> | null = config ? Promise.resolve(state.status) : null;

/** Writes the outcome only once the server answered, so an async memo can call it. */
export function checkSession(force = false): Promise<Status> {
  if (force || !checked) checked = probe();
  return checked;
}

async function probe(): Promise<Status> {
  try {
    const value = await probeSession();
    const status = value ? 'ready' : 'signed-out';
    setState((draft) => {
      draft.status = status;
      draft.session = value;
      draft.error = null;
    });
    return status;
  } catch (error) {
    setState((draft) => {
      draft.status = 'error';
      draft.session = null;
      draft.error = describe(error, 'The session check failed');
    });
    return 'error';
  }
}

/** Ends the local session. Also used after a change that made the server invalidate it. */
export function signedOut(notice: string | null) {
  setState((draft) => {
    draft.status = 'signed-out';
    draft.session = null;
    draft.notice = notice;
  });
}

/** Runs an unsafe request, and retries it once with a fresh CSRF token if the server found the old one stale. */
async function withFreshCsrf(post: () => Promise<void>): Promise<void> {
  try {
    await post();
  } catch (error) {
    if (statusOf(error) !== 403) throw error;
    await refreshCsrfToken();
    await post();
  }
}

/** Signs in. Rejects with a UserMessage on failure. */
export async function login(username: string, password: string): Promise<void> {
  try {
    if (!hasCsrfToken()) await refreshCsrfToken();
    await withFreshCsrf(() =>
      apiEmpty('/login', { method: 'POST', body: new URLSearchParams({ username, password }) }),
    );
  } catch (error) {
    throw new UserMessage(statusOf(error) === 401 ? 'Invalid credentials' : describe(error, 'Sign in failed'));
  }
  setState((draft) => {
    draft.notice = null;
  });
  // The browser did not keep the session cookie, the form must not wait for a panel that never comes
  if ((await checkSession(true)) === 'signed-out')
    throw new UserMessage('Sign in failed, allow the cookies of this site.');
}

/** Signs out. Rejects with a UserMessage when the server did not end the session, which stays signed in. */
export async function logout(): Promise<void> {
  try {
    await withFreshCsrf(() => apiEmpty('/logout', { method: 'POST' }));
  } catch (error) {
    // A 401 means there is no session left to end.
    if (statusOf(error) !== 401) {
      throw new UserMessage(describe(error, 'Sign out failed', 'Could not reach the server, you are still signed in.'));
    }
  }
  signedOut('You are signed out.');
  // Fetches a fresh CSRF cookie for the next sign in.
  refreshCsrfToken().catch(() => {});
}

/** Turns a 401 into a SessionExpired, and into the sign in form if the session was thought to be alive. */
async function expiring<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (statusOf(error) !== 401) throw error;
    if (state.status === 'ready') signedOut('Your session expired, sign in again.');
    throw new SessionExpired();
  }
}

export const call = <T>(path: string, options?: ApiOptions): Promise<T> => expiring(api<T>(path, options));

/** Like call() for the endpoints without a response body. */
export const callEmpty = (path: string, options?: ApiOptions): Promise<void> => expiring(apiEmpty(path, options));
