// The requests per Mindustry network, as a donut and a legend listing every network, from GET /api/stats/networks.

import { createMemo, createSignal, For, Show } from 'solid-js';
import type { Networks } from '../lib/api';
import { number, share } from '../lib/format';

/** The networks drawn in their own colour, the others share the "Other networks" slice. */
const COLORED = 3;

/** The colours of the slices, App.css validates the categorical ones for colour blindness against the card. */
const COLORS = ['var(--network-1)', 'var(--network-2)', 'var(--network-3)'];
const OTHER_COLOR = 'var(--ink-3)';
const UNLISTED_COLOR = 'var(--line-strong)';

/** The gap between two slices, in hundredths of the circumference. */
const GAP = 0.6;

interface Slice {
  key: string;
  label: string;
  count: number;
  color: string;
}

export function NetworkChart(props: { networks: Networks }) {
  const [hovered, setHovered] = createSignal<string | null>(null);

  const slices = createMemo(() => {
    const { networks, total } = props.networks;
    const listed = networks.reduce((sum, network) => sum + network.count, 0);
    const other = networks.slice(COLORED).reduce((sum, network) => sum + network.count, 0);
    const slices: Slice[] = networks.slice(0, COLORED).map((network, index) => ({
      key: network.name,
      label: network.name,
      count: network.count,
      color: COLORS[index],
    }));
    if (other > 0) slices.push({ key: 'other', label: 'Other networks', count: other, color: OTHER_COLOR });
    if (total > listed)
      slices.push({ key: 'unlisted', label: 'Not a listed server', count: total - listed, color: UNLISTED_COLOR });
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

  /** The slice of a legend row, the networks past the coloured ones belong to "Other networks". */
  const sliceOf = (index: number) => (index < COLORED ? props.networks.networks[index].name : 'other');
  const focus = () => slices().find((slice) => slice.key === hovered());

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
            onMouseLeave={() => setHovered(null)}>
            <For each={arcs()} keyed={(arc) => arc.slice.key}>
              {(arc) => (
                // biome-ignore lint/a11y/noStaticElementInteractions: the legend next to the donut lists the same counts
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
                  ]}
                  onMouseEnter={() => setHovered(arc().slice.key)}
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
          <For each={props.networks.networks} keyed={(network) => network.name}>
            {(network, index) => (
              <LegendRow
                label={network().name}
                count={network().count}
                total={props.networks.total}
                color={index() < COLORED ? COLORS[index()] : OTHER_COLOR}
                active={hovered() === sliceOf(index())}
                onHover={(on) => setHovered(on ? sliceOf(index()) : null)}
              />
            )}
          </For>
          <Show when={slices().find((slice) => slice.key === 'unlisted')}>
            {(slice) => (
              <LegendRow
                label={slice().label}
                count={slice().count}
                total={props.networks.total}
                color={UNLISTED_COLOR}
                active={hovered() === 'unlisted'}
                onHover={(on) => setHovered(on ? 'unlisted' : null)}
                class="mt-1 border-line border-t pt-1 text-ink-3"
              />
            )}
          </Show>
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
  class?: string;
}) {
  return (
    <li
      class={[
        'flex items-center gap-3 rounded-lg px-2 py-1.5 transition',
        { 'bg-surface-2': props.active },
        props.class,
      ]}
      onMouseEnter={() => props.onHover(true)}
      onMouseLeave={() => props.onHover(false)}>
      <span aria-hidden="true" class="size-2.5 shrink-0 rounded-sm" style={{ background: props.color }} />
      <span class="min-w-0 flex-1 truncate" data-tooltip={props.label}>
        {props.label}
      </span>
      <span class="font-mono text-ink-2 text-xs tabular-nums">{number(props.count)}</span>
      <span class="w-12 text-right font-mono text-ink-3 text-xs tabular-nums">{share(props.count, props.total)}</span>
    </li>
  );
}
