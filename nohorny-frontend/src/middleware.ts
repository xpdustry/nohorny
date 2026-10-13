// Runs around the server rendering of every page, the prerendering included.

/**
 * Defers the client entry until the document is parsed. The plugin marks it async to hydrate a streamed page early,
 * but when the HTML arrives slower than the cached scripts, such as with the back button, it runs before the end of
 * the body is parsed and fails to hydrate the missing elements.
 *
 * TODO: Find out why the hydration does not wait for the rest of the document, and report it to Solid.
 *   Remove this middleware once @solidjs/vite-plugin or solid-js handles it.
 */
export default async function deferEntry(_request: Request, next: () => Promise<Response>) {
  const response = await next();
  if (!response.headers.get('content-type')?.includes('text/html')) return response;
  const html = await response.text();
  return new Response(html.replace(/(<script type="module"[^>]*) async>/, '$1>'), response);
}
