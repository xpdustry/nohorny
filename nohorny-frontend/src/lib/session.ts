// The session of the signed in user, global to the app.

import { createStore } from 'solid-js';
import { type ApiOptions, api, describe, hasCsrfToken, probeSession, type Session, statusOf } from './api';

/** Thrown by call() when the session has ended. The sign in form shows by then. */
export class SessionExpired extends Error {}

type Status = 'unknown' | 'loading' | 'signed-out' | 'ready' | 'error';

const [state, setState] = createStore<{
  status: Status;
  session: Session | null;
  /** Why the session check failed, for the 'error' status. */
  error: string | null;
  /** Shown above the sign in form, such as an expired session. */
  notice: string | null;
}>({ status: 'unknown', session: null, error: null, notice: null });

export const session = state;

export const isAdmin = () => state.session?.admin === true;

let checked: Promise<void> | null = null;

/** Checks the session. Without `force`, it checks only once. */
export function checkSession(force = false): Promise<void> {
  if (force || !checked) checked = probe();
  return checked;
}

async function probe(): Promise<void> {
  setState((draft) => {
    draft.status = 'loading';
    draft.error = null;
  });
  try {
    const value = await probeSession();
    setState((draft) => {
      draft.status = value ? 'ready' : 'signed-out';
      draft.session = value;
    });
  } catch (error) {
    setState((draft) => {
      draft.status = 'error';
      draft.session = null;
      draft.error = describe(error, 'The session check failed');
    });
  }
}

function signedOut(notice: string | null) {
  setState((draft) => {
    draft.status = 'signed-out';
    draft.session = null;
    draft.notice = notice;
  });
}

/** Signs in. Rejects with the message to show on failure. */
export async function login(username: string, password: string): Promise<void> {
  const post = () => api('/login', { method: 'POST', body: new URLSearchParams({ username, password }) });
  try {
    // Any GET request sets the XSRF-TOKEN cookie.
    if (!hasCsrfToken()) await probeSession();
    try {
      await post();
    } catch (error) {
      if (statusOf(error) !== 403) throw error;
      // The CSRF token is stale. Fetch a fresh one and retry once.
      await probeSession();
      await post();
    }
  } catch (error) {
    throw new Error(statusOf(error) === 401 ? 'Invalid credentials' : describe(error, 'Sign in failed'));
  }
  setState((draft) => {
    draft.notice = null;
  });
  await checkSession(true);
}

export async function logout(): Promise<void> {
  try {
    await api('/logout', { method: 'POST' });
  } catch {
    // The local sign out happens anyway.
  }
  signedOut('You are signed out.');
  // Fetches a fresh CSRF cookie for the next sign in.
  probeSession().catch(() => {});
}

/** Ends the local session after a change that made the server invalidate it. */
export function expire(notice: string) {
  signedOut(notice);
}

/** Calls api() with the session. A 401 shows the sign in form and throws SessionExpired. */
export async function call<T = unknown>(path: string, options?: ApiOptions): Promise<T> {
  try {
    return await api<T>(path, options);
  } catch (error) {
    if (statusOf(error) !== 401) throw error;
    if (state.status === 'ready') signedOut('Your session expired, sign in again.');
    throw new SessionExpired();
  }
}
