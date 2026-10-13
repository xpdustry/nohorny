// What the plugin sends and what the server keeps, with the retention periods of this server.

import { Title } from '@solidjs/meta';
import type { JSX } from '@solidjs/web';
import { createMemo, For, Loading } from 'solid-js';
import type { Retention } from '../../lib/api';
import { config } from '../../lib/config';
import { period } from '../../lib/format';
import { DISCORD, EXTERNAL, GITHUB } from '../../lib/links';

export default function Privacy() {
  // Each server sets its own periods and whether it reports the errors of the pages, see lib/sentry.ts. The build
  // cannot know them, the browser reads them once hydrated
  const settings = createMemo(() => config ?? null, { ssrSource: 'client' });

  return (
    <div class="wrap max-w-3xl! py-16">
      <Title>Privacy · NoHorny</Title>
      {/*
       * The prerendered page holds the generic wording, shown only when the app is slow to start or does not. The page
       * of this server replaces it at once after the hydration
       */}
      <Loading fallback={<Policy class="pending" retention={null} reporting={false} />}>
        <Policy class="reveal" retention={settings()?.retention ?? null} reporting={Boolean(settings()?.sentryDsn)} />
      </Loading>
    </div>
  );
}

function Policy(props: { class: string; retention: Retention | null; reporting: boolean }) {
  const image = () => {
    const millis = props.retention?.imageMillis;
    return millis ? period(millis) : 'a limited time';
  };
  const request = () => {
    const millis = props.retention?.requestMillis;
    return millis ? period(millis) : 'a longer time';
  };

  const answers: [string, () => JSX.Element][] = [
    ['What is sent?', () => 'The image and the plugin version. Nothing about players.'],
    [
      'What is kept?',
      () => (
        <>
          Every submitted image up to <strong class="font-normal text-ink">{image()}</strong>, the verdict and the IP
          address of the sender up to <strong class="font-normal text-ink">{request()}</strong>. Aggregated stats are
          kept as long as necessary.
        </>
      ),
    ],
    ['Who can see it?', () => 'Anyone with the request link, and the admins operating this NoHorny instance.'],
    ['Can I delete an image?', () => 'Yes, with "Delete image" on its page.'],
    ['Cookies?', () => 'Session data for admins. Nothing else.'],
  ];
  const errorReports: [string, () => JSX.Element] = [
    'Error reports?',
    () => (
      <>
        When a page fails, the error is sent to{' '}
        <a class="link" href="https://sentry.io" {...EXTERNAL}>
          Sentry
        </a>{' '}
        with the browser version and the page address, without its identifiers. No cookies or account data.
      </>
    ),
  ];

  return (
    <div class={props.class}>
      <h1 class="text-5xl">Privacy</h1>
      <dl class="mt-10 divide-y divide-line border-y border-line">
        <For each={props.reporting ? [...answers, errorReports] : answers}>
          {([question, answer]) => (
            <div class="grid gap-1 py-5 sm:grid-cols-[12rem_1fr] sm:gap-6">
              <dt class="text-accent-ink text-lg">{question}</dt>
              <dd class="text-ink-2">{answer()}</dd>
            </div>
          )}
        </For>
      </dl>
      <p class="mt-8 text-sm text-ink-3">
        These are the settings of this instance. NoHorny is{' '}
        <a class="link" href={GITHUB} {...EXTERNAL}>
          open source
        </a>{' '}
        and can be self-hosted. Questions? Ask in our{' '}
        <a class="link" href={DISCORD} {...EXTERNAL}>
          Discord
        </a>
        .
      </p>
    </div>
  );
}
