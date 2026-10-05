import momoLatin from '@fontsource/momo-trust-display/files/momo-trust-display-latin-400-normal.woff2?url';
import martianLatin from '@fontsource-variable/martian-mono/files/martian-mono-latin-wght-normal.woff2?url';
import { HydrationScript } from '@solidjs/web';
import type { ParentProps } from 'solid-js';

// The <html> around every page. vite build prerenders it with each page listed in vite.config.ts.
export default function Document(props: ParentProps) {
  return (
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="color-scheme" content="dark light" />
        <meta name="theme-color" content="#140b16" media="(prefers-color-scheme: dark)" />
        <meta name="theme-color" content="#fcf5fa" media="(prefers-color-scheme: light)" />
        {/* A request URL grants access to the request. Other sites must not receive it as a referrer. */}
        <meta name="referrer" content="same-origin" />
        <meta
          name="description"
          content="NoHorny detects NSFW logic displays, canvases, sorter and illuminator pixel art on Mindustry servers, deletes it and bans the author."
        />
        <meta property="og:title" content="NoHorny" />
        <meta property="og:description" content="Autonomous NSFW moderation for Mindustry servers." />
        <meta property="og:type" content="website" />
        <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
        <link rel="preload" as="font" type="font/woff2" href={momoLatin} crossorigin="anonymous" />
        <link rel="preload" as="font" type="font/woff2" href={martianLatin} crossorigin="anonymous" />
        <title>NoHorny · Autonomous NSFW moderation for Mindustry servers</title>
        <HydrationScript />
      </head>
      <body>{props.children}</body>
    </html>
  );
}
