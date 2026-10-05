import solid from '@solidjs/vite-plugin';
import tailwindcss from '@tailwindcss/vite';
import { fileRoutes } from 'filesystem-routing/vite';
import { prerender } from 'prerender-crawler/vite';
import { defineConfig } from 'vite';

// The dev server proxies the API to a NoHorny server on its default address
const backend = process.env.NOHORNY_BACKEND ?? 'http://127.0.0.1:8080';

export default defineConfig({
  // The Solid plugin generates the entries from src/App.tsx and src/Document.tsx. `vite build` renders the pages
  // listed below through the server build into dist/client, and the browser hydrates them.
  // The server bundles dist/client and answers each path with its page, see NoHornyConfiguration.
  plugins: [
    // `extensions` makes the plugin also compile the `?pick=` route modules fileRoutes emits
    solid({ start: true, ssr: true, extensions: ['.jsx', '.tsx'], diagnostics: true }),
    fileRoutes({ types: true }),
    tailwindcss(),
    prerender({
      mode: 'static',
      // Lists the pages instead of following links, because the server maps its paths onto these files
      crawlLinks: false,
      pages: [
        '/',
        '/privacy',
        '/admin',
        '/admin/users',
        // The request page renders in the browser, so one shell serves every request identifier
        { path: '/requests/shell', filename: 'request.html' },
        // Spring Boot serves this page for unknown paths
        { path: '/not-found', filename: 'error/404.html' },
      ],
    }),
  ],
  server: {
    host: '127.0.0.1',
    proxy: Object.fromEntries(['/api', '/login', '/logout'].map((path) => [path, backend])),
  },
  preview: {
    host: '127.0.0.1',
  },
  build: {
    target: 'esnext',
    assetsInlineLimit: 0,
  },
});
