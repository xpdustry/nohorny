import type { JSX } from '@solidjs/web';
import { createSignal, For, Show } from 'solid-js';
import {
  ART_KINDS,
  type FailureDetails,
  type Request,
  type Requester as RequesterData,
  type RequesterType,
  type Step,
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
import { Button, Meter, RATING_TEXT, Spinner, State, Timeline, TimelineDot, TimelineItem } from './ui';

const PLACEHOLDERS = {
  none: 'No image kept',
  stored: 'Image unavailable',
  expired: 'Image expired after the retention period',
  deleted: 'Image deleted',
};

export function confirmImageDeletion(request: Request): Promise<boolean> {
  return confirm({
    title: `Delete the image of ${shortId(request.id)}?`,
    body: 'The image is deleted immediately. This cannot be undone.',
    action: 'Delete image',
  });
}

export function confirmRequestDeletion(request: Request): Promise<boolean> {
  return confirm({
    title: `Delete request ${shortId(request.id)}?`,
    body: 'The request, its verdict and its image are removed for good and its link stops working. Statistics are not affected.',
    action: 'Delete request',
  });
}

export function RequestImage(props: {
  request: Request;
  revealed: boolean;
  onReveal: (revealed: boolean) => void;
  class?: string;
}) {
  // Keyed by URL, because the admin viewer reuses this component across requests
  const [brokenUrl, setBrokenUrl] = createSignal<string>();
  const rating = () => bucket(props.request);
  const sensitive = () => rating() !== 'safe';
  const blurred = () => sensitive() && !props.revealed;
  const url = () => {
    const url = props.request.image.state === 'stored' ? props.request.image.url : undefined;
    return url === brokenUrl() ? undefined : url;
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
              onError={() => setBrokenUrl(src())}
            />
            <Show
              when={blurred()}
              fallback={
                <Show when={sensitive()}>
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
                <span class="text-ink-2 text-sm">Click to reveal</span>
              </button>
            </Show>
          </>
        )}
      </Show>
    </div>
  );
}

function Card(props: { label: string; children: JSX.Element }) {
  return (
    <section aria-label={props.label} class="card flex min-w-0 flex-col gap-4 p-5 sm:p-6">
      {props.children}
    </section>
  );
}

interface Path {
  steps: Step[];
  /** The step whose verdict the request kept, or the one that failed it. */
  picked: number;
}

/**
 * The chain escalates while the verdicts are NSFW. A failing step ends it: the request keeps the verdict before it, and
 * fails without one. Older requests have no steps, the request itself stands in for its single step.
 */
function pathOf(request: Request): Path {
  if (request.steps.length === 0) {
    const step = {
      classifier: request.classifier ?? 'unknown',
      rating: request.successful ? request.rating : null,
      confidence: request.confidence,
      durationMillis: request.durationMillis,
      error: request.error ?? undefined,
    };
    return { steps: [step], picked: 0 };
  }
  const picked = request.successful
    ? request.steps.findLastIndex((step) => step.rating && step.classifier === request.classifier)
    : request.steps.length - 1;
  return { steps: request.steps, picked: Math.max(picked, 0) };
}

/** The path through the chain, its picked step grown into the verdict. */
export function ClassificationCard(props: { request: Request }) {
  const path = () => pathOf(props.request);
  const kept = () => path().picked < path().steps.length - 1;
  return (
    <Card label="Classification">
      <Timeline>
        <For each={path().steps}>
          {(step, index) => {
            const rating = () => stepBucket(step);
            const picked = () => index() === path().picked;
            return (
              <TimelineItem marker={<TimelineDot rating={rating()} emphasized={picked()} />}>
                <div class="flex min-h-6 flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                  <Classifier id={step.classifier} class="wrap-anywhere" />
                  <Show when={!picked()}>
                    <span class={RATING_TEXT[rating()]}>{step.rating ?? 'Failed'}</span>
                  </Show>
                  <span class="ms-auto flex gap-3 font-mono text-xs tabular-nums">
                    <Show when={!picked() && step.confidence != null}>
                      <span class="text-ink-2">{percent(step.confidence)}</span>
                    </Show>
                    <span class="text-ink-3">{duration(step.durationMillis)}</span>
                  </span>
                </div>
                <Show when={picked()}>
                  <Verdict request={props.request} class="pt-1 pb-2" />
                </Show>
                <Show when={step.error}>{(error) => <FailureTrace details={error()} />}</Show>
              </TimelineItem>
            );
          }}
        </For>
      </Timeline>
      <Show when={kept()}>
        <p class="ps-9 text-ink-3 text-sm">
          The chain stopped at the failure, the verdict of <Classifier id={path().steps[path().picked].classifier} />{' '}
          stands.
        </p>
      </Show>
    </Card>
  );
}

function Verdict(props: { request: Request; class?: string }) {
  const rating = () => bucket(props.request);
  return (
    <div class={['flex flex-col gap-3', props.class]}>
      <div class="flex items-end justify-between gap-4">
        <p class={['text-4xl leading-none sm:text-5xl', RATING_TEXT[rating()]]}>
          {rating() === 'failed' ? 'Failed' : props.request.rating}
        </p>
        <Show when={props.request.confidence != null}>
          <p class="font-mono text-xl tabular-nums" data-tooltip="NSFW score">
            {percent(props.request.confidence)}
          </p>
        </Show>
      </div>
      <Show when={props.request.confidence != null}>
        <Meter value={props.request.confidence ?? 0} rating={rating()} class="w-full" />
      </Show>
    </div>
  );
}

const COPIED_MILLIS = 2000;

/** Copies `text` to the clipboard, `copied` stays true for COPIED_MILLIS. Without clipboard access, selects `fallback` for a manual copy. */
function createCopy(text: () => string, fallback: () => Node) {
  const [copied, setCopied] = createSignal(false);
  let reset: ReturnType<typeof setTimeout> | undefined;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text());
      setCopied(true);
      clearTimeout(reset);
      reset = setTimeout(() => setCopied(false), COPIED_MILLIS);
    } catch {
      getSelection()?.selectAllChildren(fallback());
    }
  };
  return [copied, copy] as const;
}

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

function FailureTrace(props: { details: FailureDetails }) {
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
        class="min-w-0 max-w-full cursor-pointer self-start truncate text-left font-mono text-danger text-xs underline decoration-dotted underline-offset-2 hover:decoration-solid"
        onClick={() => setOpen(true)}>
        {props.details.type}
      </button>
      <Dialog open={open()} onClose={() => setOpen(false)} label="Stack trace" class="w-[min(64rem,calc(100vw-2rem))]">
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

function Classifier(props: { id: string; class?: string }) {
  const info = () => classifierInfo(props.id);
  // On two lines, the tooltip keeps line breaks
  const tooltip = () => {
    const model = info().model;
    return model && `${model.name}\n${model.origin}`;
  };
  return (
    <Show
      when={info().href}
      fallback={
        <code
          class={[
            'text-ink',
            { 'cursor-help underline decoration-dotted underline-offset-4': tooltip() !== undefined },
            props.class,
          ]}
          data-tooltip={tooltip()}>
          {info().kind}
        </code>
      }>
      {(href) => (
        <a
          class={['link-quiet font-mono text-[0.84em]', props.class]}
          href={href()}
          data-tooltip={tooltip()}
          {...EXTERNAL}>
          {info().kind}
        </a>
      )}
    </Show>
  );
}

const REQUESTERS: Record<
  RequesterType,
  { icon: (props: { class?: string }) => JSX.Element; kind: string; unnamed: string }
> = {
  'mindustry-network': { icon: ServerIcon, kind: 'Mindustry network', unnamed: 'Unnamed server' },
  user: { icon: UserIcon, kind: 'Account', unnamed: 'Account' },
  local: { icon: HouseIcon, kind: 'Local address', unnamed: 'Local' },
  anonymous: { icon: HatGlassesIcon, kind: 'Anonymous client', unnamed: 'Anonymous' },
};

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
    ['Type', props.request.kind ? ART_KINDS[props.request.kind] : 'unknown'],
    ['Requester', <Requester requester={props.request.requester} />],
    ['Plugin', <code>{props.request.version ? `v${props.request.version}` : 'unknown'}</code>],
    ['Request ID', <CopyCode text={props.request.id} label="request ID" />],
    ...(props.request.remoteAddress
      ? [['Address', <code class="break-all">{props.request.remoteAddress}</code>] as [string, JSX.Element]]
      : []),
  ];
  return (
    <Card label="Details">
      <KeyValues rows={rows()} />
    </Card>
  );
}

export function KeyValues(props: { rows: [string, JSX.Element][] }) {
  return (
    <dl class="divide-y divide-line">
      <For each={props.rows} keyed={([key]) => key}>
        {(row) => (
          <div class="grid grid-cols-[6rem_minmax(0,1fr)] gap-3 py-2.5 first:pt-0 last:pb-0 sm:grid-cols-[8rem_minmax(0,1fr)]">
            <dt class="text-ink-3">{row()[0]}</dt>
            <dd class="min-w-0">{row()[1]}</dd>
          </div>
        )}
      </For>
    </dl>
  );
}

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
