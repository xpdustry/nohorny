import { createMemo, createSignal, For, Show } from 'solid-js';
import { BUCKETS, type Bucket, type History } from '../lib/api';
import { number, ratingLabel } from '../lib/format';
import { RATING_FILL, RatingDot } from './ui';

const TICKS = 4;

/** The most labels along the time axis. */
const MAX_LABELS = 8;

/** 1.5rem wide, narrower when the slots are packed, and half the slot when they are few, like the 7 days. */
const BAR_WIDTH = 'max(50%, min(85%, 1.5rem))';

/** Rounds the top of the axis, so the ticks read 0, 5, 10, 15 rather than 0, 4.25, 8.5. */
function roundUpToTickStep(max: number): number {
  if (max <= TICKS) return TICKS;
  const step = max / TICKS;
  const magnitude = 10 ** Math.floor(Math.log10(step));
  const nice = [1, 2, 2.5, 5, 10].find((factor) => factor * magnitude >= step) ?? 10;
  return nice * magnitude * TICKS;
}

/** Daily slots are UTC days. */
function slotLabel(start: string, slot: History['slot']): string {
  const date = new Date(start);
  return slot === 'hour'
    ? date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' });
}

const SLOT_MILLIS = { hour: 3_600_000, day: 86_400_000 };

/** `onSelect` receives the bounds of a clicked slot, the end excluded. */
export function ActivityChart(props: {
  history: History;
  shown: Record<Bucket, boolean>;
  onSelect?: (range: { fromMillis: number; toMillis: number }) => void;
}) {
  const [hovered, setHovered] = createSignal<number | null>(null);
  const visible = createMemo(() => BUCKETS.filter((key) => props.shown[key]));
  // Only the ratings present get a segment, so the 2px gap is only drawn between two of them.
  const bars = createMemo(() =>
    props.history.slots.map((slot) => {
      const parts = visible()
        .filter((key) => (slot.ratings[key] ?? 0) > 0)
        .map((key) => ({ key, count: slot.ratings[key] ?? 0 }));
      return { slot, parts, total: parts.reduce((sum, part) => sum + part.count, 0) };
    }),
  );
  const max = createMemo(() => roundUpToTickStep(Math.max(0, ...bars().map((bar) => bar.total))));
  const ticks = createMemo(() =>
    Array.from({ length: TICKS + 1 }, (_, index) => ({
      value: (max() / TICKS) * (TICKS - index),
      at: (index / TICKS) * 100,
    })),
  );
  const labelEvery = () => Math.ceil(props.history.slots.length / MAX_LABELS);

  return (
    <div class="relative flex gap-3" aria-hidden="true">
      <div
        class="relative h-56 shrink-0 font-mono text-[0.65rem] text-ink-3 tabular-nums"
        style={{ width: `${number(max()).length}ch` }}>
        <For each={ticks()} keyed={false}>
          {(tick) => (
            <span class="absolute right-0 -translate-y-1/2 leading-none" style={{ top: `${tick().at}%` }}>
              {number(tick().value)}
            </span>
          )}
        </For>
      </div>
      <div class="flex min-w-0 flex-1 flex-col gap-2">
        <div class="relative h-56 border-line border-b">
          <For each={ticks().slice(0, -1)} keyed={false}>
            {(tick) => <span class="absolute inset-x-0 border-line/60 border-t" style={{ top: `${tick().at}%` }} />}
          </For>
          {/* The tooltip is decorative. Screen readers get the totals from the text below the chart */}
          <div class="absolute inset-0 flex items-end gap-[3px]" onPointerLeave={() => setHovered(null)}>
            <For each={bars()} keyed={(bar) => bar.slot.start}>
              {(bar, index) => (
                // biome-ignore lint/a11y/noStaticElementInteractions lint/a11y/useKeyWithClickEvents: the list has its own time range fields
                <div
                  class={[
                    'flex h-full min-w-0 flex-1 flex-col justify-end rounded-t-sm transition-colors hover:bg-surface-3/40',
                    { 'cursor-pointer': props.onSelect !== undefined },
                  ]}
                  // A tap selects the slot without leaving a tooltip behind
                  onPointerEnter={(event) => event.pointerType === 'mouse' && setHovered(index())}
                  onClick={() => {
                    const fromMillis = Date.parse(bar().slot.start);
                    props.onSelect?.({ fromMillis, toMillis: fromMillis + SLOT_MILLIS[props.history.slot] });
                  }}>
                  <Show when={bar().total > 0}>
                    <div
                      class="flex flex-col-reverse self-center overflow-hidden rounded-t-[4px]"
                      style={{ height: `${(bar().total / max()) * 100}%`, width: BAR_WIDTH }}>
                      {/* At least 4px of colour per segment, so a handful of failures among hundreds still shows */}
                      <For each={bar().parts} keyed={(part) => part.key}>
                        {(part) => (
                          <span
                            class={[
                              'min-h-1.5 border-surface border-t-2 last:min-h-1 last:border-t-0',
                              RATING_FILL[part().key],
                            ]}
                            style={{ 'flex-grow': part().count }}
                          />
                        )}
                      </For>
                    </div>
                  </Show>
                </div>
              )}
            </For>
          </div>
          {/* Sized to its content, the room right of its anchor would otherwise wrap it towards the end of the chart */}
          <Show when={hovered() !== null && props.history.slots[hovered() ?? 0]}>
            {(slot) => (
              <div
                class="pointer-events-none absolute top-0 z-10 w-max min-w-40 rounded-xl border border-line-strong bg-surface-2 p-3 text-sm shadow-float"
                style={{
                  left: `${(((hovered() ?? 0) + 0.5) / props.history.slots.length) * 100}%`,
                  transform: `translateX(${(hovered() ?? 0) > props.history.slots.length / 2 ? 'calc(-100% - 0.75rem)' : '0.75rem'})`,
                }}>
                <p class="mb-2">{slotLabel(slot().start, props.history.slot)}</p>
                <ul class="flex flex-col gap-1 font-mono text-ink-2 text-xs">
                  <For each={[...visible()].reverse()}>
                    {(key) => (
                      <li class="flex items-center gap-2">
                        <RatingDot rating={key} />
                        <span class="flex-1">{ratingLabel(key)}</span>
                        {number(slot().ratings[key] ?? 0)}
                      </li>
                    )}
                  </For>
                  <li class="mt-1 flex justify-between border-line border-t pt-1 text-ink">
                    <span>Total</span>
                    {number(bars()[hovered() ?? 0]?.total)}
                  </li>
                </ul>
                <Show when={props.onSelect}>
                  <p class="mt-2 text-ink-3 text-xs">Click to list these requests</p>
                </Show>
              </div>
            )}
          </Show>
        </div>
        <div class="flex gap-[3px] font-mono text-[0.62rem] text-ink-3">
          <For each={props.history.slots} keyed={(slot) => slot.start}>
            {(slot, index) => (
              <span
                class={[
                  'min-w-0 flex-1 overflow-visible whitespace-nowrap text-center',
                  { 'max-sm:invisible': index() % (labelEvery() * 2) !== 0 },
                ]}>
                {index() % labelEvery() === 0 ? slotLabel(slot().start, props.history.slot) : ''}
              </span>
            )}
          </For>
        </div>
      </div>
    </div>
  );
}
