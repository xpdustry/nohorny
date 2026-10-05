// Live statistics from GET /api/stats/stream over SSE, with polling when the stream is unavailable.

import { type Accessor, createSignal, onSettled } from 'solid-js';
import { api, type Stats } from './api';

export type Connection = 'connecting' | 'live' | 'polling' | 'offline';

export interface LiveStats {
  data: Accessor<Stats | null>;
  connection: Accessor<Connection>;
  label: Accessor<string>;
  /** Fetches the statistics now, unless the stream already keeps them fresh. */
  refresh(): void;
}

/**
 * Follows the stream while the calling component is mounted.
 * Polls every `pollSeconds` when the stream is unavailable.
 */
export function createLiveStats(pollSeconds: number, load = () => api<Stats>('/api/stats')): LiveStats {
  const [data, setData] = createSignal<Stats | null>(null);
  const [connection, setConnection] = createSignal<Connection>('connecting');
  // A plain flag. A write to connection() shows only after the next flush
  let live = false;
  let timer: ReturnType<typeof setInterval> | undefined;

  async function poll() {
    try {
      const value = await load();
      setData(value);
      if (!live) setConnection('polling');
    } catch {
      if (!live) setConnection('offline');
    }
  }

  function startPolling() {
    if (timer) return;
    void poll();
    timer = setInterval(poll, pollSeconds * 1000);
  }

  function stopPolling() {
    clearInterval(timer);
    timer = undefined;
  }

  onSettled(() => {
    if (!('EventSource' in window)) {
      startPolling();
      return stopPolling;
    }
    const stream = new EventSource('/api/stats/stream');
    let received = false;
    stream.addEventListener('stats', (event) => {
      received = live = true;
      setConnection('live');
      stopPolling();
      try {
        setData(JSON.parse(event.data));
      } catch {
        // Ignores a malformed frame. The next frame replaces it.
      }
    });
    stream.addEventListener('error', () => {
      live = false;
      if (stream.readyState === EventSource.CLOSED || !received) {
        // The stream is unavailable, for example behind some proxies. Poll instead.
        stream.close();
        startPolling();
      } else {
        // The browser reconnects on its own. The last numbers stay until then.
        setConnection('connecting');
      }
    });
    return () => {
      stream.close();
      stopPolling();
    };
  });

  const labels: Record<Connection, string> = {
    live: 'Live',
    connecting: 'Connecting…',
    polling: `Every ${pollSeconds} s`,
    offline: 'Offline',
  };

  return {
    data,
    connection,
    label: () => labels[connection()],
    refresh: () => {
      if (!live) void poll();
    },
  };
}
