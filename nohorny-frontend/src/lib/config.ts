// The runtime configuration of this server, written by /config.js before the app loads, see NoHornyController.

import type { Retention, Session } from './api';

export interface Config {
  sentryDsn: string | null;
  /** The signed in user, null when signed out. */
  session: Session | null;
  retention: Retention;
}

declare global {
  interface Window {
    NOHORNY_CONFIG?: Config;
  }
}

/**
 * Undefined while prerendering, and under `vite preview` which has no /config.js. The prerendered pages cannot hold
 * it, so the components read it after the hydration, see `ssrSource: 'client'`.
 */
export const config = import.meta.env.SSR ? undefined : window.NOHORNY_CONFIG;
