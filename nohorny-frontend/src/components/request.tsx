// Request components shared by the request page and the viewer of the admin panel.

import type { JSX } from '@solidjs/web';
import { createSignal, For, Show } from 'solid-js';
import type {
  FailureDetails as FailureDetailsData,
  Request,
  Requester as RequesterData,
  RequesterType,
} from '../lib/api';
import { bucket, classifierInfo, duration, formatTime, percent, shortId, stepBucket } from '../lib/format';
import { EXTERNAL } from '../lib/links';
import { confirm, Dialog } from './dialog';
import {
  CheckIcon,
  CopyIcon,
  EyeOffIcon,
  HatGlassesIcon,
  HouseIcon,
  ImageOffIcon,
  ServerIcon,
  UserIcon,
  XIcon,
} from './icon';
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
  deleted: 'Image deleted',
};

// The confirmations are shared by the request page and the admin panel

/** Asks to confirm the deletion of the image of a request. */
export function confirmImageDeletion(request: Request): Promise<boolean> {
  return confirm({
    title: `Delete the image of ${shortId(request.id)}?`,
    body: 'The image is deleted immediately. This cannot be undone.',
    action: 'Delete image',
  });
}

/** Asks to confirm the deletion of a request. */
export function confirmRequestDeletion(request: Request): Promise<boolean> {
  return confirm({
    title: `Delete request ${shortId(request.id)}?`,
    body: 'The request, its verdict and its image are removed for good and its link stops working. Statistics are not affected.',
    action: 'Delete request',
  });
}

/** The image of a request. Flagged images stay blurred until `revealed`, a click toggles them. */
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
              class={[
                'veil absolute inset-0 size-full object-contain [image-rendering:pixelated]',
                { 'blur-veil': blurred() },
              ]}
              onError={() => setBroken(src())}
            />
            <Show
              when={blurred()}
              fallback={
                <Show when={sensitive()}>
                  {/* The whole image blurs it back, with a hint on hover */}
                  <button
                    type="button"
                    aria-label="Blur the image"
                    class="group absolute inset-0 flex cursor-pointer items-end justify-center p-3"
                    onClick={() => props.onReveal(false)}>
                    <span class="flex items-center gap-1.5 rounded-full border border-line bg-surface/90 px-3 py-1 text-ink-2 text-sm opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100 [&_svg]:size-4">
                      <EyeOffIcon />
                      Click to blur
                    </span>
                  </button>
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
      {/* The classifier chain names the deciding step, this only shows for a request recorded without its steps */}
      <Show when={props.request.steps.length === 0 && props.request.classifier}>
        <p class="flex min-w-0 flex-wrap items-baseline gap-x-1.5 text-ink-2 text-sm">
          {rating() === 'failed' ? 'Failed in' : 'Decided by'} <Classifier id={props.request.classifier ?? ''} />
          <Show when={props.request.error}>
            {(error) => (
              <>
                with <FailureDetails details={error()} />
              </>
            )}
          </Show>
        </p>
      </Show>
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
  // The verdict comes from the last step of its classifier with a rating, a failed request has none
  const decided = () =>
    props.request.successful
      ? props.request.steps.findLastIndex((step) => step.rating && step.classifier === props.request.classifier)
      : -1;
  return (
    <Timeline>
      <For each={props.request.steps}>
        {(step, index) => {
          const rating = () => stepBucket(step);
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
              <div class="flex min-w-0 items-center gap-3 text-sm">
                <RatingBadge rating={rating()} label={step.rating ?? 'Error'} />
                <Show when={step.confidence != null}>
                  <Meter value={step.confidence ?? 0} rating={rating()} class="max-w-40 flex-1" />
                  <b class="font-mono font-normal text-ink">{percent(step.confidence)}</b>
                </Show>
                <Show when={step.error}>{(error) => <FailureDetails details={error()} />}</Show>
              </div>
            </TimelineItem>
          );
        }}
      </For>
    </Timeline>
  );
}

/** Copies `text` to the clipboard, `copied` stays true for 2 seconds. Without clipboard access, selects `fallback` for a manual copy. */
function createCopy(text: () => string, fallback: () => Node) {
  const [copied, setCopied] = createSignal(false);
  let reset: ReturnType<typeof setTimeout> | undefined;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text());
      setCopied(true);
      clearTimeout(reset);
      reset = setTimeout(() => setCopied(false), 2000);
    } catch {
      getSelection()?.selectAllChildren(fallback());
    }
  };
  return [copied, copy] as const;
}

/** Code copied to the clipboard on click. */
function CopyCode(props: { text: string; label: string }) {
  let code!: HTMLElement;
  const [copied, copy] = createCopy(
    () => props.text,
    () => code,
  );
  return (
    <button
      type="button"
      data-tooltip={copied() ? 'Copied' : `Copy the ${props.label}`}
      class="group inline-flex cursor-pointer items-start gap-2 text-left"
      onClick={copy}>
      <code ref={code} class="break-all">
        {props.text}
      </code>
      <span class={['mt-1 shrink-0 [&_svg]:size-3.5', copied() ? 'text-safe' : 'text-ink-3 group-hover:text-ink']}>
        {copied() ? <CheckIcon /> : <CopyIcon />}
      </span>
    </button>
  );
}

/** The exception of a failure, which opens its stack trace. Only the administrators receive it. */
function FailureDetails(props: { details: FailureDetailsData }) {
  const [open, setOpen] = createSignal(false);
  let trace!: HTMLPreElement;
  const [copied, copy] = createCopy(
    () => props.details.stackTrace,
    () => trace,
  );
  return (
    <>
      <button
        type="button"
        data-tooltip="Show the stack trace"
        class="min-w-0 cursor-pointer truncate font-mono text-danger text-xs underline decoration-dotted underline-offset-2 hover:decoration-solid"
        onClick={() => setOpen(true)}>
        {props.details.type}
      </button>
      <Dialog
        open={open()}
        onClose={() => setOpen(false)}
        label="Stack trace"
        class="w-[min(64rem,calc(100vw-2rem))]"
        // Keeps the arrow keys from paging the request viewer of the admin panel underneath
        onKeyDown={(event) => event.stopPropagation()}>
        <div class="flex flex-col gap-4 p-6">
          <header class="flex items-start gap-2">
            <div class="flex min-w-0 flex-1 flex-col gap-1">
              <h2 class="text-xl">Stack trace</h2>
              <code class="wrap-anywhere text-danger text-sm">{props.details.type}</code>
            </div>
            <Button size="sm" onClick={copy}>
              {copied() ? <CheckIcon /> : <CopyIcon />}
              {copied() ? 'Copied' : 'Copy'}
            </Button>
            <Button size="icon" variant="quiet" aria-label="Close" class="-mt-1" onClick={() => setOpen(false)}>
              <XIcon />
            </Button>
          </header>
          <pre
            ref={trace}
            class="max-h-[70dvh] overflow-auto rounded-lg border border-line bg-bg-2 p-4 font-mono text-ink-2 text-xs leading-relaxed">
            {props.details.stackTrace}
          </pre>
        </div>
      </Dialog>
    </>
  );
}

/** The short name of a classifier, its model in the tooltip, linking to the page of the model. */
function Classifier(props: { id: string; class?: string }) {
  const info = () => classifierInfo(props.id);
  return (
    <Show
      when={info().href}
      fallback={
        <code
          class={[
            'text-ink',
            { 'cursor-help underline decoration-dotted underline-offset-4': info().details !== undefined },
            props.class,
          ]}
          data-tooltip={info().details}>
          {info().label}
        </code>
      }>
      {(href) => (
        <a
          class={['link-quiet font-mono text-[0.84em]', props.class]}
          href={href()}
          data-tooltip={info().details}
          {...EXTERNAL}>
          {info().label}
        </a>
      )}
    </Show>
  );
}

/** The icon, the kind and the fallback name of each requester type. */
const REQUESTERS: Record<
  RequesterType,
  { icon: (props: { class?: string }) => JSX.Element; kind: string; unnamed: string }
> = {
  'mindustry-network': { icon: ServerIcon, kind: 'Mindustry network', unnamed: 'Unnamed server' },
  user: { icon: UserIcon, kind: 'Account', unnamed: 'Account' },
  local: { icon: HouseIcon, kind: 'Local address', unnamed: 'Local' },
  anonymous: { icon: HatGlassesIcon, kind: 'Anonymous client', unnamed: 'Anonymous' },
};

/** Who sent a request: an icon for its type, named in the tooltip and for screen readers, then its name. */
export function Requester(props: { requester: RequesterData; class?: string }) {
  const info = () => REQUESTERS[props.requester.type];
  return (
    <span class={['inline-flex min-w-0 items-center gap-1.5', props.class]}>
      <span class="shrink-0 text-ink-3 [&_svg]:size-3.5" data-tooltip={info().kind}>
        {info().icon({})}
      </span>
      <span class="sr-only">{info().kind}</span>
      <span class="truncate" data-tooltip={props.requester.name ?? undefined} data-tooltip-overflow>
        {props.requester.name ?? info().unnamed}
      </span>
    </span>
  );
}

export function DetailsCard(props: { request: Request }) {
  const rows = (): [string, JSX.Element][] => [
    [
      'Created',
      <time datetime={props.request.createdAt} data-tooltip={props.request.createdAt}>
        {formatTime(props.request.createdAt)}
      </time>,
    ],
    ['Duration', duration(props.request.durationMillis)],
    ['Requester', <Requester requester={props.request.requester} />],
    ['Plugin', <code>{props.request.version ? `v${props.request.version}` : 'unknown'}</code>],
    ['Request ID', <CopyCode text={props.request.id} label="request ID" />],
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

/** Shown while a request loads, after a moment. The prerendered shell of the request pages contains it. */
export function RequestLoading() {
  return (
    <div class="pending">
      <State icon={<Spinner class="size-5" />} title="Loading request…">
        <span class="sr-only" role="status">
          Loading request
        </span>
      </State>
    </div>
  );
}
