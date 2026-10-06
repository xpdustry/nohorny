// Request components shared by the request page and the viewer of the admin panel.

import type { JSX } from '@solidjs/web';
import { createSignal, For, Show } from 'solid-js';
import type { Request } from '../lib/api';
import {
  bucket,
  classifierLabel,
  clientLabel,
  duration,
  formatTime,
  isServer,
  percent,
  stepBucket,
} from '../lib/format';
import { EyeOffIcon, ImageOffIcon } from './icon';
import { Button, Chip, Meter, RATING_TEXT, RatingBadge, Spinner, State } from './ui';

const PLACEHOLDERS = {
  none: 'No image kept',
  stored: 'Image unavailable',
  expired: 'Image expired after the retention period',
  purged: 'Image removed',
};

/** The image of a request. Flagged images stay blurred until `revealed`. */
export function RequestImage(props: {
  request: Request;
  revealed: boolean;
  onReveal: (revealed: boolean) => void;
  class?: string;
}) {
  // Keyed by URL, because the admin viewer reuses this component across requests
  const [broken, setBroken] = createSignal<string>();
  const rating = () => bucket(props.request);
  const sensitive = () => rating() !== 'safe';
  const blurred = () => sensitive() && !props.revealed;
  const url = () => {
    const url = props.request.image.state === 'stored' ? props.request.image.url : undefined;
    return url === broken() ? undefined : url;
  };

  return (
    <div
      class={[
        'relative grid place-items-center overflow-hidden rounded-2xl border border-line bg-bg-2',
        props.class ?? 'aspect-square',
      ]}>
      <Show
        when={url()}
        fallback={
          <p class="flex flex-col items-center gap-2 p-6 text-center text-ink-3">
            <ImageOffIcon class="size-6" />
            {PLACEHOLDERS[props.request.image.state]}
          </p>
        }>
        {(src) => (
          <>
            <img
              src={src()}
              alt={`Rendered build submitted for classification, rated ${rating()}`}
              class={['veil size-full object-contain [image-rendering:pixelated]', { 'blur-veil': blurred() }]}
              onError={() => setBroken(src())}
            />
            <Show
              when={blurred()}
              fallback={
                <Show when={sensitive()}>
                  <Button
                    size="sm"
                    class="absolute top-3 right-3 bg-surface hover:bg-surface-2"
                    onClick={() => props.onReveal(false)}>
                    <EyeOffIcon />
                    Hide
                  </Button>
                </Show>
              }>
              <button
                type="button"
                class="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-bg/30 text-ink"
                onClick={() => props.onReveal(true)}>
                <span class="grid size-10 place-items-center rounded-full border border-line bg-surface">
                  <EyeOffIcon class="size-5" />
                </span>
                <b class="font-normal text-lg">
                  {rating() === 'failed' ? 'Not classified' : `Flagged as ${props.request.rating}`}
                </b>
                <span class="label text-ink-2">Click to reveal</span>
              </button>
            </Show>
          </>
        )}
      </Show>
    </div>
  );
}

export function Card(props: { title: string; children: JSX.Element; class?: string }) {
  return (
    <section class={['card flex flex-col gap-4 p-6', props.class]}>
      <h2 class="label">{props.title}</h2>
      {props.children}
    </section>
  );
}

export function VerdictCard(props: { request: Request }) {
  const rating = () => bucket(props.request);
  return (
    <Card title="Verdict">
      <p class={['text-6xl', RATING_TEXT[rating()]]}>{rating() === 'failed' ? 'FAILED' : props.request.rating}</p>
      <Show when={props.request.confidence != null}>
        <div class="flex items-center gap-3 text-sm">
          <Meter value={props.request.confidence ?? 0} rating={rating()} class="flex-1" />
          <span class="text-ink-2">
            NSFW score <b class="font-mono font-normal text-ink">{percent(props.request.confidence)}</b>
          </span>
        </div>
      </Show>
      <Show when={props.request.classifier}>
        <p class="text-ink-2 text-sm">
          Decided by <Classifier id={props.request.classifier ?? ''} />
        </p>
      </Show>
      <Show when={props.request.error}>
        <p class="text-danger text-sm">Classification failed: {props.request.error}</p>
        <StackTrace trace={props.request.stackTrace} />
      </Show>
    </Card>
  );
}

export function ChainCard(props: { request: Request }) {
  return (
    <Show when={props.request.steps.length > 0}>
      <Card title="Classifier chain">
        <ol class="flex flex-col gap-4">
          <For each={props.request.steps}>
            {(step) => (
              <li class="flex flex-col gap-1.5">
                <Classifier id={step.classifier} class="self-start text-sm" />
                <span class="flex flex-wrap items-center gap-3 text-ink-2 text-sm">
                  <RatingBadge rating={stepBucket(step)} label={step.rating ?? 'Error'} />
                  <Show when={step.confidence != null}>
                    <Meter value={step.confidence ?? 0} rating={stepBucket(step)} class="w-20" />
                    <b class="font-mono font-normal text-ink">{percent(step.confidence)}</b>
                  </Show>
                  <span>{duration(step.durationMillis)}</span>
                </span>
                <Show when={step.error}>
                  <span class="text-danger text-sm">{step.error}</span>
                  <StackTrace trace={step.stackTrace} />
                </Show>
              </li>
            )}
          </For>
        </ol>
      </Card>
    </Show>
  );
}

/** The collapsed stack trace of a failure, when the server sent it. */
function StackTrace(props: { trace: string | null | undefined }) {
  return (
    <Show when={props.trace}>
      <details class="text-sm">
        <summary class="cursor-pointer text-ink-2">Stack trace</summary>
        <pre class="mt-2 max-h-80 overflow-auto rounded-md border border-line bg-surface-2 p-3 font-mono text-ink-2 text-xs">
          {props.trace}
        </pre>
      </details>
    </Show>
  );
}

/** The short name of a classifier, the full identifier with the model repository and revision in its tooltip. */
function Classifier(props: { id: string; class?: string }) {
  const label = () => classifierLabel(props.id);
  return (
    <code
      class={[
        'text-ink',
        { 'cursor-help underline decoration-dotted underline-offset-4': label() !== props.id },
        props.class,
      ]}
      title={label() !== props.id ? props.id : undefined}>
      {label()}
    </code>
  );
}

export function DetailsCard(props: { request: Request }) {
  const rows = (): [string, JSX.Element][] => [
    [
      'Created',
      <time datetime={props.request.createdAt} title={props.request.createdAt}>
        {formatTime(props.request.createdAt)}
      </time>,
    ],
    ['Duration', duration(props.request.durationMillis)],
    [
      'Client',
      <span class="flex flex-wrap items-center gap-2">
        <Show when={isServer(props.request.client)}>
          <Chip>Server</Chip>
        </Show>
        <span class="break-all">{clientLabel(props.request.client)}</span>
      </span>,
    ],
    ['Plugin', <code>{props.request.version ? `v${props.request.version}` : 'unknown'}</code>],
    ['Request ID', <code class="break-all">{props.request.id}</code>],
  ];
  return (
    <Card title="Details">
      <KeyValues rows={rows()} />
    </Card>
  );
}

export function KeyValues(props: { rows: [string, JSX.Element][] }) {
  return (
    <dl class="divide-y divide-line">
      <For each={props.rows} keyed={([key]) => key}>
        {(row) => (
          <div class="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-4 py-2.5 first:pt-0 last:pb-0 sm:grid-cols-[8rem_minmax(0,1fr)]">
            <dt class="text-ink-3">{row()[0]}</dt>
            <dd>{row()[1]}</dd>
          </div>
        )}
      </For>
    </dl>
  );
}

/** Shown while a request loads. The prerendered shell of the request pages contains it. */
export function RequestLoading() {
  return (
    <State icon={<Spinner class="size-5" />} title="Loading request…">
      <span class="sr-only" role="status">
        Loading request
      </span>
    </State>
  );
}
