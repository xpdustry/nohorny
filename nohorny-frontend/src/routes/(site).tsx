// The layout of the public pages, their header and footer.

import type { RouteSectionProps } from '@solidjs/router';
import { Show } from 'solid-js';
import xpdustryLogo from '../assets/xpdustry.svg';
import { ArrowUpIcon, DiscordIcon, GitHubIcon } from '../components/icon';
import { Brand, buttonClass } from '../components/ui';
import { DISCORD, EXTERNAL, GITHUB } from '../lib/links';
import { isAdmin } from '../lib/session';

const NAV_LINK =
  'inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm text-ink-2 no-underline transition hover:bg-surface-2 hover:text-ink aria-[current=page]:text-ink [&_svg]:size-4';

// The year of the build, the pages are prerendered
const YEAR = new Date().getFullYear();

export default function SiteLayout(props: RouteSectionProps) {
  return (
    <div class="flex min-h-dvh flex-col">
      <a
        href="#main"
        class="fixed top-2 left-2 z-50 -translate-y-20 rounded-full bg-accent px-4 py-2 text-on-accent focus:translate-y-0">
        Skip to content
      </a>
      <header class="sticky top-0 z-40 border-line border-b bg-bg">
        <div class="wrap flex h-16 items-center gap-1">
          <Brand />
          <nav aria-label="Main" class="ml-4 hidden items-center gap-1 sm:flex">
            <a href="/#how" class={NAV_LINK}>
              How it works
            </a>
            <a href="/privacy" class={NAV_LINK}>
              Privacy
            </a>
            <Show when={isAdmin()}>
              <a href="/admin" class={NAV_LINK}>
                Admin panel
              </a>
            </Show>
          </nav>
          <span class="flex-1" />
          <a href={DISCORD} {...EXTERNAL} class={NAV_LINK}>
            <DiscordIcon />
            <span class="max-sm:sr-only">Discord</span>
          </a>
          <a href={GITHUB} {...EXTERNAL} class={NAV_LINK}>
            <GitHubIcon />
            <span class="max-sm:sr-only">GitHub</span>
          </a>
        </div>
      </header>

      <main id="main" tabindex="-1" class="flex-1 outline-none">
        {props.children}
      </main>

      <footer class="mt-24 border-line border-t">
        <div class="wrap flex flex-wrap items-center gap-x-6 gap-y-3 py-8 text-sm">
          <Brand />
          <nav aria-label="Footer" class="flex flex-wrap gap-4 text-ink-2 [&_a:hover]:text-ink">
            <a href={`${GITHUB}/releases`} {...EXTERNAL}>
              Releases
            </a>
            <a href="/privacy">Privacy</a>
            <a href="https://xpdustry.com" {...EXTERNAL}>
              xpdustry.com
            </a>
          </nav>
          <span class="flex-1" />
          <div class="flex gap-2 [&_svg]:size-4">
            <a
              class={buttonClass('default', 'icon')}
              href={GITHUB}
              {...EXTERNAL}
              aria-label="NoHorny on GitHub"
              data-tooltip="NoHorny on GitHub">
              <GitHubIcon />
            </a>
            <a
              class={buttonClass('default', 'icon')}
              href={DISCORD}
              {...EXTERNAL}
              aria-label="Xpdustry Discord"
              data-tooltip="Xpdustry Discord">
              <DiscordIcon />
            </a>
            <button
              type="button"
              class={buttonClass('default', 'icon')}
              aria-label="Back to top"
              data-tooltip="Back to top"
              onClick={() => window.scrollTo({ top: 0 })}>
              <ArrowUpIcon />
            </button>
          </div>
        </div>
        <div class="wrap border-line border-t py-6 text-sm">
          <a
            href="https://xpdustry.com"
            {...EXTERNAL}
            class="inline-flex items-center gap-3 text-ink-3 transition-colors hover:text-ink">
            {/* A mask, so the monochrome logo takes the colour of the text like on xpdustry.com */}
            <span
              aria-hidden="true"
              class="size-7 shrink-0 bg-current [mask-position:center] [mask-repeat:no-repeat] [mask-size:contain]"
              style={{ 'mask-image': `url("${xpdustryLogo}")` }}
            />
            © {YEAR} Xpdustry
          </a>
        </div>
      </footer>
    </div>
  );
}
