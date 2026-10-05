// The requests page of the admin panel, with the activity chart, the filtered request list and the viewer.

import { Meta, Title } from '@solidjs/meta';
import {
  createEffect,
  createMemo,
  createSignal,
  createStore,
  Errored,
  For,
  isPending,
  Loading,
  onSettled,
  reconcile,
  refresh,
  Show,
  useContext,
} from 'solid-js';
import { ActivityChart } from '../../components/activity-chart';
import { confirm, Dialog } from '../../components/dialog';
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  ExternalLinkIcon,
  EyeIcon,
  EyeOffIcon,
  ImageOffIcon,
  RefreshIcon,
  SearchIcon,
  TrashIcon,
  XIcon,
} from '../../components/icon';
import { NetworkChart } from '../../components/network-chart';
import { KeyValues, RequestImage, VerdictCard } from '../../components/request';
import { Button, buttonClass, Chip, Meter, RatingBadge, RatingDot, Spinner, State } from '../../components/ui';
import { AdminContext } from '../../lib/admin';
import {
  BUCKETS,
  type Bucket,
  describe,
  type History,
  type HistoryRange,
  type Networks,
  type Page,
  type Request,
  requestPath,
  statusOf,
} from '../../lib/api';
import { cursorAt, parsePrefix, toLocalInput } from '../../lib/cursor';
import {
  bucket,
  clientLabel,
  duration,
  formatTime,
  number,
  ratingLabel,
  relativeTime,
  requesterLabel,
  share,
  shortId,
} from '../../lib/format';
import { call, SessionExpired } from '../../lib/session';

export default function Requests() {
  // A click on a bar of the chart lists the requests of its slot
  const [slot, setSlot] = createSignal<{ from: number; to: number } | null>(null);
  return (
    <>
      <Title>Requests · NoHorny admin</Title>
      <Meta name="robots" content="noindex, nofollow" />
      <Overview onSelect={(from, to) => setSlot({ from, to })} />
      <RequestList slot={slot()} />
    </>
  );
}

const RANGES: { value: HistoryRange; label: string }[] = [
  { value: '24h', label: '24 h' },
  { value: '7d', label: '7 d' },
  { value: '30d', label: '30 d' },
  { value: '90d', label: '90 d' },
];

/** The minimum delay between two reloads of the history triggered by the live statistics. */
const REFRESH_MILLIS = 15000;

const SEGMENTED = 'flex min-w-0 max-w-full gap-1 overflow-x-auto rounded-full border border-line bg-bg-2 p-1';
const SEGMENT =
  'shrink-0 rounded-full border border-transparent px-3 py-1 font-mono text-xs text-ink-2 transition-colors hover:text-ink aria-pressed:border-accent-edge aria-pressed:bg-accent-soft aria-pressed:text-on-accent-soft pointer-coarse:py-2';

function Overview(props: { onSelect: (from: number, to: number) => void }) {
  const admin = useContext(AdminContext);
  const [range, setRange] = createSignal<HistoryRange>('30d');
  const [shown, setShown] = createStore<Record<Bucket, boolean>>({ safe: true, warn: true, nsfw: true, failed: true });

  const history = createMemo(() => call<History>(`/api/stats/history?range=${range()}`));
  const networks = createMemo(() => call<Networks>(`/api/stats/networks?range=${range()}`));

  // Reloads the history when the live total changes. A busy server classifies many images per second, so the reloads
  // wait at least REFRESH_MILLIS apart
  let lastLoad = Date.now();
  let pending: ReturnType<typeof setTimeout> | undefined;
  createEffect(
    () => admin.stats.data()?.total,
    () => {
      if (pending) return;
      pending = setTimeout(
        () => {
          pending = undefined;
          lastLoad = Date.now();
          refresh(history);
          refresh(networks);
        },
        Math.max(0, lastLoad + REFRESH_MILLIS - Date.now()),
      );
    },
    { defer: true },
  );
  onSettled(() => () => clearTimeout(pending));

  const totals = createMemo(() => {
    const totals: Record<Bucket, number> = { safe: 0, warn: 0, nsfw: 0, failed: 0 };
    for (const slot of history().slots) for (const key of BUCKETS) totals[key] += slot.ratings[key] ?? 0;
    return totals;
  });
  const rangeTotal = () => BUCKETS.reduce((sum, key) => sum + totals()[key], 0);

  return (
    <section class="card flex flex-col gap-6 p-6" aria-labelledby="activity">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div class="flex items-baseline gap-6">
          <h1 id="activity" class="text-3xl">
            Activity
          </h1>
          <p class="text-ink-2">
            <b class="font-normal text-2xl text-ink">{number(admin.stats.data()?.total)}</b> all time
          </p>
        </div>
        <fieldset class={SEGMENTED}>
          <legend class="sr-only">Range</legend>
          <For each={RANGES}>
            {(option) => (
              <button
                type="button"
                class={SEGMENT}
                aria-pressed={range() === option.value ? 'true' : 'false'}
                onClick={() => setRange(option.value)}>
                {option.label}
              </button>
            )}
          </For>
        </fieldset>
      </div>

      <Errored
        fallback={(error, reset) => (
          <Show when={!(error() instanceof SessionExpired)}>
            <State title="Could not load the activity" tone="error">
              <p class="text-ink-2">{describe(error(), 'The server answered with an error')}</p>
              <Button onClick={() => reset()}>Retry</Button>
            </State>
          </Show>
        )}>
        <Loading
          fallback={
            <div class="grid h-72 place-items-center text-ink-3">
              <Spinner class="size-5" />
            </div>
          }>
          <div class={['flex flex-col gap-6 transition-opacity', { 'opacity-60': isPending(() => history()) }]}>
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <For each={BUCKETS}>
                {(key) => (
                  <button
                    type="button"
                    aria-pressed={shown[key] ? 'true' : 'false'}
                    title={shown[key] ? `Hide ${ratingLabel(key)} from the chart` : `Show ${ratingLabel(key)}`}
                    class={[
                      'flex flex-col items-start gap-1 rounded-xl border px-4 py-3 text-left transition-colors',
                      shown[key]
                        ? 'border-line bg-surface-2 hover:border-line-strong'
                        : 'border-line-strong border-dashed text-ink-3 hover:bg-surface-2',
                    ]}
                    onClick={() =>
                      setShown((draft) => {
                        draft[key] = !draft[key];
                      })
                    }>
                    <span class="label flex items-center gap-2">
                      <RatingDot rating={key} hollow={!shown[key]} />
                      {ratingLabel(key)}
                    </span>
                    <span class="text-2xl">{number(totals()[key])}</span>
                    <span class="font-mono text-ink-3 text-xs">{share(totals()[key], rangeTotal()) || '–'}</span>
                  </button>
                )}
              </For>
            </div>
            <ActivityChart history={history()} shown={shown} onSelect={props.onSelect} />
            <p class="sr-only">
              Classified requests over the range:{' '}
              {BUCKETS.map((key) => `${number(totals()[key])} ${ratingLabel(key)}`).join(', ')}.
            </p>
          </div>
        </Loading>
      </Errored>

      <section class="flex flex-col gap-4 border-line border-t pt-6" aria-labelledby="networks">
        <h2 id="networks" class="text-xl">
          Networks
        </h2>
        <Errored
          fallback={(error, reset) => (
            <Show when={!(error() instanceof SessionExpired)}>
              <State title="Could not load the networks" tone="error">
                <p class="text-ink-2">{describe(error(), 'The server answered with an error')}</p>
                <Button onClick={() => reset()}>Retry</Button>
              </State>
            </Show>
          )}>
          <Loading
            fallback={
              <div class="grid h-48 place-items-center text-ink-3">
                <Spinner class="size-5" />
              </div>
            }>
            <div class={['transition-opacity', { 'opacity-60': isPending(() => networks()) }]}>
              <NetworkChart networks={networks()} />
            </div>
          </Loading>
        </Errored>
      </section>
    </section>
  );
}

const FILTERS: { value: Bucket | null; label: string }[] = [
  { value: null, label: 'All' },
  ...BUCKETS.map((key) => ({ value: key, label: ratingLabel(key) })),
];

const PAGE_SIZE = 20;

/** The filters of the list. `id` is an identifier or its beginning, `from` and `to` are milliseconds, `to` excluded. */
interface Query {
  rating: Bucket | null;
  id: string;
  from: number | null;
  to: number | null;
}

/** Opens the picker of a date field on a click anywhere in it, not only on its calendar icon. */
function openPicker(event: MouseEvent & { currentTarget: HTMLInputElement }) {
  try {
    event.currentTarget.showPicker();
  } catch {
    // Some browsers refuse, the field stays editable by hand
  }
}

const NO_QUERY: Query = { rating: null, id: '', from: null, to: null };

const FILTER_FIELD =
  'h-9 rounded-full border border-line-strong bg-bg-2 px-3 font-mono text-ink text-xs transition-colors placeholder:text-ink-3 focus-visible:outline-offset-1 aria-invalid:border-danger pointer-coarse:h-10';

/** Past this many pages of new requests, a refresh reloads the list from the top. */
const MAX_REFRESH_PAGES = 5;

const THUMBNAIL_PLACEHOLDERS = { none: 'Not kept', stored: 'Unavailable', expired: 'Expired', purged: 'Purged' };

function RequestList(props: { slot: { from: number; to: number } | null }) {
  const admin = useContext(AdminContext);
  const [query, setQuery] = createSignal<Query>(NO_QUERY);
  /** The text of the identifier field, applied when it changes and is valid. */
  const [idText, setIdText] = createSignal('');
  const [list, setList] = createStore({
    /** Loaded requests, newest first. */
    items: [] as Request[],
    loading: false,
    exhausted: false,
    error: null as string | null,
    /** The images revealed one by one and the rows with a running action, by request identifier. */
    revealed: {} as Record<string, boolean>,
    busy: {} as Record<string, boolean>,
  });
  const [revealAll, setRevealAll] = createSignal(false);
  /** Index of the request open in the viewer, null when closed. */
  const [viewing, setViewing] = createSignal<number | null>(null);

  // Plain variables for the control flow. A store write shows only after the next flush
  let generation = 0;
  let cursor: string | null = null;
  let loading = false;
  let exhausted = false;
  let failed = false;
  let current: Query = NO_QUERY;
  let section!: HTMLElement;
  let sentinel!: HTMLDivElement;
  let observer: IntersectionObserver | undefined;

  /** The cursor of the first page: the end of the identifier prefix or the end of the range, whichever is older. */
  function startCursor(): string | null {
    const bounds = [parsePrefix(current.id)?.cursor, current.to === null ? undefined : cursorAt(current.to)];
    return bounds.reduce<string | null>(
      (oldest, bound) => (bound && (!oldest || bound < oldest) ? bound : oldest),
      null,
    );
  }

  /** Whether a request older than the loaded ones still matches. The list ends at the first one that does not. */
  function matches(item: Request): boolean {
    const prefix = parsePrefix(current.id)?.prefix;
    if (prefix && !item.id.startsWith(prefix)) return false;
    return current.from === null || Date.parse(item.createdAt) >= current.from;
  }

  const filtered = () => query().id !== '' || query().from !== null || query().to !== null;
  const idInvalid = () => idText().trim() !== '' && !parsePrefix(idText());

  /** Clears the identifier and the time range, the rating filter stays. */
  const clearFilters = () => reset({ ...NO_QUERY, rating: current.rating });

  function reset(next: Query) {
    generation++;
    current = next;
    cursor = startCursor();
    loading = false;
    exhausted = false;
    failed = false;
    setQuery(next);
    setIdText(next.id);
    setViewing(null);
    setList((draft) => {
      draft.items = [];
      draft.loading = false;
      draft.exhausted = false;
      draft.error = null;
      draft.revealed = {};
      draft.busy = {};
    });
    void loadMore();
  }

  async function loadMore() {
    if (loading || exhausted || failed) return;
    const at = generation;
    loading = true;
    setList((draft) => {
      draft.loading = true;
    });
    const params = new URLSearchParams({ limit: String(PAGE_SIZE) });
    if (current.rating) params.set('rating', current.rating);
    if (cursor) params.set('before', cursor);
    try {
      const page = await call<Page<Request>>(`/api/requests?${params}`);
      if (at !== generation) return;
      const end = page.items.findIndex((item) => !matches(item));
      const items = end === -1 ? page.items : page.items.slice(0, end);
      cursor = page.nextCursor;
      exhausted = cursor === null || end !== -1;
      setList((draft) => {
        const known = new Set(draft.items.map((item) => item.id));
        draft.items.push(...items.filter((item) => !known.has(item.id)));
        draft.exhausted = exhausted;
      });
    } catch (error) {
      if (at !== generation || error instanceof SessionExpired) return;
      failed = true;
      setList((draft) => {
        draft.error = describe(error, 'Could not load requests');
      });
    } finally {
      if (at === generation) {
        loading = false;
        setList((draft) => {
          draft.loading = false;
        });
        // A second observe() call reports the sentinel again if it is still on screen
        observer?.unobserve(sentinel);
        observer?.observe(sentinel);
      }
    }
  }

  createEffect(
    () => props.slot,
    (slot) => {
      if (!slot) return;
      reset({ ...current, from: slot.from, to: slot.to });
      section.scrollIntoView({ behavior: 'smooth', block: 'start' });
    },
    { defer: true },
  );

  /** Applies the identifier field, unless it holds something that cannot start an identifier. */
  function applyId(value: string) {
    const text = value.trim();
    if (text === current.id || (text !== '' && !parsePrefix(text))) return;
    reset({ ...current, id: text });
  }

  /** Applies a datetime-local field, an empty one removes the bound. */
  function applyTime(bound: 'from' | 'to', value: string) {
    const millis = value ? new Date(value).getTime() : null;
    if (millis === null || !Number.isNaN(millis)) reset({ ...current, [bound]: millis });
  }

  onSettled(() => {
    observer = new IntersectionObserver((entries) => entries.some((entry) => entry.isIntersecting) && loadMore(), {
      rootMargin: '0px 0px 600px 0px',
    });
    observer.observe(sentinel);
    void loadMore();
    return () => observer?.disconnect();
  });

  const [refreshing, setRefreshing] = createSignal(false);

  /**
   * Adds the requests newer than the loaded ones on top of the list, page by page until a loaded request shows up.
   * Starts over when more than MAX_REFRESH_PAGES pages are new.
   */
  async function loadNewer() {
    if (list.items.length === 0 || failed) return reset(current);
    const at = generation;
    const known = new Set(list.items.map((item) => item.id));
    const newer: Request[] = [];
    let before = startCursor();
    setRefreshing(true);
    try {
      for (let pages = 0; pages < MAX_REFRESH_PAGES; pages++) {
        const params = new URLSearchParams({ limit: String(PAGE_SIZE) });
        if (current.rating) params.set('rating', current.rating);
        if (before) params.set('before', before);
        const page = await call<Page<Request>>(`/api/requests?${params}`);
        if (at !== generation) return;
        const overlap = page.items.findIndex((item) => known.has(item.id));
        newer.push(...(overlap === -1 ? page.items : page.items.slice(0, overlap)));
        if (overlap !== -1 || page.nextCursor === null) {
          if (newer.length === 0) return;
          setViewing((index) => (index === null ? null : index + newer.length));
          setList((draft) => {
            draft.items.unshift(...newer);
          });
          return;
        }
        before = page.nextCursor;
      }
      reset(current);
    } catch (error) {
      if (at === generation && !(error instanceof SessionExpired))
        admin.notify(describe(error, 'Could not refresh the requests'), 'error');
    } finally {
      setRefreshing(false);
    }
  }

  function retry() {
    failed = false;
    setList((draft) => {
      draft.error = null;
    });
    void loadMore();
  }

  function replace(updated: Request) {
    setList((draft) => {
      // Writes only the changed fields onto the loaded request
      const item = draft.items.find((item) => item.id === updated.id);
      if (item) reconcile(updated, 'id')(item);
    });
  }

  function drop(id: string) {
    const index = list.items.findIndex((item) => item.id === id);
    if (index === -1) return;
    // The viewer moves on to the next request, or the previous one at the end of the list
    const open = viewing();
    if (open !== null && open >= index) {
      const next = open > index || index === list.items.length - 1 ? open - 1 : open;
      setViewing(next < 0 ? null : next);
    }
    setList((draft) => {
      draft.items.splice(index, 1);
    });
  }

  const revealed = (item: Request) => revealAll() || list.revealed[item.id] === true;
  const setRevealed = (item: Request, value: boolean) =>
    setList((draft) => {
      draft.revealed[item.id] = value;
    });

  /** Runs a row action while `busy` is set. Both callbacks return the status to show. */
  async function act(item: Request, action: () => Promise<string>, failed: (error: unknown) => string) {
    setList((draft) => {
      draft.busy[item.id] = true;
    });
    try {
      admin.notify(await action());
    } catch (error) {
      if (!(error instanceof SessionExpired)) admin.notify(failed(error), 'error');
    } finally {
      setList((draft) => {
        delete draft.busy[item.id];
      });
    }
  }

  async function purge(item: Request) {
    const confirmed = await confirm({
      title: `Purge the image of ${shortId(item.id)}?`,
      body: 'The image bytes are deleted immediately. The request stays as a tombstone with its verdict. This cannot be undone.',
      action: 'Purge image',
    });
    if (!confirmed) return;
    await act(
      item,
      async () => {
        replace(await call<Request>(`${requestPath(item.id)}/purge`, { method: 'POST' }));
        return `Image of ${shortId(item.id)} purged.`;
      },
      (error) => {
        if (statusOf(error) === 404) {
          drop(item.id);
          return `Request ${shortId(item.id)} no longer exists.`;
        }
        return describe(error, 'Purge failed', 'Could not reach the server, nothing was purged.');
      },
    );
  }

  async function remove(item: Request) {
    const confirmed = await confirm({
      title: `Delete request ${shortId(item.id)}?`,
      body: 'The request, its verdict and its image are removed for good and its link stops working. Statistics are not affected.',
      action: 'Delete request',
    });
    if (!confirmed) return;
    const deleted = () => {
      drop(item.id);
      return `Request ${shortId(item.id)} deleted.`;
    };
    await act(
      item,
      async () => {
        await call(requestPath(item.id), { method: 'DELETE' });
        return deleted();
      },
      (error) =>
        statusOf(error) === 404
          ? deleted()
          : describe(error, 'Deletion failed', 'Could not reach the server, nothing was deleted.'),
    );
  }

  const viewed = createMemo(() => {
    const index = viewing();
    const item = index === null ? undefined : list.items[index];
    return index === null || !item ? null : { index, item };
  });

  /** Moves the viewer by `delta` requests. Loads the next page near the end of the list. */
  function step(delta: number) {
    const index = (viewing() ?? 0) + delta;
    if (index < 0 || index >= list.items.length) return;
    setViewing(index);
    if (index >= list.items.length - 3) void loadMore();
  }

  return (
    <section ref={section} class="flex scroll-mt-20 flex-col gap-4" aria-labelledby="requests">
      <div class="flex flex-wrap items-center gap-3">
        <h2 id="requests" class="mr-2 text-3xl">
          Requests
        </h2>
        <fieldset class={[SEGMENTED, 'max-sm:order-last max-sm:w-full']}>
          <legend class="sr-only">Filter by rating</legend>
          <For each={FILTERS}>
            {(option) => (
              <button
                type="button"
                class={[SEGMENT, 'inline-flex items-center justify-center gap-1.5 max-sm:flex-1 max-sm:px-2']}
                aria-pressed={query().rating === option.value ? 'true' : 'false'}
                onClick={() => query().rating !== option.value && reset({ ...current, rating: option.value })}>
                <Show when={option.value}>{(key) => <RatingDot rating={key()} />}</Show>
                {option.label}
              </button>
            )}
          </For>
        </fieldset>
        <span class="flex-1" />
        {/* The labels stay for screen readers when small screens show the icons only */}
        <div class="flex gap-2">
          <Button
            size="sm"
            aria-pressed={revealAll() ? 'true' : 'false'}
            title={revealAll() ? 'Blur flagged images' : 'Reveal all images'}
            onClick={() => setRevealAll(!revealAll())}>
            <Show when={revealAll()} fallback={<EyeIcon />}>
              <EyeOffIcon />
            </Show>
            <span class="max-sm:sr-only">{revealAll() ? 'Blur flagged images' : 'Reveal all images'}</span>
          </Button>
          <Button
            size="sm"
            busy={refreshing()}
            title="Load the newer requests"
            onClick={() => {
              void loadNewer();
              admin.stats.refresh();
            }}>
            <Show when={!refreshing()}>
              <RefreshIcon />
            </Show>
            <span class="max-sm:sr-only">Refresh</span>
          </Button>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <label class="relative max-sm:w-full">
          <span class="sr-only">Find a request by its identifier or link</span>
          <SearchIcon class="pointer-events-none absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-ink-3" />
          <input
            type="search"
            class={[FILTER_FIELD, 'w-full pl-8 sm:w-64']}
            placeholder="Request id or link"
            spellcheck={false}
            autocapitalize="none"
            value={idText()}
            aria-invalid={idInvalid() ? 'true' : undefined}
            aria-describedby={idInvalid() ? 'id-problem' : undefined}
            onInput={(event) => setIdText(event.currentTarget.value)}
            onChange={(event) => applyId(event.currentTarget.value)}
          />
        </label>
        <div class="grid gap-2 max-sm:w-full sm:flex sm:items-center">
          <label class="grid grid-cols-[2.5rem_1fr] items-center gap-2 sm:flex">
            <span class="label">From</span>
            <input
              type="datetime-local"
              class={[FILTER_FIELD, 'w-full cursor-pointer font-sans']}
              value={query().from === null ? '' : toLocalInput(query().from ?? 0)}
              max={query().to === null ? undefined : toLocalInput(query().to ?? 0)}
              onClick={openPicker}
              onChange={(event) => applyTime('from', event.currentTarget.value)}
            />
          </label>
          <label class="grid grid-cols-[2.5rem_1fr] items-center gap-2 sm:flex">
            <span class="label">To</span>
            <input
              type="datetime-local"
              class={[FILTER_FIELD, 'w-full cursor-pointer font-sans']}
              value={query().to === null ? '' : toLocalInput(query().to ?? 0)}
              min={query().from === null ? undefined : toLocalInput(query().from ?? 0)}
              onClick={openPicker}
              onChange={(event) => applyTime('to', event.currentTarget.value)}
            />
          </label>
        </div>
        <Show when={filtered()}>
          <Button size="sm" variant="quiet" onClick={clearFilters}>
            <XIcon />
            Clear filters
          </Button>
        </Show>
      </div>
      <Show when={idInvalid()}>
        <p id="id-problem" class="-mt-1 text-danger text-xs">
          Paste a request link or the start of its identifier, made of the digits 0-9 and the letters a-f.
        </p>
      </Show>

      <div class="card overflow-hidden">
        <ul class="divide-y divide-line">
          <For each={list.items}>
            {(item, index) => (
              <li>
                <RequestRow
                  request={item}
                  revealed={revealed(item)}
                  busy={list.busy[item.id] === true}
                  onReveal={(value) => setRevealed(item, value)}
                  onOpen={() => setViewing(index())}
                  onPurge={() => purge(item)}
                  onDelete={() => remove(item)}
                />
              </li>
            )}
          </For>
        </ul>
        <div ref={sentinel} class="flex flex-col items-center gap-3 p-6 text-ink-3 text-sm">
          <Show when={list.loading}>
            <Spinner class="size-5 text-ink-3" />
          </Show>
          <Show when={list.error}>
            <p role="alert" class="text-danger">
              {list.error}
            </p>
            <Button size="sm" onClick={retry}>
              Retry
            </Button>
          </Show>
          <Show when={list.exhausted}>
            <Show
              when={list.items.length > 0}
              fallback={
                <div class="flex flex-col items-center gap-3 py-6">
                  <p class="text-base text-ink">
                    {filtered() || query().rating ? 'No requests match these filters' : 'No requests yet'}
                  </p>
                  <p>
                    {filtered() || query().rating
                      ? 'Widen the time range or clear the filters.'
                      : 'The requests show up here as the servers send them.'}
                  </p>
                  <Show when={filtered() || query().rating}>
                    <Button size="sm" onClick={() => reset(NO_QUERY)}>
                      <XIcon />
                      Clear all filters
                    </Button>
                  </Show>
                </div>
              }>
              <p>
                {`End of the ${filtered() ? 'results' : 'list'} · ${number(list.items.length)} request${list.items.length === 1 ? '' : 's'}`}
              </p>
            </Show>
          </Show>
        </div>
      </div>

      <Dialog
        open={viewed() !== null}
        onClose={() => setViewing(null)}
        label="Request viewer"
        onKeyDown={(event) => {
          if (event.key === 'ArrowLeft') step(-1);
          else if (event.key === 'ArrowRight') step(1);
        }}
        class="w-[min(64rem,calc(100vw-2rem))]">
        <Show when={viewed()}>
          {(value) => (
            <div class="flex flex-col gap-4 p-5">
              <header class="flex items-center gap-2">
                <h2 class="min-w-0 flex-1 text-xl">
                  Request <span class="whitespace-nowrap font-mono text-ink-3">{shortId(value().item.id)}</span>
                </h2>
                <span class="whitespace-nowrap font-mono text-ink-3 text-xs tabular-nums max-sm:hidden">
                  {value().index + 1} / {list.items.length}
                  {list.exhausted ? '' : '+'}
                </span>
                <Button
                  size="icon"
                  aria-label="Previous request"
                  disabled={value().index === 0}
                  onClick={() => step(-1)}>
                  <ChevronLeftIcon />
                </Button>
                <Button
                  size="icon"
                  aria-label="Next request"
                  disabled={value().index === list.items.length - 1 && list.exhausted}
                  onClick={() => step(1)}>
                  <ChevronRightIcon />
                </Button>
                <Button size="icon" variant="quiet" aria-label="Close" onClick={() => setViewing(null)}>
                  <XIcon />
                </Button>
              </header>
              <div class="grid gap-4 md:grid-cols-[1.1fr_1fr]">
                <RequestImage
                  class="aspect-square self-start"
                  request={value().item}
                  revealed={revealed(value().item)}
                  onReveal={(revealed) => setRevealed(value().item, revealed)}
                />
                <div class="flex flex-col gap-4">
                  <VerdictCard request={value().item} />
                  <section class="px-1">
                    <KeyValues
                      rows={[
                        [
                          'Created',
                          <time datetime={value().item.createdAt} title={formatTime(value().item.createdAt)}>
                            {relativeTime(value().item.createdAt)}
                          </time>,
                        ],
                        ['Client', clientLabel(value().item.client)],
                        ['Plugin', <code>{value().item.version ? `v${value().item.version}` : 'unknown'}</code>],
                        ['Address', <code class="break-all">{value().item.remoteAddress ?? '–'}</code>],
                        ['Requester', requesterLabel(value().item.requester)],
                      ]}
                    />
                  </section>
                  <div class="flex flex-wrap gap-2 border-line border-t pt-4">
                    <a class={buttonClass()} href={`/requests/${value().item.id}`}>
                      <ExternalLinkIcon />
                      Full page
                    </a>
                    <Show when={value().item.image.state === 'stored'}>
                      <Button
                        variant="danger"
                        busy={list.busy[value().item.id] === true}
                        onClick={() => purge(value().item)}>
                        <ImageOffIcon />
                        Purge image
                      </Button>
                    </Show>
                    <Button
                      variant="solid-danger"
                      busy={list.busy[value().item.id] === true}
                      onClick={() => remove(value().item)}>
                      <TrashIcon />
                      Delete
                    </Button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </Show>
      </Dialog>
    </section>
  );
}

function RequestRow(props: {
  request: Request;
  revealed: boolean;
  busy: boolean;
  onReveal: (revealed: boolean) => void;
  onOpen: () => void;
  onPurge: () => void;
  onDelete: () => void;
}) {
  const rating = () => bucket(props.request);
  const [broken, setBroken] = createSignal(false);
  const url = () => (props.request.image.state === 'stored' && !broken() ? props.request.image.url : undefined);

  // A click anywhere on the row opens the viewer, except on its links and buttons.
  // Keyboard users reach the request through its link.
  return (
    // biome-ignore lint/a11y/noStaticElementInteractions lint/a11y/useKeyWithClickEvents: see above
    <div
      class="grid cursor-pointer grid-cols-[4rem_1fr_auto] items-center gap-4 px-4 py-3 transition hover:bg-surface-2 md:grid-cols-[4rem_10rem_1fr_9rem_auto]"
      onClick={(event) => !(event.target as Element).closest('a, button') && props.onOpen()}>
      <button
        type="button"
        class={[
          'relative grid size-16 place-items-center overflow-hidden rounded-lg border border-line bg-bg-2 text-ink-3',
        ]}
        aria-label={url() ? (props.revealed ? 'Blur the image' : 'Reveal the image') : 'Open the request'}
        onClick={() => (url() && rating() !== 'safe' ? props.onReveal(!props.revealed) : props.onOpen())}>
        <Show
          when={url()}
          fallback={
            <span class="flex flex-col items-center gap-0.5 text-[0.6rem]">
              <ImageOffIcon />
              {THUMBNAIL_PLACEHOLDERS[props.request.image.state]}
            </span>
          }>
          {(src) => (
            <img
              src={src()}
              alt=""
              loading="lazy"
              class={[
                'veil size-full object-cover [image-rendering:pixelated]',
                { 'blur-veil': rating() !== 'safe' && !props.revealed },
              ]}
              onError={() => setBroken(true)}
            />
          )}
        </Show>
      </button>

      <div class="flex min-w-0 flex-col items-start gap-2">
        <RatingBadge rating={rating()} />
        <Show when={props.request.confidence != null}>
          <Meter value={props.request.confidence ?? 0} rating={rating()} class="w-28" />
        </Show>
        <span class="flex min-w-0 max-w-full items-baseline gap-2 text-xs md:hidden">
          <a class="link-quiet font-mono" href={`/requests/${props.request.id}`}>
            {shortId(props.request.id)}
          </a>
          <time class="truncate text-ink-3" datetime={props.request.createdAt} title={props.request.createdAt}>
            {relativeTime(props.request.createdAt)}
          </time>
        </span>
      </div>

      <div class="hidden min-w-0 flex-col gap-1 md:flex">
        <span class="flex flex-wrap items-center gap-2">
          <a class="link-quiet font-mono text-sm" href={`/requests/${props.request.id}`}>
            {shortId(props.request.id)}
          </a>
          <Chip>{props.request.version ? `v${props.request.version}` : 'unknown'}</Chip>
          <span class="truncate text-sm">{clientLabel(props.request.client)}</span>
        </span>
        <span class="truncate font-mono text-ink-3 text-xs">{props.request.remoteAddress}</span>
      </div>

      <div class="hidden flex-col md:flex">
        <time class="text-sm" datetime={props.request.createdAt} title={formatTime(props.request.createdAt)}>
          {relativeTime(props.request.createdAt)}
        </time>
        <span class="font-mono text-ink-3 text-xs">{duration(props.request.durationMillis)}</span>
      </div>

      {/* As wide as both buttons, so the column does not move when a row has no image to purge */}
      <div class="flex w-19 justify-end gap-1 pointer-coarse:w-23">
        <Show when={props.request.image.state === 'stored'}>
          <Button
            size="icon"
            variant="quiet"
            aria-label="Purge the image"
            title="Purge the image"
            disabled={props.busy}
            onClick={() => props.onPurge()}>
            <ImageOffIcon />
          </Button>
        </Show>
        <Button
          size="icon"
          variant="quiet-danger"
          aria-label="Delete the request"
          title="Delete the request"
          disabled={props.busy}
          onClick={() => props.onDelete()}>
          <TrashIcon />
        </Button>
      </div>
    </div>
  );
}
