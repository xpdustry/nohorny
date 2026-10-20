// The requests per Mindustry network and from the local addresses, as a donut and a legend listing every network, from
// GET /api/stats/networks. The other requesters share a slice, and the legend lists them under the networks. A click on
// a network, the local addresses or another requester lists its requests.

import { createMemo, createSignal, For, Show } from 'solid-js';
import type { Networks, RequesterFilter, RequesterType } from '../lib/api';
import { number, share } from '../lib/format';

/** The networks drawn in their own colour, the others share the "Other networks" slice. The local addresses always have their own. */
const COLORED = 3;

/** The colours of the slices, App.css validates the categorical ones for colour blindness against the card. */
const COLORS = ['var(--network-1)', 'var(--network-2)', 'var(--network-3)'];
const OTHER_COLOR = 'var(--ink-3)';
const LOCAL_COLOR = 'var(--accent)';
const UNLISTED_COLOR = 'var(--line-strong)';

/** The requesters without a network, an empty type for the requests counted before the requesters were recorded. */
const UNLISTED_LABELS: Record<Exclude<RequesterType, 'local'> | '', string> = {
  user: 'Accounts',
  'mindustry-network': 'Servers without network',
  anonymous: 'Anonymous clients',
  '': 'Unknown requester',
};

/** The gap between two slices, in hundredths of the circumference. */
const GAP = 0.6;

interface Slice {
  key: string;
  label: string;
  count: number;
  color: string;
}

/** A row of the legend, with the key of the slice it is drawn in and the filter of its requests, if it has one. */
interface Row extends Slice {
  slice: string;
  filter?: RequesterFilter;
}

/** The filters of the requesters without a network. The unknown requests are gone, they have none. */
const UNLISTED_FILTERS: Partial<Record<RequesterType | '', Omit<RequesterFilter, 'label'>>> = {
  user: { type: 'user', name: null },
  'mindustry-network': { type: 'mindustry-network', name: '' },
  anonymous: { type: 'anonymous', name: null },
};

export function NetworkChart(props: { networks: Networks; onSelect?: (filter: RequesterFilter) => void }) {
  /** The slice under the pointer, or the slice of the row under it. */
  const [hovered, setHovered] = createSignal<string | null>(null);
  /**
   * The row under the pointer, or the row of the slice under it. A shared slice, like "Other networks", highlights
   * none of its rows, and a row of a shared slice highlights only itself.
   */
  const [hoveredRow, setHoveredRow] = createSignal<string | null>(null);
  const hover = (slice: string | null, row: string | null) => {
    setHovered(slice);
    setHoveredRow(row);
  };

  const local = () => props.networks.requesters.find((requester) => requester.name === 'local')?.count ?? 0;

  /** The networks and the local addresses, the most active first. */
  const rows = createMemo(() => {
    const entries = props.networks.networks.map((network) => ({
      key: `network:${network.name}`,
      label: network.name,
      count: network.count,
    }));
    if (local() > 0) entries.push({ key: 'local', label: 'Local', count: local() });
    // Stable, so the networks keep their order by name on a tie
    entries.sort((a, b) => b.count - a.count);
    let colored = 0;
    return entries.map((entry): Row => {
      if (entry.key === 'local')
        return {
          ...entry,
          color: LOCAL_COLOR,
          slice: entry.key,
          filter: { type: 'local', name: null, label: entry.label },
        };
      const filter: RequesterFilter = { type: 'mindustry-network', name: entry.label, label: entry.label };
      const index = colored++;
      return index < COLORED
        ? { ...entry, color: COLORS[index], slice: entry.key, filter }
        : { ...entry, color: OTHER_COLOR, slice: 'other', filter };
    });
  });

  /** The requesters without a network, sharing the unlisted slice, the most active first then the unknown one. */
  const unlisted = createMemo(() => {
    const named = props.networks.requesters.filter(
      (requester) => requester.name !== 'local' && requester.name !== '' && requester.count > 0,
    );
    // Whatever the networks and the known requesters leave, the requests counted before the requesters were recorded
    const unknown = props.networks.total - [...rows(), ...named].reduce((sum, entry) => sum + entry.count, 0);
    const entries = unknown > 0 ? [...named, { name: '' as const, count: unknown }] : named;
    return entries.map((entry): Row => {
      const label = UNLISTED_LABELS[entry.name as keyof typeof UNLISTED_LABELS];
      const filter = UNLISTED_FILTERS[entry.name];
      return {
        key: `requester:${entry.name}`,
        label,
        count: entry.count,
        color: UNLISTED_COLOR,
        slice: 'unlisted',
        filter: filter && { ...filter, label },
      };
    });
  });

  const slices = createMemo(() => {
    const slices: Slice[] = rows().filter((row) => row.slice === row.key);
    const other = rows()
      .filter((row) => row.slice === 'other')
      .reduce((sum, row) => sum + row.count, 0);
    const listed = rows().reduce((sum, row) => sum + row.count, 0);
    if (other > 0) slices.push({ key: 'other', label: 'Other networks', count: other, color: OTHER_COLOR });
    if (props.networks.total > listed)
      slices.push({
        key: 'unlisted',
        label: 'No network',
        count: props.networks.total - listed,
        color: UNLISTED_COLOR,
      });
    return slices;
  });

  /** The arcs of the slices, as dash patterns on a circle of circumference 100. */
  const arcs = createMemo(() => {
    const total = props.networks.total;
    let start = 0;
    return slices().map((slice) => {
      const length = (slice.count / total) * 100;
      const arc = { slice, start, length: slices().length > 1 ? Math.max(0, length - GAP) : length };
      start += length;
      return arc;
    });
  });

  const focus = () => slices().find((slice) => slice.key === hovered());
  /** The filter of a slice drawn for a single row, the shared ones have none. */
  const sliceFilter = (key: string) => rows().find((row) => row.key === key)?.filter;
  /** Lists the requests of a row, when it has a filter and the chart is clickable. */
  const select = (filter: RequesterFilter | undefined) =>
    filter && props.onSelect ? () => props.onSelect?.(filter) : undefined;

  return (
    <Show
      when={props.networks.total > 0}
      fallback={<p class="py-8 text-center text-ink-3">No requests in this range.</p>}>
      <div class="grid items-center gap-8 sm:grid-cols-[auto_1fr]">
        <div class="relative mx-auto size-48 shrink-0">
          <svg
            viewBox="0 0 100 100"
            class="size-full -rotate-90"
            aria-hidden="true"
            onMouseLeave={() => hover(null, null)}>
            <For each={arcs()} keyed={(arc) => arc.slice.key}>
              {(arc) => (
                // biome-ignore lint/a11y/noStaticElementInteractions: the legend next to the donut has the same counts and buttons
                <circle
                  cx="50"
                  cy="50"
                  r="40"
                  fill="none"
                  pathLength="100"
                  stroke={arc().slice.color}
                  stroke-width={hovered() === arc().slice.key ? 16 : 13}
                  stroke-dasharray={`${arc().length} ${100 - arc().length}`}
                  stroke-dashoffset={-arc().start}
                  class={[
                    'transition-[opacity,stroke-width] duration-200',
                    { 'opacity-35': hovered() !== null && hovered() !== arc().slice.key },
                    { 'cursor-pointer': props.onSelect !== undefined && sliceFilter(arc().slice.key) !== undefined },
                  ]}
                  onMouseEnter={() => hover(arc().slice.key, arc().slice.key)}
                  onClick={() => {
                    const filter = sliceFilter(arc().slice.key);
                    if (filter) props.onSelect?.(filter);
                  }}
                />
              )}
            </For>
          </svg>
          <div
            class="pointer-events-none absolute inset-0 grid place-content-center px-8 text-center"
            aria-hidden="true">
            <Show
              when={focus()}
              fallback={
                <>
                  <b class="font-normal text-3xl">{number(props.networks.total)}</b>
                  <span class="label">requests</span>
                </>
              }>
              {(slice) => (
                <>
                  <b class="font-normal text-3xl">{share(slice().count, props.networks.total)}</b>
                  <span class="line-clamp-2 text-ink-2 text-xs">{slice().label}</span>
                </>
              )}
            </Show>
          </div>
        </div>

        <ul class="flex max-h-80 w-full max-w-md flex-col overflow-y-auto text-sm">
          <For each={rows()} keyed={(row) => row.key}>
            {(row) => (
              <LegendRow
                label={row().label}
                count={row().count}
                total={props.networks.total}
                color={row().color}
                active={hoveredRow() === row().key}
                onHover={(on) => (on ? hover(row().slice, row().key) : hover(null, null))}
                onSelect={select(row().filter)}
              />
            )}
          </For>
          <For each={unlisted()} keyed={(row) => row.key}>
            {(row, index) => (
              <LegendRow
                label={row().label}
                count={row().count}
                total={props.networks.total}
                color={row().color}
                active={hoveredRow() === row().key}
                onHover={(on) => (on ? hover(row().slice, row().key) : hover(null, null))}
                onSelect={select(row().filter)}
                class={['text-ink-3', { 'mt-1 border-line border-t pt-1': index() === 0 }]}
              />
            )}
          </For>
        </ul>
      </div>
    </Show>
  );
}

function LegendRow(props: {
  label: string;
  count: number;
  total: number;
  color: string;
  active: boolean;
  onHover: (on: boolean) => void;
  /** Lists the requests of the row. Without it, the row is not clickable. */
  onSelect?: () => void;
  class?: string | (string | Record<string, boolean>)[];
}) {
  return (
    <li class={props.class} onMouseEnter={() => props.onHover(true)} onMouseLeave={() => props.onHover(false)}>
      {/* A button even when not clickable, so every row lines up the same */}
      <button
        type="button"
        disabled={!props.onSelect}
        class={[
          'flex w-full items-center gap-3 rounded-lg px-2 py-1.5 text-left transition enabled:cursor-pointer',
          { 'bg-surface-2': props.active },
        ]}
        onClick={() => props.onSelect?.()}>
        <span aria-hidden="true" class="size-2.5 shrink-0 rounded-sm" style={{ background: props.color }} />
        <span class="min-w-0 flex-1 truncate" data-tooltip={props.label} data-tooltip-overflow>
          {props.label}
        </span>
        <span class="font-mono text-ink-2 text-xs tabular-nums">{number(props.count)}</span>
        <span class="w-12 text-right font-mono text-ink-3 text-xs tabular-nums">{share(props.count, props.total)}</span>
      </button>
    </li>
  );
}
