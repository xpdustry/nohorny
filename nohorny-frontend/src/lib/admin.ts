// State shared by the admin pages while signed in.

import { createContext, createSignal } from 'solid-js';
import type { Stats } from './api';
import { call } from './session';
import { createLiveStats, type LiveStats } from './stats';

export interface Admin {
  stats: LiveStats;
  /** Outcome of the last action, shown as a dismissable notice at the bottom of the screen. */
  status: () => Status | null;
  /** Shows the outcome of an action. Pass null to clear it. */
  notify: (text: string | null, tone?: Status['tone']) => void;
}

export interface Status {
  tone: 'success' | 'error';
  text: string;
}

export const AdminContext = createContext<Admin>();

/** How long a success stays on screen. Errors stay until dismissed. */
const SUCCESS_MILLIS = 6000;

export function createAdmin(): Admin {
  const [status, setStatus] = createSignal<Status | null>(null);
  let timer: ReturnType<typeof setTimeout> | undefined;
  return {
    stats: createLiveStats(30, () => call<Stats>('/api/stats')),
    status,
    notify: (text, tone = 'success') => {
      clearTimeout(timer);
      setStatus(text === null ? null : { tone, text });
      if (text !== null && tone === 'success') timer = setTimeout(() => setStatus(null), SUCCESS_MILLIS);
    },
  };
}
