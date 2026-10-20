// The requests per client version, as one bar per version, from GET /api/stats/versions. A click on a version lists
// its requests.

import { createMemo, For, Show } from 'solid-js';
import type { VersionFilter, Versions } from '../lib/api';
import { number, share } from '../lib/format';

export function VersionChart(props: { versions: Versions; onSelect?: (filter: VersionFilter) => void }) {
  // The clients that send no version, like the scripts calling the API by hand
  const unknown = createMemo(
    () => props.versions.total - props.versions.versions.reduce((sum, version) => sum + version.count, 0),
  );

  return (
    <Show
      when={props.versions.total > 0}
      fallback={<p class="py-8 text-center text-ink-3">No requests in this range.</p>}>
      <ul class="flex max-h-80 flex-col overflow-y-auto text-sm">
        <For each={props.versions.versions} keyed={(version) => version.name}>
          {(version) => (
            <VersionRow
              label={version().name}
              count={version().count}
              total={props.versions.total}
              onSelect={props.onSelect && (() => props.onSelect?.({ value: version().name, label: version().name }))}
            />
          )}
        </For>
        <Show when={unknown() > 0}>
          <VersionRow
            label="Unknown"
            mono={false}
            count={unknown()}
            total={props.versions.total}
            class="mt-1 border-line border-t pt-1 text-ink-3"
            fill="bg-line-strong"
            onSelect={props.onSelect && (() => props.onSelect?.({ value: '', label: 'Unknown version' }))}
          />
        </Show>
      </ul>
    </Show>
  );
}

function VersionRow(props: {
  label: string;
  count: number;
  total: number;
  mono?: boolean;
  class?: string;
  fill?: string;
  /** Lists the requests of the version. Without it, the row is not clickable. */
  onSelect?: () => void;
}) {
  return (
    <li class={props.class}>
      <button
        type="button"
        disabled={!props.onSelect}
        class="flex w-full flex-col gap-1.5 rounded-lg px-2 py-1.5 text-left transition enabled:cursor-pointer enabled:hover:bg-surface-2"
        onClick={() => props.onSelect?.()}>
        <div class="flex w-full items-center gap-3">
          <span
            class={['min-w-0 flex-1 truncate', { 'font-mono text-[0.84em]': props.mono !== false }]}
            data-tooltip={props.label}
            data-tooltip-overflow>
            {props.label}
          </span>
          <span class="font-mono text-ink-2 text-xs tabular-nums">{number(props.count)}</span>
          <span class="w-12 text-right font-mono text-ink-3 text-xs tabular-nums">
            {share(props.count, props.total)}
          </span>
        </div>
        <div class="h-1.5 w-full overflow-hidden rounded-full bg-surface-2">
          <div
            class={['h-full min-w-1.5 rounded-full', props.fill ?? 'bg-ink-3']}
            style={{ width: `${(props.count / props.total) * 100}%` }}
          />
        </div>
      </button>
    </li>
  );
}
