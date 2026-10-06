// Request components shared by the request page and the viewer of the admin panel.

import type { JSX } from '@solidjs/web';
import { createSignal, For, Show } from 'solid-js';
import type { FailureDetails as FailureDetailsData, Request } from '../lib/api';
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
import {
  Button,
  Chip,
  Meter,
  RATING_TEXT,
  RatingBadge,
  Spinner,
  State,
  Timeline,
  TimelineDot,
  TimelineItem,
} from './ui';

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
          {rating() === 'failed' ? 'Failed in' : 'Decided by'} <Classifier id={props.request.classifier ?? ''} />
        </p>
      </Show>
      <Show when={props.request.error}>{(error) => <FailureDetails details={error()} />}</Show>
    </Card>
  );
}

export function ChainCard(props: { request: Request }) {
  return (
    <Show when={props.request.steps.length > 0}>
      <Card title="Classifier chain">
        <ClassifierTimeline request={props.request} />
      </Card>
    </Show>
  );
}

/** The classifiers a request went through, in order. An NSFW verdict escalates to the next classifier. */
export function ClassifierTimeline(props: { request: Request }) {
  // A verdict comes from the last step of its classifier with a rating, a failure from the last step
  const decided = () =>
    props.request.successful
      ? props.request.steps.findLastIndex((step) => step.rating && step.classifier === props.request.classifier)
      : props.request.steps.length - 1;
  return (
    <Timeline>
      <For each={props.request.steps}>
        {(step, index) => {
          const rating = () => stepBucket(step);
          const next = () => props.request.steps[index() + 1];
          return (
            <TimelineItem marker={<TimelineDot rating={rating()} emphasized={index() === decided()} />}>
              <div class="flex min-h-6 items-center justify-between gap-3">
                <span class="flex min-w-0 items-center gap-2">
                  <Classifier id={step.classifier} class="truncate text-sm" />
                  <Show when={index() === decided()}>
                    <Chip>final</Chip>
                  </Show>
                </span>
                <span class="shrink-0 font-mono text-ink-3 text-xs">{duration(step.durationMillis)}</span>
              </div>
              <div class="flex items-center gap-3 text-sm">
                <RatingBadge rating={rating()} label={step.rating ?? 'Error'} />
                <Show when={step.confidence != null}>
                  <Meter value={step.confidence ?? 0} rating={rating()} class="max-w-40 flex-1" />
                  <b class="font-mono font-normal text-ink">{percent(step.confidence)}</b>
                </Show>
              </div>
              <Show when={next() && step.rating === 'NSFW'}>
                <p class="text-ink-3 text-xs">
                  Flagged, sent to {classifierLabel(next()?.classifier ?? '')} to confirm.
                </p>
              </Show>
              <Show when={step.rating == null}>
                <p class="text-ink-3 text-xs">
                  {index() === decided()
                    ? 'Failed, the request has no verdict.'
                    : 'Failed, the previous verdict stands.'}
                </p>
              </Show>
              <Show when={step.error}>{(error) => <FailureDetails details={error()} />}</Show>
            </TimelineItem>
          );
        }}
      </For>
    </Timeline>
  );
}

/** The exception of a failure, which expands into its stack trace. Only the administrators receive it. */
function FailureDetails(props: { details: FailureDetailsData }) {
  return (
    <details class="text-sm">
      <summary class="cursor-pointer text-ink-3 text-xs marker:text-ink-3">
        <code class="wrap-anywhere text-danger">{props.details.type}</code>
      </summary>
      <pre class="mt-2 max-h-80 overflow-y-auto whitespace-pre-wrap wrap-anywhere rounded-md border border-line bg-surface-2 p-3 font-mono text-ink-2 text-xs">
        {props.details.stackTrace}
      </pre>
    </details>
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
