// What the plugin sends and what the server keeps, with the retention periods of this server.

import { Title } from '@solidjs/meta';
import type { JSX } from '@solidjs/web';
import { createSignal, For, onSettled } from 'solid-js';
import { api, type Retention } from '../../lib/api';
import { period } from '../../lib/format';
import { DISCORD, EXTERNAL, GITHUB } from '../../lib/links';

export default function Privacy() {
  // Each server sets its own periods, so the browser fetches them rather than the build.
  // The generic wording stays until they load, or if they fail to.
  const [retention, setRetention] = createSignal<Retention | null>(null);
  onSettled(() => {
    api<Retention>('/api/retention').then(setRetention, () => {});
  });
  const IMAGE = 'a limited time';
  const REQUEST = 'a longer time';
  const image = () => {
    const millis = retention()?.imageMillis;
    return millis ? period(millis) : IMAGE;
  };
  const request = () => {
    const millis = retention()?.requestMillis;
    return millis ? period(millis) : REQUEST;
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
    ['Can I delete an image?', () => 'Yes, with "Report and delete" on its page.'],
    ['Cookies?', () => 'Session data for admins. Nothing else.'],
  ];

  return (
    <div class="wrap max-w-3xl! py-16">
      <Title>Privacy · NoHorny</Title>
      <h1 class="text-5xl">Privacy</h1>
      <dl class="mt-10 divide-y divide-line border-y border-line">
        <For each={answers}>
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
