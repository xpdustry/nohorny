// The landing page, with the live counter.

import type { JSX } from '@solidjs/web';
import { createEffect, createSignal, For, onSettled, Show } from 'solid-js';
import {
  CheckIcon,
  DownloadIcon,
  EyeIcon,
  GavelIcon,
  GitHubIcon,
  ImageIcon,
  ScanSearchIcon,
} from '../../components/icon';
import { buttonClass, LiveIndicator, RATING_FILL, RATING_TEXT } from '../../components/ui';
import type { Bucket } from '../../lib/api';
import { number } from '../../lib/format';
import { DOWNLOAD, EXTERNAL, GITHUB, MINDUSTRY } from '../../lib/links';
import { createLiveStats } from '../../lib/stats';

/** The 8 by 6 heart drawn in the steps, in the colours of the logo: `p` for pink, `d` for deep pink, `.` for empty. */
const ART = '.pp..pp.ppppppdppppppddp.ppppdd...ppdd.....dd...';
const ART_COLORS: Record<string, string> = { '.': 'var(--surface-3)', p: 'var(--brand)', d: 'var(--brand-deep)' };

const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** The words the headline cycles through, the first one is the prerendered and spoken one. */
const HERO_WORDS = ['horny', 'porn', 'NSFW', 'hentai'] as const;
const WORD_MILLIS = 2200;

/**
 * Cycles through the words with a short slide in. Screen readers only hear the first word, so the heading is not
 * announced again on every change. The first word stays when the user prefers reduced motion.
 */
function RotatingWord(props: { words: readonly string[] }) {
  const [index, setIndex] = createSignal(0);
  onSettled(() => {
    if (reducedMotion()) return;
    const timer = setInterval(() => setIndex((current) => (current + 1) % props.words.length), WORD_MILLIS);
    return () => clearInterval(timer);
  });
  return (
    <>
      <span class="sr-only">{props.words[0]}</span>
      {/* A new element per word, so the slide in plays on every change */}
      <For each={[props.words[index()]]}>
        {(word) => (
          <em aria-hidden="true" class="word-in inline-block text-accent-ink not-italic">
            {word}
          </em>
        )}
      </For>
    </>
  );
}

/** Animates the shown value to `target()`, or jumps to it when the user prefers reduced motion. */
function createCountUp(target: () => number | undefined) {
  const [value, setValue] = createSignal<number | null>(null);
  // The value on screen. The signal updates only after the next flush
  let shown: number | null = null;
  let frame = 0;
  const show = (next: number) => {
    shown = next;
    setValue(next);
  };
  createEffect(target, (to) => {
    if (to === undefined) return;
    cancelAnimationFrame(frame);
    const from = shown ?? 0;
    if (from === to || reducedMotion()) return show(to);
    const length = shown === null ? 1400 : 700;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / length);
      show(Math.round(from + (to - from) * (1 - (1 - progress) ** 3)));
      if (progress < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
  });
  onSettled(() => () => cancelAnimationFrame(frame));
  return value;
}

export default function Landing() {
  const stats = createLiveStats(15);
  const total = createCountUp(() => stats.data()?.total);
  const processed = () => stats.data()?.last24Hours.total ?? null;
  const flagged = () => {
    const ratings = stats.data()?.last24Hours.ratings;
    return ratings ? ratings.warn + ratings.nsfw : null;
  };

  // Announces the total at most every 30 seconds so screen readers are not flooded
  const [spoken, setSpoken] = createSignal('');
  let spokenAt = 0;
  createEffect(
    () => stats.data()?.total,
    (value) => {
      if (value === undefined || Date.now() - spokenAt < 30000) return;
      spokenAt = Date.now();
      setSpoken(`${number(value)} images classified`);
    },
  );

  return (
    <>
      <section
        class="wrap grid items-center gap-12 pt-16 pb-24 lg:grid-cols-[1.1fr_1fr] lg:pt-24"
        aria-labelledby="hero">
        <div class="@container flex flex-col gap-6">
          {/*
           * Always the same three lines, scaled to the column so the widest one, "in your Mindustry" at about 8.7em,
           * fits. The changing word ends its line, so it never moves the rest of the headline
           */}
          <h1 id="hero" class="text-[length:min(3.75rem,11cqi)] leading-[1.02] whitespace-nowrap">
            No more <RotatingWord words={HERO_WORDS} />
            <br />
            in your Mindustry
            <br />
            server.
          </h1>
          <p class="max-w-xl text-lg text-ink-2">
            NoHorny <strong class="font-normal text-accent-ink">detects</strong> NSFW logic displays, canvases, sorters
            and illuminators on your{' '}
            <a class="link" href={MINDUSTRY} {...EXTERNAL}>
              Mindustry
            </a>{' '}
            server the moment they are built, <strong class="font-normal text-accent-ink">deletes</strong> them and{' '}
            <strong class="font-normal text-accent-ink">bans</strong> their author.
          </p>
          <p class="text-ink-2">
            Enjoy this family friendly factory building game as the{' '}
            <a class="link" href="https://github.com/Anuken" {...EXTERNAL}>
              cat
            </a>{' '}
            intended it to be.
          </p>
          <div class="flex flex-wrap gap-3">
            <a class={buttonClass('primary')} href={DOWNLOAD}>
              <DownloadIcon />
              Download the plugin
            </a>
            <a class={buttonClass()} href={GITHUB} {...EXTERNAL}>
              <GitHubIcon />
              Source code
            </a>
          </div>
        </div>

        <section class="card relative flex flex-col gap-6 overflow-hidden p-6 sm:p-8" aria-labelledby="live">
          <div class="flex items-center justify-between">
            <h2 id="live" class="text-ink-2">
              Images classified
            </h2>
            <LiveIndicator connection={stats.connection()} label={stats.label()} />
          </div>
          <p aria-hidden="true" class="text-7xl tabular-nums sm:text-8xl">
            {number(total())}
          </p>
          <p class="sr-only" aria-live="polite" aria-atomic="true">
            {spoken()}
          </p>
          <div class="@container border-line-strong border-t border-dashed pt-6">
            <div class="grid grid-cols-2 gap-2 @lg:grid-cols-[1fr_auto_auto]">
              <p class="col-span-2 flex items-center gap-3 rounded-xl border border-line bg-surface-2 px-4 py-2.5 @lg:col-span-1">
                <b class="text-3xl font-normal">{number(processed())}</b>
                <span class="flex flex-col leading-tight">
                  <span>{processed() === 1 ? 'image processed' : 'images processed'}</span>
                  <small class="text-ink-3">in the last 24 hours</small>
                </span>
              </p>
              <DayCount rating="warn" label="flagged" value={flagged()} />
              <DayCount rating="nsfw" label="nsfw" value={stats.data()?.last24Hours.ratings.nsfw} />
            </div>
          </div>
        </section>
      </section>

      <section id="how" class="wrap py-12" aria-labelledby="how-title">
        <h2 id="how-title" class="mb-8 text-4xl">
          How it works
        </h2>
        <ol class="steps grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Step step="watch" icon={<ScanSearchIcon />} title="Watch" art={<PixelArt scanned />}>
            Spots pixel art on logic displays, canvases, sorters and illuminators.
          </Step>
          <Step step="render" icon={<ImageIcon />} title="Render" art={<Rendering />}>
            Turns it into an image, off the main thread.
          </Step>
          <Step step="classify" icon={<EyeIcon />} title="Classify" art={<Verdicts />}>
            The NoHorny server rates it safe, warn or NSFW.
          </Step>
          <Step step="act" icon={<GavelIcon />} title="Act" art={<PixelArt banned />}>
            Deletes the build and bans its author.
          </Step>
        </ol>
      </section>

      <section id="why" class="wrap py-12" aria-labelledby="why-title">
        <h2 id="why-title" class="mb-8 text-4xl">
          Why NoHorny
        </h2>
        <div class="grid gap-4 md:grid-cols-2">
          <article class="card flex flex-col gap-6 p-6 sm:p-8">
            <div>
              <h3 class="text-3xl">Incredibly fast</h3>
              <p class="mt-2 text-ink-2">Go on, spam the canvases: we tested that too.</p>
            </div>
            <div class="flex flex-col items-start gap-6 sm:flex-row sm:items-center">
              <TickRing />
              <p class="text-ink-2">
                <strong class="block font-normal text-2xl text-ink">That sliver is NoHorny.</strong>
                The rest of the tick is all yours, even at{' '}
                <strong class="font-normal text-accent-ink">600 block changes</strong> a second.
              </p>
            </div>
          </article>
          {/* A container, so the tree scales down with the card instead of widening the grid past narrow screens */}
          <article class="@container card flex flex-col gap-6 p-6 sm:p-8">
            <div>
              <h3 class="text-3xl">Zero config needed</h3>
              <p class="mt-2 text-ink-2">
                Just put the jar in{' '}
                <code class="rounded-md border border-line bg-surface-2 px-1.5 py-0.5 font-mono text-[0.85em] text-ink">
                  config/mods
                </code>
                , and no more porn.
              </p>
            </div>
            <ul
              class="tree m-auto rounded-xl border border-line bg-bg-2 px-[1.7em] py-[1.4em] font-mono text-[length:min(0.875rem,4.5cqi)]"
              aria-hidden="true">
              <li>
                config/
                <ul>
                  <li>
                    mods/
                    <ul>
                      <li class="jar flex items-center gap-[0.6em]">
                        <span class="jar-drop whitespace-nowrap rounded-md border border-line-strong bg-surface-2 px-[0.6em] py-[0.15em] text-ink max-sm:text-[0.86em]">
                          nohorny-plugin.jar
                        </span>
                        <CheckIcon class="jar-tick size-[1.15em] text-safe" />
                      </li>
                    </ul>
                  </li>
                </ul>
              </li>
            </ul>
          </article>
        </div>
      </section>

      <section id="install" class="wrap py-12" aria-labelledby="install-title">
        <div class="card flex flex-col justify-between gap-8 p-6 sm:p-12 md:flex-row md:items-center">
          <div class="max-w-xl">
            <h2 id="install-title" class="text-4xl">
              What are you waiting for?
            </h2>
            <p class="mt-3 text-ink-2">
              You only need{' '}
              <a class="link" href={`${MINDUSTRY}/releases`} {...EXTERNAL}>
                Mindustry v159+
              </a>{' '}
              and <strong class="font-normal text-accent-ink">Java 25</strong> or above.
            </p>
          </div>
          <a class={['self-start md:self-center', ...buttonClass('primary')]} href={DOWNLOAD}>
            <DownloadIcon />
            Download the plugin
          </a>
        </div>
      </section>
    </>
  );
}

function DayCount(props: { rating: Bucket; label: string; value: number | undefined | null }) {
  return (
    <p class="flex min-w-20 flex-col rounded-xl border border-line bg-surface-2 px-3 py-2">
      <span class={['flex items-center gap-1.5 font-mono text-[0.65rem] uppercase', RATING_TEXT[props.rating]]}>
        <span class={['size-1.5 rounded-sm', RATING_FILL[props.rating]]} />
        {props.label}
      </span>
      <b class="text-xl font-normal">{number(props.value)}</b>
    </p>
  );
}

function Step(props: { step: string; icon: JSX.Element; title: string; art: JSX.Element; children: JSX.Element }) {
  return (
    <li class="card flex flex-col gap-2 p-2 pb-6" data-step={props.step}>
      <div class="mb-3 grid h-36 place-items-center rounded-xl bg-bg-2" aria-hidden="true">
        {props.art}
      </div>
      <h3 class="flex items-center gap-2 px-4 text-xl [&_svg]:size-4 [&_svg]:text-ink-3">
        {props.icon}
        {props.title}
      </h3>
      <p class="px-4 text-ink-2">{props.children}</p>
    </li>
  );
}

/** The heart, `scanned` adds the scanner of the watch step and `banned` shows it wiped. */
function PixelArt(props: { scanned?: boolean; banned?: boolean }) {
  return (
    <span class="pixels relative grid grid-cols-8 gap-0.5">
      <For each={[...ART]} keyed={false}>
        {(cell, index) => (
          <i
            class={['size-2.5 rounded-[2px]', props.banned ? 'bg-surface-3' : 'bg-(--c)', { lit: cell() !== '.' }]}
            style={{ '--x': index % 8, '--y': Math.floor(index / 8), '--c': ART_COLORS[cell()] }}
          />
        )}
      </For>
      <Show when={props.scanned}>
        <span class="scan" />
      </Show>
      <Show when={props.banned}>
        <span class="stamp absolute inset-0 m-auto h-fit w-fit -rotate-12 rounded-lg border-2 border-nsfw bg-bg-2 px-2 font-mono text-sm uppercase tracking-widest text-nsfw">
          Banned
        </span>
      </Show>
    </span>
  );
}

function Rendering() {
  return (
    <span class="flex flex-col items-center gap-4">
      <span class="rounded-lg border border-line-strong bg-surface-2 p-3">
        <PixelArt />
      </span>
      <span class="h-1 w-22 overflow-hidden rounded-full bg-surface-3">
        <span class="progress block h-full origin-left bg-accent" />
      </span>
    </span>
  );
}

function Verdicts() {
  return (
    <span class="flex w-44 flex-col gap-4">
      <span class="relative h-1.5 rounded-full bg-gradient-to-r from-fill-safe via-fill-warn to-fill-nsfw">
        <span class="gauge absolute top-1/2 left-[88%] size-3 -translate-1/2 rounded-full border-2 border-bg bg-ink" />
      </span>
      <span class="flex justify-between">
        <For each={['safe', 'warn', 'nsfw'] as const}>
          {(key) => (
            <span
              data-verdict={key}
              class={[
                'rounded-full border border-line px-1.5 font-mono text-[0.6rem] uppercase',
                RATING_TEXT[key],
                { 'opacity-35': key !== 'nsfw' },
              ]}>
              {key}
            </span>
          )}
        </For>
      </span>
    </span>
  );
}

/** The share of a server tick taken by NoHorny, 2%. A sweep goes around and NoHorny pulses as it is passed. */
function TickRing() {
  return (
    <span class="relative grid size-32 shrink-0 place-items-center">
      <svg viewBox="0 0 100 100" class="absolute inset-0 -rotate-90" aria-hidden="true">
        <circle cx="50" cy="50" r="40" fill="none" stroke="var(--surface-3)" stroke-width="16" />
        <circle
          class="tick-sweep"
          cx="50"
          cy="50"
          r="40"
          fill="none"
          stroke="currentColor"
          stroke-opacity="0.12"
          stroke-width="16"
          pathLength="100"
          stroke-dasharray="0 100"
        />
        <circle
          class="tick-nh"
          cx="50"
          cy="50"
          r="40"
          fill="none"
          stroke="var(--accent)"
          stroke-width="16"
          pathLength="100"
          stroke-dasharray="2 98"
        />
      </svg>
      <b class="text-3xl font-normal">2%</b>
    </span>
  );
}
