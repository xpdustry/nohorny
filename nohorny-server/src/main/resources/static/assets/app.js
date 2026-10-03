/*
 * NoHorny page logic, shared by the three pages.
 *
 * Load it BEFORE Alpine, both deferred, so this file registers its components on `alpine:init`:
 *
 *   <script defer src="/assets/app.js"></script>
 *   <script defer src="/assets/alpine.min.js"></script>
 *
 * The order matters: Alpine starts in a microtask right after its own script runs, so a deferred
 * app.js placed after it would miss `alpine:init` and every component would be undefined.
 *
 * The pages are markup and CSS only. Each mounts one component with `x-data="landing"`,
 * `x-data="request"` or `x-data="admin"` (usually on <body>) and binds to the fields below.
 * Everything not listed here is internal and may change.
 *
 * PAGE CONTRACT
 * =============
 *
 * Shared by all three components
 * ------------------------------
 * Formatting helpers (pure functions, safe in any binding):
 *   number(value)             Localized integer, '–' for null/undefined.
 *   percent(value)            0..1 score as '97%', '–' for null/undefined.
 *   share(part, total)        part/total as '4.2%' (one decimal under 10%), '' when unknown.
 *   duration(millis)          '143 ms' or '1.25 s', '–' for null/undefined.
 *   formatTime(iso)           Localized date and time, '–' for null.
 *   relativeTime(iso)         '3 minutes ago' style, '' for null.
 *   shortId(id)               First block of a UUID, for compact display.
 *   bucket(request)           'safe' | 'warn' | 'nsfw' | 'failed' for a request object.
 *   verdict(request)          'SAFE' | 'WARN' | 'NSFW' | 'FAILED' for a request object.
 *   stepBucket(step)          Bucket of one classifier step ('failed' when it has no rating).
 *   ratingLabel(bucket)       'Safe' | 'Warn' | 'NSFW' | 'Failed'.
 *   ratingColor(bucket)       CSS color: `var(--nh-<bucket>, <default hex>)`, override the variable in CSS.
 *   nsfwScore(value)          'NSFW score 97%', or 'No score' when null.
 *   isServer(client)          true when client.type is 'mindustry-server'.
 *   clientLabel(client)       Human label of a request client, Mindustry color tags stripped.
 *   stripColors(text)         Removes Mindustry [name] / [#hex] / [] color tags.
 *
 * Alpine.data('landing')  (index.html)
 * ------------------------------------
 *   stats                     Last GET /api/stats payload (see API.md), null until the first one arrives.
 *   display                   Animated all-time total for the big counter (counts up, honors reduced motion).
 *   spoken                    Throttled text for an aria-live region (at most every 30 s).
 *   connection                'connecting' | 'live' | 'polling' | 'offline'.
 *   connectionLabel           Human text for `connection`.
 *   flagged                   WARN + NSFW count over the last 24 hours, null until known.
 *   flaggedShare              '4.2% of the last 24 hours', '' until known.
 *   installEndpoint           This instance's API endpoint, `location.origin + '/api'`.
 *   installCommand            'config nohorny-api-endpoint <installEndpoint>'.
 *   webhookCommand            'config nohorny-discord-webhook https://discord.com/api/webhooks/...'.
 *   copy(text)                Copies text to the clipboard (with a fallback for insecure origins).
 *   copied                    The text copied last, cleared after 2 s, null otherwise.
 *   copyFailed                The text whose copy failed, null otherwise.
 *   isCopied(text)            Shortcut for `copied === text`.
 *
 * Alpine.data('request')  (request.html, id from /requests/<id>, or ?id=<id> as a fallback)
 * ------------------------------------------------------------------------------------------
 *   id                        Request identifier.
 *   state                     'loading' | 'ready' | 'missing' | 'error' | 'deleted'.
 *   request                   The request object (see API.md), null until loaded.
 *   error                     Message for state 'error'.
 *   load()                    (Re)loads the request, use it for a retry button.
 *   session                   GET /api/session payload ({ username, admin, bootstrap }) or null when signed out.
 *   admin                     true when the visitor is a signed-in administrator.
 *   bucketKey                 bucket(request), 'failed' while loading.
 *   imageState                'none' | 'stored' | 'expired' | 'purged'.
 *   hasImage                  true when an image is stored and did not fail to load.
 *   imageBroken               Set it to true from the <img> @error handler.
 *   sensitive                 true unless the verdict is SAFE (flagged images start blurred).
 *   revealed                  Whether the visitor chose to see a sensitive image.
 *   blurred                   sensitive && !revealed.
 *   reveal() / hide() / toggleReveal()
 *   flagLabel                 'Flagged as NSFW' or 'Not classified', for the blur overlay.
 *   placeholder               Text to show instead of the image when there is none.
 *   busy                      true while purge() or remove() runs, disable the buttons.
 *   message                   { tone: 'success' | 'error', text } after an action, or null.
 *   purge()                   Asks for confirmation, then deletes the image (anyone, rate limited).
 *   remove()                  Admin only: asks for confirmation, then deletes the whole request.
 *
 * Alpine.data('admin')  (admin.html)
 * ----------------------------------
 * Session:
 *   phase                     'loading' | 'login' | 'forbidden' | 'error' | 'ready'.
 *   fatal                     Message for phase 'error'.
 *   checkSession()            Re-probes the session, use it for a retry button.
 *   session                   { username, admin, bootstrap } once signed in, null otherwise.
 *   credentials               { username, password }, bind the login form with x-model.
 *   login() / logout()
 *   loginBusy, loginError, loginNotice   Login form feedback ('Your session expired…', 'You are signed out.').
 * Views:
 *   view                      'requests' | 'users' (also mirrored in the URL hash #users).
 *   setView(name)
 *   status                    Last action outcome, for a dismissable notice and an aria-live region.
 * Statistics:
 *   stats, connection, connectionLabel   Same as the landing component, live over SSE with polling fallback.
 *   ratingTiles               [{ key, label, value, share }] for safe/warn/nsfw/failed over the last 24 hours.
 * Requests (view 'requests'):
 *   filters                   [{ value, label }], value '' means all.
 *   filter / setFilter(value)
 *   items                     Loaded request objects, newest first.
 *   loading, exhausted, listError
 *   loadMore()                Loads the next page; also called by the infinite scroll sentinel.
 *   refresh() / retry()
 *   versions                  [{ name, count, value (0..1), share }] plugin versions among the loaded items.
 *   revealAll                 Toggle to unblur every image.
 *   hasImage(item), sensitive(item), blurred(item), toggle(item), placeholder(item)
 *   broken                    { [id]: true } set it from the <img> @error handler.
 *   busy                      { [id]: true } while a row action runs.
 *   purge(item) / remove(item)
 * Users (view 'users'):
 *   users                     User objects ({ username, admin, createdAt, bootstrap }), sorted by username.
 *   usersLoaded, usersLoading, usersError, loadUsers()
 *   adminCount                Number of administrators.
 *   isSelf(account)
 *   demoteBlocked(account)    Reason the admin role cannot be removed (bootstrap, own account, last admin), else null.
 *   deleteBlocked(account)    Reason the account cannot be deleted (bootstrap, own account), else null.
 *   newUser                   { username, password, admin }, bind the create form with x-model.
 *   newUserProblems           { username, password } client-side rule violations (null when fine), for hints.
 *   createUser(), createBusy, createError (API message on 400/409)
 *   toggleAdmin(account)
 *   removeUser(account)       Asks for confirmation first.
 *   userBusy                  { [username]: true } while a row action runs.
 *   openPassword(account) / closePassword() / savePassword()
 *   passwordTarget            The account being edited, null when the form is closed (use it for x-show).
 *   passwordValue, passwordBusy, passwordError
 *
 * Confirmations (request and admin)
 * ---------------------------------
 *   dialog                    { title, body, action } of the pending confirmation.
 *   closed()                  Call from the dialog's @close.
 *   With a <dialog x-ref="dialog"> containing a <form method="dialog"> whose confirm button has value="confirm",
 *   confirmations use it; without that ref they fall back to window.confirm().
 *
 * Optional refs (admin)
 * ---------------------
 *   x-ref="sentinel"          Element near the end of the list, loads the next page when it scrolls into view.
 *   x-ref="passwordDialog"    <dialog> opened by openPassword(); without it, render an inline form on passwordTarget.
 *   x-ref="username", "password", "newUsername", "passwordInput"   Focused when relevant.
 */

'use strict';

(() => {
  // Shared helpers

  const numberFormat = new Intl.NumberFormat();
  const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
  const relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

  const RATING_COLORS = { safe: '#3fb950', warn: '#d29922', nsfw: '#f85149', failed: '#8b949e' };
  const RATING_LABELS = { safe: 'Safe', warn: 'Warn', nsfw: 'NSFW', failed: 'Failed' };

  /** The `X-XSRF-TOKEN` header echoing the `XSRF-TOKEN` cookie, empty when the cookie is missing. */
  function csrfHeaders() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
  }

  class ApiError extends Error {
    constructor(status, message, serverMessage = null, response = null) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.serverMessage = serverMessage;
      this.retryAfter = response?.headers.get('Retry-After') ?? null;
    }

    /** The API's own message when it sent one, the fallback otherwise. */
    describe(fallback) {
      if (this.status === 0) return this.message;
      return this.serverMessage || fallback || this.message;
    }
  }

  /**
   * Fetches an API path. Adds `Accept: application/json` and, for unsafe methods, the CSRF header.
   * `options.json` is serialized as the JSON body. Resolves to the parsed JSON body, the text body,
   * or null for empty responses. Rejects with an ApiError for non 2xx answers (status 0 when unreachable).
   */
  async function api(path, options = {}) {
    const { json, headers, ...rest } = options;
    const method = (rest.method ?? 'GET').toUpperCase();
    const init = {
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

    let response;
    try {
      response = await fetch(path, init);
    } catch {
      throw new ApiError(0, 'Could not reach the server.');
    }
    if (!response.ok) {
      let serverMessage = null;
      try {
        const body = await response.json();
        if (body && typeof body.message === 'string' && body.message) serverMessage = body.message;
      } catch {
        // Not JSON.
      }
      throw new ApiError(response.status, serverMessage ?? `The server answered with HTTP ${response.status}.`, serverMessage, response);
    }
    if (response.status === 204 || response.headers.get('Content-Length') === '0') return null;
    const text = await response.text();
    if (!text) return null;
    return (response.headers.get('Content-Type') ?? '').includes('json') ? JSON.parse(text) : text;
  }

  function number(value) {
    return value === null || value === undefined ? '–' : numberFormat.format(value);
  }

  function percent(value) {
    return value === null || value === undefined ? '–' : `${Math.round(value * 100)}%`;
  }

  function share(part, total) {
    if (!total || part === null || part === undefined) return '';
    const value = (part / total) * 100;
    return `${value > 0 && value < 10 ? value.toFixed(1) : Math.round(value)}%`;
  }

  function duration(millis) {
    if (millis === null || millis === undefined) return '–';
    return millis < 1000 ? `${millis} ms` : `${(millis / 1000).toFixed(2)} s`;
  }

  function formatTime(iso) {
    return iso ? dateFormat.format(new Date(iso)) : '–';
  }

  function relativeTime(iso) {
    if (!iso) return '';
    const seconds = (new Date(iso).getTime() - Date.now()) / 1000;
    const units = [['year', 31536000], ['month', 2592000], ['day', 86400], ['hour', 3600], ['minute', 60]];
    for (const [unit, size] of units) {
      if (Math.abs(seconds) >= size) return relativeFormat.format(Math.round(seconds / size), unit);
    }
    return relativeFormat.format(Math.round(seconds), 'second');
  }

  function shortId(id) {
    return id ? String(id).split('-')[0] : '';
  }

  function bucket(request) {
    if (!request || !request.successful || !request.rating) return 'failed';
    return request.rating.toLowerCase();
  }

  function verdict(request) {
    return bucket(request) === 'failed' ? 'FAILED' : request.rating;
  }

  function stepBucket(step) {
    return step?.rating ? step.rating.toLowerCase() : 'failed';
  }

  function ratingLabel(key) {
    return RATING_LABELS[key] ?? 'Failed';
  }

  function ratingColor(key) {
    const known = key in RATING_COLORS ? key : 'failed';
    return `var(--nh-${known}, ${RATING_COLORS[known]})`;
  }

  function nsfwScore(value) {
    return value === null || value === undefined ? 'No score' : `NSFW score ${percent(value)}`;
  }

  function stripColors(text) {
    return String(text ?? '').replace(/\[(#[0-9a-fA-F]{3,8}|[a-zA-Z]+)?\]/g, '').trim();
  }

  function isServer(client) {
    return client?.type === 'mindustry-server';
  }

  function clientLabel(client) {
    if (!isServer(client)) return { localhost: 'Localhost' }[client?.type] ?? 'Unknown client';
    return stripColors(client.name) || 'Mindustry server';
  }

  const helpers = {
    number, percent, share, duration, formatTime, relativeTime, shortId,
    bucket, verdict, stepBucket, ratingLabel, ratingColor, nsfwScore,
    isServer, clientLabel, stripColors,
  };

  /** Merges objects keeping getters as getters, which a spread would evaluate. */
  function compose(...parts) {
    const target = {};
    for (const part of parts) Object.defineProperties(target, Object.getOwnPropertyDescriptors(part));
    return target;
  }

  // Live statistics, over SSE with a polling fallback

  function statsFeed(pollSeconds) {
    return {
      stats: null,
      connection: 'connecting',
      _source: null,
      _pollTimer: null,

      connectStats() {
        this.stopStats();
        this.connection = 'connecting';
        if (!('EventSource' in window)) {
          this._startPolling();
          return;
        }
        const source = new EventSource('/api/stats/stream');
        this._source = source;
        let received = false;
        source.addEventListener('stats', (event) => {
          received = true;
          this.connection = 'live';
          this._stopPolling();
          try {
            this.applyStats(JSON.parse(event.data));
          } catch {
            // Ignore a malformed frame, the next one replaces it.
          }
        });
        source.addEventListener('error', () => {
          if (source.readyState === EventSource.CLOSED || !received) {
            // The stream is unavailable (proxy, server error): poll instead.
            source.close();
            if (this._source === source) this._source = null;
            this._startPolling();
          } else {
            // The browser reconnects by itself, keep the last numbers meanwhile.
            this.connection = 'connecting';
          }
        });
      },

      stopStats() {
        this._source?.close();
        this._source = null;
        this._stopPolling();
      },

      _startPolling() {
        if (this._pollTimer) return;
        this.pollStats();
        this._pollTimer = setInterval(() => this.pollStats(), pollSeconds * 1000);
      },

      _stopPolling() {
        clearInterval(this._pollTimer);
        this._pollTimer = null;
      },

      async pollStats() {
        try {
          this.applyStats(await api('/api/stats'));
          if (this.connection !== 'live') this.connection = 'polling';
        } catch {
          if (this.connection !== 'live') this.connection = 'offline';
        }
      },

      applyStats(stats) {
        this.stats = stats;
      },

      get connectionLabel() {
        return {
          live: 'Live',
          connecting: 'Connecting…',
          polling: `Refreshing every ${pollSeconds} seconds`,
          offline: 'Statistics unavailable',
        }[this.connection];
      },
    };
  }

  // Confirmation dialog, a <dialog x-ref="dialog"> or window.confirm()

  function confirmations() {
    return {
      dialog: { title: '', body: '', action: '' },
      _settle: null,

      ask(dialog) {
        this.dialog = dialog;
        const element = this.$refs.dialog;
        if (!element || typeof element.showModal !== 'function') {
          return Promise.resolve(window.confirm(`${dialog.title}\n\n${dialog.body}`));
        }
        this._settle?.(false);
        element.returnValue = '';
        element.showModal();
        return new Promise((resolve) => (this._settle = resolve));
      },

      closed() {
        this._settle?.(this.$refs.dialog?.returnValue === 'confirm');
        this._settle = null;
      },
    };
  }

  async function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return;
    }
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand('copy');
    area.remove();
    if (!ok) throw new Error('copy failed');
  }

  async function probeSession() {
    try {
      return await api('/api/session');
    } catch (error) {
      if (error.status === 401) return null;
      throw error;
    }
  }

  // Components

  function landing() {
    return compose(helpers, statsFeed(15), {
      display: null,
      spoken: '',
      installEndpoint: `${location.origin}/api`,
      copied: null,
      copyFailed: null,
      _frame: 0,
      _lastSpokenAt: 0,
      _copyTimer: null,

      init() {
        this.connectStats();
      },

      destroy() {
        this.stopStats();
        cancelAnimationFrame(this._frame);
        clearTimeout(this._copyTimer);
      },

      get installCommand() {
        return `config nohorny-api-endpoint ${this.installEndpoint}`;
      },

      get webhookCommand() {
        return 'config nohorny-discord-webhook https://discord.com/api/webhooks/...';
      },

      applyStats(stats) {
        this.stats = stats;
        this._animateTo(stats?.total ?? 0);
        const now = Date.now();
        // Announce at most every 30 seconds so screen readers are not flooded.
        if (now - this._lastSpokenAt > 30000) {
          this._lastSpokenAt = now;
          this.spoken = `${number(stats?.total)} images classified`;
        }
      },

      _animateTo(target) {
        cancelAnimationFrame(this._frame);
        const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const from = this.display ?? 0;
        if (reduced || from === target) {
          this.display = target;
          return;
        }
        const length = this.display === null ? 1400 : 700;
        const start = performance.now();
        const step = (now) => {
          const progress = Math.min(1, (now - start) / length);
          const eased = 1 - Math.pow(1 - progress, 3);
          this.display = Math.round(from + (target - from) * eased);
          if (progress < 1) this._frame = requestAnimationFrame(step);
        };
        this.display = from;
        this._frame = requestAnimationFrame(step);
      },

      get flagged() {
        const ratings = this.stats?.last24Hours?.ratings;
        return ratings ? (ratings.warn ?? 0) + (ratings.nsfw ?? 0) : null;
      },

      get flaggedShare() {
        const value = share(this.flagged, this.stats?.last24Hours?.total);
        return value ? `${value} of the last 24 hours` : '';
      },

      async copy(text) {
        this.copyFailed = null;
        clearTimeout(this._copyTimer);
        try {
          await copyText(text);
          this.copied = text;
          this._copyTimer = setTimeout(() => (this.copied = null), 2000);
        } catch {
          this.copied = null;
          this.copyFailed = text;
        }
      },

      isCopied(text) {
        return this.copied === text;
      },
    });
  }

  function request() {
    return compose(helpers, confirmations(), {
      id: null,
      state: 'loading',
      request: null,
      session: null,
      revealed: false,
      imageBroken: false,
      busy: false,
      message: null,
      error: null,

      init() {
        const match = location.pathname.match(/^\/requests\/([^/]+)\/?$/);
        this.id = match ? decodeURIComponent(match[1]) : new URLSearchParams(location.search).get('id');
        if (!this.id) {
          this.state = 'missing';
          return;
        }
        this.load();
      },

      async load() {
        this.state = 'loading';
        this.error = null;
        const session = probeSession().then((value) => (this.session = value), () => (this.session = null));
        try {
          this._show(await api(`/api/requests/${encodeURIComponent(this.id)}`));
          await session;
          this.state = 'ready';
        } catch (error) {
          if (error.status === 404) {
            this.state = 'missing';
            return;
          }
          this.error = error.status === 429
            ? 'Too many requests from your address, wait a moment and retry.'
            : error.status === 0
              ? 'Could not reach the server. Check your connection and retry.'
              : error.describe?.(`The server answered with an error (HTTP ${error.status}).`) ?? 'Unexpected error.';
          this.state = 'error';
        }
      },

      _show(value) {
        this.request = value;
        this.imageBroken = false;
        if (value?.image?.state !== 'stored') this.revealed = false;
      },

      get admin() {
        return this.session?.admin === true;
      },

      get bucketKey() {
        return this.request ? bucket(this.request) : 'failed';
      },

      get imageState() {
        return this.request?.image?.state ?? 'none';
      },

      get hasImage() {
        return this.imageState === 'stored' && !this.imageBroken;
      },

      get sensitive() {
        return this.bucketKey !== 'safe';
      },

      get blurred() {
        return this.sensitive && !this.revealed;
      },

      reveal() {
        this.revealed = true;
      },

      hide() {
        this.revealed = false;
      },

      toggleReveal() {
        this.revealed = !this.revealed;
      },

      get flagLabel() {
        return this.bucketKey === 'failed' ? 'Not classified' : `Flagged as ${verdict(this.request)}`;
      },

      get placeholder() {
        if (this.imageBroken) return 'Image unavailable';
        return {
          none: 'No image kept for safe results',
          expired: 'Image expired after the retention period',
          purged: 'Image removed',
        }[this.imageState] ?? 'No image available';
      },

      async purge() {
        const confirmed = await this.ask({
          title: 'Report illegal content?',
          body: 'This permanently deletes the image from NoHorny, for everyone including moderators. The verdict stays on record without the image. This cannot be undone.',
          action: 'Delete image',
        });
        if (!confirmed) return;
        this.busy = true;
        this.message = null;
        try {
          this._show(await api(`/api/requests/${encodeURIComponent(this.id)}/purge`, { method: 'POST' }));
          this.message = { tone: 'success', text: 'The image was deleted. Thank you for reporting it.' };
        } catch (error) {
          if (error.status === 404) this.state = 'missing';
          else if (error.status === 429) this.message = { tone: 'error', text: 'Too many reports from your address, try later.' };
          else if (error.status === 0) this.message = { tone: 'error', text: 'Could not reach the server, the image was not deleted.' };
          else this.message = { tone: 'error', text: error.describe(`The report failed (HTTP ${error.status}). Reload the page and try again.`) };
        } finally {
          this.busy = false;
        }
      },

      async remove() {
        const confirmed = await this.ask({
          title: 'Delete this request?',
          body: 'The request, its verdict and its image are removed for good and this link stops working. Statistics are not affected.',
          action: 'Delete request',
        });
        if (!confirmed) return;
        this.busy = true;
        this.message = null;
        try {
          await api(`/api/requests/${encodeURIComponent(this.id)}`, { method: 'DELETE' });
          this.state = 'deleted';
        } catch (error) {
          if (error.status === 404) this.state = 'deleted';
          else if (error.status === 401 || error.status === 403) this.message = { tone: 'error', text: 'Your admin session expired or was rejected. Sign in again from the admin panel.' };
          else if (error.status === 0) this.message = { tone: 'error', text: 'Could not reach the server, the request was not deleted.' };
          else this.message = { tone: 'error', text: error.describe(`Deletion failed (HTTP ${error.status}).`) };
        } finally {
          this.busy = false;
        }
      },
    });
  }

  const FILTERS = [
    { value: '', label: 'All' },
    { value: 'safe', label: 'Safe' },
    { value: 'warn', label: 'Warn' },
    { value: 'nsfw', label: 'NSFW' },
    { value: 'failed', label: 'Failed' },
  ];

  const PAGE_SIZE = 20;
  const USERNAME_RULE = /^[a-z0-9_.-]{3,32}$/;

  class SessionExpired extends Error {}

  function admin() {
    return compose(helpers, statsFeed(30), confirmations(), {
      filters: FILTERS,
      phase: 'loading',
      fatal: null,
      session: null,
      view: location.hash === '#users' ? 'users' : 'requests',
      status: '',

      credentials: { username: '', password: '' },
      loginBusy: false,
      loginError: null,
      loginNotice: null,

      filter: '',
      items: [],
      nextCursor: null,
      exhausted: false,
      loading: false,
      listError: null,
      revealAll: false,
      revealed: {},
      broken: {},
      busy: {},
      _generation: 0,
      _observer: null,

      users: [],
      usersLoaded: false,
      usersLoading: false,
      usersError: null,
      userBusy: {},
      newUser: { username: '', password: '', admin: false },
      createBusy: false,
      createError: null,
      passwordTarget: null,
      passwordValue: '',
      passwordBusy: false,
      passwordError: null,

      init() {
        if (this.$refs.sentinel && 'IntersectionObserver' in window) {
          this._observer = new IntersectionObserver(
            (entries) => {
              if (entries.some((entry) => entry.isIntersecting)) this.loadMore();
            },
            { rootMargin: '0px 0px 600px 0px' },
          );
          this._observer.observe(this.$refs.sentinel);
        }
        this.checkSession();
      },

      destroy() {
        this._observer?.disconnect();
        this.stopStats();
      },

      // Session

      async checkSession() {
        this.phase = 'loading';
        this.fatal = null;
        try {
          const session = await probeSession();
          if (!session) {
            this._showLogin();
            return;
          }
          this.session = session;
          if (session.admin !== true) {
            this.phase = 'forbidden';
            return;
          }
          this._start();
        } catch (error) {
          this.fatal = error.describe?.('The session check failed.') ?? 'The session check failed.';
          this.phase = 'error';
        }
      },

      _showLogin(notice = null) {
        this._teardown();
        this.session = null;
        this.loginNotice = notice;
        this.phase = 'login';
        this.$nextTick(() => this.$refs.username?.focus());
      },

      _postLogin() {
        return api('/login', { method: 'POST', body: new URLSearchParams(this.credentials) });
      },

      async login() {
        if (this.loginBusy) return;
        this.loginBusy = true;
        this.loginError = null;
        try {
          // GET requests hand out the XSRF-TOKEN cookie.
          if (!csrfHeaders()['X-XSRF-TOKEN']) await probeSession();
          try {
            await this._postLogin();
          } catch (error) {
            if (error.status !== 403) throw error;
            // Stale CSRF token: fetch a fresh one and retry once.
            await probeSession();
            await this._postLogin();
          }
          this.credentials = { username: '', password: '' };
          this.loginNotice = null;
          await this.checkSession();
        } catch (error) {
          if (error.status === 401) {
            this.loginError = 'Invalid credentials';
            this.credentials.password = '';
            this.$nextTick(() => this.$refs.password?.focus());
          } else {
            this.loginError = error.describe?.(`Sign in failed (HTTP ${error.status}).`) ?? 'Sign in failed.';
          }
        } finally {
          this.loginBusy = false;
        }
      },

      async logout() {
        try {
          await api('/logout', { method: 'POST' });
        } catch {
          // Signing out locally is still the right outcome.
        }
        this._showLogin('You are signed out.');
        // Prime a fresh CSRF cookie for the next sign in.
        probeSession().catch(() => {});
      },

      _expire(notice = 'Your session expired, sign in again.') {
        if (this.phase === 'ready') this._showLogin(notice);
        throw new SessionExpired();
      },

      /** api() for authenticated calls: a 401 ends the session and throws SessionExpired. */
      async _call(path, options) {
        try {
          return await api(path, options);
        } catch (error) {
          if (error.status === 401) this._expire();
          throw error;
        }
      },

      _start() {
        this.phase = 'ready';
        this.connectStats();
        this.refresh();
        if (this.view === 'users') this.loadUsers();
      },

      _teardown() {
        this.stopStats();
        this._generation++;
        this.items = [];
        this.nextCursor = null;
        this.exhausted = false;
        this.loading = false;
        this.listError = null;
        this.revealed = {};
        this.broken = {};
        this.busy = {};
        this.stats = null;
        this.users = [];
        this.usersLoaded = false;
        this.usersError = null;
        this.userBusy = {};
        this.createError = null;
        this.passwordTarget = null;
        this.status = '';
      },

      setView(name) {
        if (name !== 'requests' && name !== 'users') return;
        if (this.view === name) return;
        this.view = name;
        this.status = '';
        history.replaceState(null, '', name === 'users' ? '#users' : `${location.pathname}${location.search}`);
        if (this.phase !== 'ready') return;
        if (name === 'users') this.loadUsers();
        else this.$nextTick(() => this.loadMore());
      },

      // Statistics

      async pollStats() {
        try {
          this.applyStats(await this._call('/api/stats'));
          if (this.connection !== 'live') this.connection = 'polling';
        } catch (error) {
          if (!(error instanceof SessionExpired) && this.connection !== 'live') this.connection = 'offline';
        }
      },

      get ratingTiles() {
        const day = this.stats?.last24Hours;
        return ['safe', 'warn', 'nsfw', 'failed'].map((key) => ({
          key,
          label: ratingLabel(key),
          value: day?.ratings?.[key],
          share: share(day?.ratings?.[key], day?.total),
        }));
      },

      // Request list

      setFilter(value) {
        if (this.filter === value) return;
        this.filter = value;
        this.refresh();
      },

      refresh() {
        this._generation++;
        this.items = [];
        this.nextCursor = null;
        this.exhausted = false;
        this.loading = false;
        this.listError = null;
        this.revealed = {};
        this.broken = {};
        this.loadMore();
        if (this.connection !== 'live') this.pollStats();
      },

      retry() {
        this.listError = null;
        this.loadMore();
      },

      async loadMore() {
        if (this.phase !== 'ready' || this.view !== 'requests' || this.loading || this.exhausted || this.listError) return;
        const generation = this._generation;
        this.loading = true;
        const params = new URLSearchParams({ limit: String(PAGE_SIZE) });
        if (this.filter) params.set('rating', this.filter);
        if (this.nextCursor) params.set('before', this.nextCursor);
        try {
          const page = await this._call(`/api/requests?${params}`);
          if (generation !== this._generation) return;
          const known = new Set(this.items.map((item) => item.id));
          this.items.push(...page.items.filter((item) => !known.has(item.id)));
          this.nextCursor = page.nextCursor ?? null;
          this.exhausted = this.nextCursor === null;
        } catch (error) {
          if (generation !== this._generation || error instanceof SessionExpired) return;
          this.listError = error.describe?.(`Could not load requests (HTTP ${error.status}).`) ?? 'Could not load requests.';
        } finally {
          if (generation === this._generation) {
            this.loading = false;
            // Re-observing makes the observer report again if the sentinel is still on screen.
            this.$nextTick(() => {
              const sentinel = this.$refs.sentinel;
              if (!this._observer || !sentinel) return;
              this._observer.unobserve(sentinel);
              this._observer.observe(sentinel);
            });
          }
        }
      },

      get versions() {
        const counts = new Map();
        for (const item of this.items) {
          const version = item.version || 'unknown';
          counts.set(version, (counts.get(version) ?? 0) + 1);
        }
        const total = this.items.length;
        return [...counts.entries()]
          .map(([name, count]) => ({ name, count, value: count / total, share: share(count, total) }))
          .sort((a, b) => b.count - a.count || b.name.localeCompare(a.name, undefined, { numeric: true }));
      },

      sensitive(item) {
        return bucket(item) !== 'safe';
      },

      hasImage(item) {
        return item.image?.state === 'stored' && !this.broken[item.id];
      },

      blurred(item) {
        return this.sensitive(item) && !this.revealAll && !this.revealed[item.id];
      },

      toggle(item) {
        this.revealed[item.id] = !this.revealed[item.id];
      },

      placeholder(item) {
        if (this.broken[item.id]) return 'Unavailable';
        return { none: 'Not kept', expired: 'Expired', purged: 'Purged' }[item.image?.state] ?? 'No image';
      },

      _replace(updated) {
        const index = this.items.findIndex((item) => item.id === updated.id);
        if (index >= 0) this.items[index] = updated;
      },

      _drop(id) {
        this.items = this.items.filter((item) => item.id !== id);
      },

      async purge(item) {
        const confirmed = await this.ask({
          title: `Purge the image of ${shortId(item.id)}?`,
          body: 'The image bytes are deleted immediately. The request stays as a tombstone with its verdict. This cannot be undone.',
          action: 'Purge image',
        });
        if (!confirmed) return;
        this.busy[item.id] = true;
        try {
          this._replace(await this._call(`/api/requests/${encodeURIComponent(item.id)}/purge`, { method: 'POST' }));
          this.status = `Image of ${shortId(item.id)} purged.`;
        } catch (error) {
          if (error instanceof SessionExpired) return;
          if (error.status === 404) {
            this._drop(item.id);
            this.status = `Request ${shortId(item.id)} no longer exists.`;
          } else if (error.status === 429) {
            this.status = 'Too many purges from your address, try later.';
          } else {
            this.status = error.status === 0 ? 'Could not reach the server, nothing was purged.' : error.describe(`Purge failed (HTTP ${error.status}).`);
          }
        } finally {
          delete this.busy[item.id];
        }
      },

      async remove(item) {
        const confirmed = await this.ask({
          title: `Delete request ${shortId(item.id)}?`,
          body: 'The request, its verdict and its image are removed for good and its link stops working. Statistics are not affected.',
          action: 'Delete request',
        });
        if (!confirmed) return;
        this.busy[item.id] = true;
        try {
          await this._call(`/api/requests/${encodeURIComponent(item.id)}`, { method: 'DELETE' });
          this._drop(item.id);
          this.status = `Request ${shortId(item.id)} deleted.`;
        } catch (error) {
          if (error instanceof SessionExpired) return;
          if (error.status === 404) {
            this._drop(item.id);
            this.status = `Request ${shortId(item.id)} deleted.`;
          } else {
            this.status = error.status === 0 ? 'Could not reach the server, nothing was deleted.' : error.describe(`Deletion failed (HTTP ${error.status}).`);
          }
        } finally {
          delete this.busy[item.id];
        }
      },

      // Users

      async loadUsers() {
        if (this.usersLoading) return;
        this.usersLoading = true;
        this.usersError = null;
        try {
          this.users = this._sortUsers(await this._call('/api/users'));
          this.usersLoaded = true;
        } catch (error) {
          if (error instanceof SessionExpired) return;
          this.usersError = error.describe?.(`Could not load users (HTTP ${error.status}).`) ?? 'Could not load users.';
        } finally {
          this.usersLoading = false;
        }
      },

      isSelf(account) {
        return !!account && account.username === this.session?.username;
      },

      get adminCount() {
        return this.users.filter((account) => account.admin).length;
      },

      // Mirrors the refusals of the API, returns why the action is unavailable or null.
      demoteBlocked(account) {
        if (!account.admin) return null;
        if (account.bootstrap) return 'The bootstrap administrator is configured on the server and stays an administrator.';
        if (this.isSelf(account)) return 'You cannot remove your own administrator role.';
        if (this.adminCount <= 1) return 'At least one administrator is required.';
        return null;
      },

      deleteBlocked(account) {
        if (account.bootstrap) return 'The bootstrap administrator is configured on the server and cannot be deleted.';
        if (this.isSelf(account)) return 'You cannot delete your own account.';
        return null;
      },

      get newUserProblems() {
        const { username, password } = this.newUser;
        return {
          username: !username || USERNAME_RULE.test(username)
            ? null
            : 'Use 3 to 32 lowercase letters, digits, _ . or -',
          password: !password || password.length >= 8 ? null : 'Use at least 8 characters',
        };
      },

      _sortUsers(list) {
        return [...list].sort((a, b) => (a.username < b.username ? -1 : a.username > b.username ? 1 : 0));
      },

      _putUser(account) {
        this.users = this._sortUsers([...this.users.filter((other) => other.username !== account.username), account]);
      },

      _dropUser(username) {
        this.users = this.users.filter((account) => account.username !== username);
      },

      async createUser() {
        if (this.createBusy) return;
        this.createBusy = true;
        this.createError = null;
        try {
          const account = await this._call('/api/users', { method: 'POST', json: this.newUser });
          this._putUser(account);
          this.newUser = { username: '', password: '', admin: false };
          this.status = `User ${account.username} created${account.admin ? ' as an administrator' : ''}.`;
          this.$nextTick(() => this.$refs.newUsername?.focus());
        } catch (error) {
          if (error instanceof SessionExpired) return;
          if (error.status === 0) this.createError = 'Could not reach the server, the user was not created.';
          else if (error.status === 409) this.createError = error.describe(`The username ${this.newUser.username} is already taken.`);
          else this.createError = error.describe(`Could not create the user (HTTP ${error.status}).`);
        } finally {
          this.createBusy = false;
        }
      },

      async toggleAdmin(account) {
        const admin = !account.admin;
        const blocked = admin ? null : this.demoteBlocked(account);
        if (blocked) {
          this.status = blocked;
          return;
        }
        this.userBusy[account.username] = true;
        try {
          await this._call(`/api/users/${encodeURIComponent(account.username)}/admin`, { method: 'PUT', json: { admin } });
          this._putUser({ ...account, admin });
          this.status = admin ? `${account.username} is now an administrator.` : `${account.username} is no longer an administrator.`;
        } catch (error) {
          if (error instanceof SessionExpired) return;
          if (error.status === 404) {
            this._dropUser(account.username);
            this.status = `User ${account.username} no longer exists.`;
          } else {
            this.status = error.status === 0 ? 'Could not reach the server, the role was not changed.' : error.describe(`Could not change the role (HTTP ${error.status}).`);
          }
        } finally {
          delete this.userBusy[account.username];
        }
      },

      openPassword(account) {
        this.passwordTarget = account;
        this.passwordValue = '';
        this.passwordError = null;
        const dialog = this.$refs.passwordDialog;
        if (dialog && typeof dialog.showModal === 'function' && !dialog.open) dialog.showModal();
        this.$nextTick(() => this.$refs.passwordInput?.focus());
      },

      closePassword() {
        const dialog = this.$refs.passwordDialog;
        if (dialog?.open) dialog.close();
        this.passwordTarget = null;
        this.passwordValue = '';
        this.passwordError = null;
      },

      async savePassword() {
        const account = this.passwordTarget;
        if (!account || this.passwordBusy) return;
        this.passwordBusy = true;
        this.passwordError = null;
        try {
          await this._call(`/api/users/${encodeURIComponent(account.username)}/password`, { method: 'PUT', json: { password: this.passwordValue } });
          this.closePassword();
          if (this.isSelf(account)) {
            // The server ends the sessions of an account whose password changed, including this one.
            this._showLogin('Your password was changed, sign in with the new one.');
            return;
          }
          this.status = `Password of ${account.username} changed.`;
        } catch (error) {
          if (error instanceof SessionExpired) return;
          if (error.status === 404) {
            this.closePassword();
            this._dropUser(account.username);
            this.status = `User ${account.username} no longer exists.`;
          } else {
            this.passwordError = error.status === 0 ? 'Could not reach the server, the password was not changed.' : error.describe(`Could not change the password (HTTP ${error.status}).`);
          }
        } finally {
          this.passwordBusy = false;
        }
      },

      async removeUser(account) {
        const blocked = this.deleteBlocked(account);
        if (blocked) {
          this.status = blocked;
          return;
        }
        const confirmed = await this.ask({
          title: `Delete user ${account.username}?`,
          body: 'The account is removed and its sessions end immediately. The requests it submitted are kept.',
          action: 'Delete user',
        });
        if (!confirmed) return;
        this.userBusy[account.username] = true;
        try {
          await this._call(`/api/users/${encodeURIComponent(account.username)}`, { method: 'DELETE' });
          this._dropUser(account.username);
          this.status = `User ${account.username} deleted.`;
        } catch (error) {
          if (error instanceof SessionExpired) return;
          if (error.status === 404) {
            this._dropUser(account.username);
            this.status = `User ${account.username} deleted.`;
          } else {
            this.status = error.status === 0 ? 'Could not reach the server, the user was not deleted.' : error.describe(`Could not delete the user (HTTP ${error.status}).`);
          }
        } finally {
          delete this.userBusy[account.username];
        }
      },
    });
  }

  if (window.Alpine) {
    console.error('NoHorny: /assets/app.js must be included before /assets/alpine.min.js, both with defer.');
  }

  document.addEventListener('alpine:init', () => {
    window.Alpine.data('landing', landing);
    window.Alpine.data('request', request);
    window.Alpine.data('admin', admin);
  });

  // Exposed for pages that need a helper outside an Alpine scope, and for debugging.
  window.NoHorny = { api, ApiError, csrfHeaders, ...helpers };
})();
