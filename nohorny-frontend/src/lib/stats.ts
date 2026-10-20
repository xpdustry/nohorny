// Statistics from GET /api/stats, polled while the page is visible.

import { type Accessor, createMemo, createSignal, onCleanup } from 'solid-js';
import { api, type Stats } from './api';

export interface LiveStats {
  data: Accessor<Stats | null>;
  /** Settles with the first statistics, or when they fail to come, so a Loading boundary can wait for them. */
  ready: Accessor<true>;
  /** Fetches the statistics now. */
  refresh(): void;
}

/**
 * Polls every `pollSeconds` while the calling component is mounted, from its hydration. A hidden page skips the polls
 * and catches up as soon as it shows again.
 */
export function createLiveStats(pollSeconds: number, load = () => api<Stats>('/api/stats')): LiveStats {
  const [data, setData] = createSignal<Stats | null>(null);
  let lastPoll = 0;
  let settle!: () => void;
  const first = new Promise<true>((resolve) => {
    settle = () => resolve(true);
  });
  // Starts as soon as the page is hydrated, in the memo rather than in onSettled: a Loading boundary waiting for
  // ready() would never settle
  const ready = createMemo(
    () => {
      onCleanup(start());
      return first;
    },
    { ssrSource: 'client' },
  );

  async function poll() {
    lastPoll = Date.now();
    try {
      setData(await load());
    } catch {
      // Keeps the last statistics, the next poll tries again
    } finally {
      settle();
    }
  }

  /** Polls now and then every `pollSeconds`. Returns the stop. */
  function start(): () => void {
    void poll();
    const timer = setInterval(() => !document.hidden && poll(), pollSeconds * 1000);
    const visible = () => !document.hidden && Date.now() - lastPoll >= pollSeconds * 1000 && poll();
    document.addEventListener('visibilitychange', visible);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', visible);
    };
  }

  return {
    data,
    ready,
    refresh: () => void poll(),
  };
}
