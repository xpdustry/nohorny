// Components shared by the pages.

import type { ComponentProps, JSX } from '@solidjs/web';
import { createUniqueId, omit, Show } from 'solid-js';
import logo from '../assets/logo.svg';
import type { Bucket } from '../lib/api';
import { percent, ratingLabel } from '../lib/format';
import type { Connection } from '../lib/stats';
import { CheckIcon, TriangleAlertIcon } from './icon';

type Variant = 'primary' | 'default' | 'danger' | 'solid-danger' | 'quiet' | 'quiet-danger';
type Size = 'md' | 'sm' | 'icon';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-accent text-on-accent hover:bg-accent-hover',
  default:
    'border border-line-strong text-ink hover:bg-surface-2 aria-pressed:border-accent-edge aria-pressed:bg-accent-soft aria-pressed:text-on-accent-soft',
  danger: 'border border-danger/60 text-danger hover:bg-danger-soft',
  'solid-danger': 'bg-danger text-on-danger hover:bg-danger-hover',
  quiet: 'text-ink-2 hover:bg-surface-2 hover:text-ink',
  // A destructive action repeated on every row, which only turns red under the pointer
  'quiet-danger': 'text-ink-2 hover:bg-danger-soft hover:text-danger',
};

// Touch screens get taller targets
const SIZES: Record<Size, string> = {
  md: 'h-10 gap-2 px-4 text-[0.95rem] pointer-coarse:h-11',
  sm: 'h-8 gap-1.5 px-3 text-sm pointer-coarse:h-10',
  icon: 'size-9 justify-center pointer-coarse:size-11',
};

/** The classes of a button, for links that look like one. */
export function buttonClass(variant: Variant = 'default', size: Size = 'md'): string[] {
  return [
    'inline-flex shrink-0 select-none items-center whitespace-nowrap rounded-full no-underline [&_svg]:size-4',
    'transition-[color,background-color,border-color,scale] duration-150 ease-out motion-safe:active:scale-[0.97]',
    'disabled:pointer-events-none disabled:opacity-50',
    VARIANTS[variant],
    SIZES[size],
  ];
}

export function Button(props: ComponentProps<'button'> & { variant?: Variant; size?: Size; busy?: boolean }) {
  const rest = omit(props, 'variant', 'size', 'busy', 'class', 'children', 'disabled', 'type');
  return (
    <button
      type={props.type ?? 'button'}
      class={[...buttonClass(props.variant, props.size), props.class]}
      disabled={props.disabled || props.busy}
      aria-busy={props.busy ? 'true' : undefined}
      {...rest}>
      <Show when={props.busy}>
        <Spinner />
      </Show>
      {props.children}
    </button>
  );
}

/** A labelled text input. The hint shows until the field has an error, and both describe the input. */
export function Field(props: ComponentProps<'input'> & { label: string; hint?: string; error?: string | null }) {
  const rest = omit(props, 'label', 'hint', 'error', 'class');
  const id = createUniqueId();
  const note = () => props.error || props.hint;
  return (
    <div class="flex flex-col gap-1.5">
      <label for={id} class="text-ink-2 text-sm">
        {props.label}
      </label>
      <input
        id={id}
        class={['field', props.class]}
        aria-invalid={props.error ? 'true' : undefined}
        aria-describedby={note() ? `${id}-note` : undefined}
        {...rest}
      />
      <Show when={note()}>
        <p id={`${id}-note`} class={['text-xs', props.error ? 'text-danger' : 'text-ink-3']}>
          {note()}
        </p>
      </Show>
    </div>
  );
}

export function Spinner(props: { class?: string }) {
  return (
    <span
      aria-hidden="true"
      class={[
        'inline-block shrink-0 motion-safe:animate-spin rounded-full border-2 border-current border-r-transparent',
        props.class ?? 'size-4',
      ]}
    />
  );
}

export const RATING_TEXT: Record<Bucket, string> = {
  safe: 'text-safe',
  warn: 'text-warn',
  nsfw: 'text-nsfw',
  failed: 'text-failed',
};

export const RATING_FILL: Record<Bucket, string> = {
  safe: 'bg-fill-safe',
  warn: 'bg-fill-warn',
  nsfw: 'bg-fill-nsfw',
  failed: 'bg-fill-failed',
};

/** The dot of a rating, hollow when the rating is hidden. */
export function RatingDot(props: { rating: Bucket; hollow?: boolean }) {
  return (
    <span
      aria-hidden="true"
      class={[
        'inline-block size-2 shrink-0 rounded-full',
        props.hollow ? ['border-[1.5px] border-current', RATING_TEXT[props.rating]] : RATING_FILL[props.rating],
      ]}
    />
  );
}

export function RatingBadge(props: { rating: Bucket; label?: string }) {
  return (
    <span
      class={[
        'inline-flex items-center gap-1.5 rounded-full border border-line bg-surface-2 px-2 py-0.5 font-mono text-[0.68rem] uppercase tracking-wider',
        RATING_TEXT[props.rating],
      ]}>
      <RatingDot rating={props.rating} />
      {props.label ?? ratingLabel(props.rating)}
    </span>
  );
}

/** Horizontal gauge of a 0..1 NSFW score. */
export function Meter(props: { value: number; rating: Bucket; class?: string }) {
  return (
    <span
      role="img"
      aria-label={`NSFW score ${percent(props.value)}`}
      class={['relative block h-1.5 overflow-hidden rounded-full bg-surface-3', props.class ?? 'w-24']}>
      <span
        class={['absolute inset-y-0 left-0 min-w-1.5 rounded-full', RATING_FILL[props.rating]]}
        style={{ width: `${(props.value * 100).toFixed(1)}%` }}
      />
    </span>
  );
}

/** A vertical sequence of events joined by a line, filled with `TimelineItem`s. */
export function Timeline(props: { children: JSX.Element; class?: string }) {
  return <ol class={['flex flex-col', props.class]}>{props.children}</ol>;
}

/** An event of a `Timeline`. The marker sits on the line, level with the first row of the content. */
export function TimelineItem(props: { marker: JSX.Element; children: JSX.Element }) {
  return (
    <li class="group relative flex gap-3 pb-5 last:pb-0">
      <span
        aria-hidden="true"
        class="absolute top-7 bottom-1 left-3 w-0.5 -translate-x-1/2 rounded-full bg-accent group-last:hidden"
      />
      <span class="flex size-6 shrink-0 items-center justify-center">{props.marker}</span>
      <div class="flex min-w-0 flex-1 flex-col gap-2">{props.children}</div>
    </li>
  );
}

/** The marker of a rated event in a `Timeline`, haloed when `emphasized`. */
export function TimelineDot(props: { rating: Bucket; emphasized?: boolean }) {
  return (
    <span
      aria-hidden="true"
      class={[
        'block rounded-full',
        props.emphasized ? ['size-3 ring-4', RATING_FILL[props.rating], RATING_RING[props.rating]] : 'size-2.5',
        !props.emphasized && (props.rating === 'failed' ? 'border-2 border-fill-failed' : RATING_FILL[props.rating]),
      ]}
    />
  );
}

const RATING_RING: Record<Bucket, string> = {
  safe: 'ring-fill-safe/25',
  warn: 'ring-fill-warn/25',
  nsfw: 'ring-fill-nsfw/25',
  failed: 'ring-fill-failed/25',
};

export function Chip(props: { children: JSX.Element; class?: string; title?: string }) {
  return (
    <span
      title={props.title}
      class={[
        'inline-flex items-center rounded-full border border-line bg-surface-2 px-2.5 py-0.5 font-mono text-[0.7rem] text-ink-2',
        props.class,
      ]}>
      {props.children}
    </span>
  );
}

const CONNECTION_DOT: Record<Connection, string> = {
  live: 'bg-accent motion-safe:animate-pulse',
  connecting: 'bg-warn',
  polling: 'bg-ink-3',
  offline: 'bg-danger',
};

export function LiveIndicator(props: { connection: Connection; label: string }) {
  return (
    <span class="inline-flex items-center gap-2 font-mono text-[0.68rem] uppercase tracking-[0.14em] text-ink-3">
      <span aria-hidden="true" class={['size-2 rounded-full', CONNECTION_DOT[props.connection]]} />
      {props.label}
    </span>
  );
}

export function Brand(props: { tag?: string }) {
  return (
    <a href="/" class="inline-flex items-center gap-2.5 text-lg text-ink no-underline">
      <img src={logo} alt="" width="34" height="21" />
      <span>NoHorny</span>
      <Show when={props.tag}>
        <span class="rounded-full border border-line-strong px-2 py-px font-mono text-[0.6rem] text-ink-2 uppercase tracking-widest">
          {props.tag}
        </span>
      </Show>
    </a>
  );
}

/** Centered message for the loading, empty and error states of a page or a panel. */
export function State(props: { icon?: JSX.Element; title: string; children?: JSX.Element; tone?: 'error' }) {
  return (
    <div class="flex flex-col items-center gap-3 px-6 py-16 text-center">
      {/* Reuses the icon that the condition created. Reading props.icon twice creates a second element,
          and the second element breaks hydration. */}
      <Show when={props.icon}>
        {(icon) => (
          <span
            class={[
              'grid size-12 place-items-center rounded-full border border-line bg-surface-2 [&_svg]:size-5',
              props.tone === 'error' ? 'bg-danger-soft text-danger' : 'text-ink-2',
            ]}>
            {icon()}
          </span>
        )}
      </Show>
      <h2 class="text-xl">{props.title}</h2>
      {props.children}
    </div>
  );
}

/** The outcome of an action. Its icon tells a success from an error, the text stays in the ink colour. */
export function Notice(props: { tone: 'success' | 'error'; children: JSX.Element; onDismiss?: () => void }) {
  return (
    <div
      role={props.tone === 'error' ? 'alert' : 'status'}
      class={[
        'flex items-center gap-3 rounded-xl border bg-surface px-4 py-2 text-ink text-sm',
        props.tone === 'error' ? 'border-danger/60' : 'border-line-strong',
      ]}>
      {props.tone === 'error' ? (
        <TriangleAlertIcon class="size-4 shrink-0 text-danger" />
      ) : (
        <CheckIcon class="size-4 shrink-0 text-safe" />
      )}
      <p class="flex-1">{props.children}</p>
      <Show when={props.onDismiss}>
        <Button size="sm" variant="quiet" onClick={() => props.onDismiss?.()}>
          Dismiss
        </Button>
      </Show>
    </div>
  );
}
