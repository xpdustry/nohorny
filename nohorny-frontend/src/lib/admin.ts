import { createContext, createSignal } from 'solid-js';
import type { Stats } from './api';
import { call } from './session';
import { createLiveStats, type LiveStats } from './stats';

export interface Admin {
  stats: LiveStats;
  /** Outcome of the last action, shown as a dismissable notice at the bottom of the screen. */
  notice: () => Notice | null;
  /** Shows the outcome of an action. */
  notify: (text: string, tone?: Notice['tone']) => void;
  dismiss: () => void;
}

export interface Notice {
  tone: 'success' | 'error';
  text: string;
}

export const AdminContext = createContext<Admin>();

const SUCCESS_MILLIS = 6000;

export function createAdmin(): Admin {
  const [notice, setNotice] = createSignal<Notice | null>(null);
  let timer: ReturnType<typeof setTimeout> | undefined;
  const dismiss = () => {
    clearTimeout(timer);
    setNotice(null);
  };
  return {
    stats: createLiveStats(60, () => call<Stats>('/api/stats')),
    notice,
    notify: (text, tone = 'success') => {
      dismiss();
      setNotice({ tone, text });
      if (tone === 'success') timer = setTimeout(dismiss, SUCCESS_MILLIS);
    },
    dismiss,
  };
}
