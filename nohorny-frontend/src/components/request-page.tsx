// The page of one request at /requests/:id, with its verdict, its blurred image and the purge button.
// It renders in the browser only, so one prerendered shell serves every request identifier.

import { Title } from '@solidjs/meta';
import { createMemo, createSignal, Errored, Loading, onSettled, refresh, Show } from 'solid-js';
import { api, describe, type Request, requestPath, statusOf } from '../lib/api';
import { relativeTime, shortId } from '../lib/format';
import { checkSession, isAdmin } from '../lib/session';
import { confirm } from './dialog';
import { ArrowLeftIcon, FlagIcon, ListIcon, LockIcon, SearchXIcon, TrashIcon, TriangleAlertIcon } from './icon';
import { Card, ChainCard, DetailsCard, KeyValues, RequestImage, RequestLoading, VerdictCard } from './request';
import { Button, buttonClass, Notice, State } from './ui';

const CAPTIONS = {
  expired: 'The image was deleted when its retention period ended. The verdict stays on record.',
  purged: 'The image was deleted by hand. The verdict stays on record.',
};

export default function RequestPage(props: { id: string }) {
  onSettled(() => void checkSession());

  // Resolves to null for an unknown request. Other errors reach the Errored boundary
  const request = createMemo(async () => {
    try {
      return await api<Request>(requestPath(props.id));
    } catch (error) {
      if (statusOf(error) === 404) return null;
      throw error;
    }
  });

  const [deleted, setDeleted] = createSignal(false);

  return (
    <div>
      <Title>Request {shortId(props.id)} · NoHorny</Title>
      <Show
        when={!deleted()}
        fallback={
          <State icon={<TrashIcon />} title="Request deleted">
            <p class="text-ink-2">The request and its image are gone. This link no longer works.</p>
            <a class={buttonClass('primary')} href="/admin">
              Back to the admin panel
            </a>
          </State>
        }>
        <Errored
          fallback={(error, reset) => (
            <State icon={<TriangleAlertIcon />} title="Something went wrong" tone="error">
              <p role="alert" class="text-ink-2">
                {describe(
                  error(),
                  'The server answered with an error',
                  'Could not reach the server. Check your connection and retry.',
                )}
              </p>
              <Button variant="primary" onClick={() => reset()}>
                Retry
              </Button>
            </State>
          )}>
          <Loading fallback={<RequestLoading />}>
            <Show
              when={request()}
              fallback={
                <State icon={<SearchXIcon />} title="Request not found">
                  <p class="text-ink-2">This request does not exist or was deleted.</p>
                  <a class={buttonClass('primary')} href="/">
                    Go to the home page
                  </a>
                </State>
              }>
              {(value) => (
                <RequestView
                  request={value()}
                  onChange={() => refresh(request)}
                  onDeleted={() => setDeleted(true)}
                  onMissing={() => refresh(request)}
                />
              )}
            </Show>
          </Loading>
        </Errored>
      </Show>
    </div>
  );
}

function RequestView(props: { request: Request; onChange: () => void; onDeleted: () => void; onMissing: () => void }) {
  const [revealed, setRevealed] = createSignal(false);
  const [busy, setBusy] = createSignal(false);
  const [message, setMessage] = createSignal<{ tone: 'success' | 'error'; text: string } | null>(null);
  const path = () => requestPath(props.request.id);

  /** Runs an action while `busy` is set. `failed(error)` returns the message to show, if any. */
  async function act(action: () => Promise<void>, failed: (error: unknown) => string | undefined) {
    setBusy(true);
    setMessage(null);
    try {
      await action();
    } catch (error) {
      const text = failed(error);
      if (text) setMessage({ tone: 'error', text });
    } finally {
      setBusy(false);
    }
  }

  /** Anyone can purge the image. The API rate limits the purges. */
  async function purge() {
    const confirmed = await confirm({
      title: 'Report illegal content?',
      body: 'This permanently deletes the image from NoHorny, for everyone including moderators. The verdict stays on record without the image. This cannot be undone.',
      action: 'Delete image',
    });
    if (!confirmed) return;
    await act(
      async () => {
        await api(`${path()}/purge`, { method: 'POST' });
        setRevealed(false);
        setMessage({ tone: 'success', text: 'The image was deleted. Thank you for reporting it.' });
        props.onChange();
      },
      (error) => {
        if (statusOf(error) === 404) {
          props.onMissing();
          return;
        }
        if (statusOf(error) === 429) return 'Too many reports from your address, try later.';
        return describe(
          error,
          'The report failed, reload the page and try again',
          'Could not reach the server, the image was not deleted.',
        );
      },
    );
  }

  async function remove() {
    const confirmed = await confirm({
      title: 'Delete this request?',
      body: 'The request, its verdict and its image are removed for good and this link stops working. Statistics are not affected.',
      action: 'Delete request',
    });
    if (!confirmed) return;
    await act(
      async () => {
        await api(path(), { method: 'DELETE' });
        props.onDeleted();
      },
      (error) => {
        if (statusOf(error) === 404) {
          props.onDeleted();
          return;
        }
        if (statusOf(error) === 401 || statusOf(error) === 403)
          return 'Your admin session expired or was rejected. Sign in again from the admin panel.';
        return describe(error, 'Deletion failed', 'Could not reach the server, the request was not deleted.');
      },
    );
  }

  return (
    <div class="flex flex-col gap-6">
      <a href={isAdmin() ? '/admin' : '/'} class="inline-flex items-center gap-2 self-start text-ink-2 hover:text-ink">
        <ArrowLeftIcon />
        {isAdmin() ? 'All requests' : 'Home'}
      </a>

      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 class="text-4xl">
            Request <span class="font-mono text-ink-3">{shortId(props.request.id)}</span>
          </h1>
          <p class="mt-1 text-ink-2">
            Classified{' '}
            <time datetime={props.request.createdAt} title={props.request.createdAt}>
              {relativeTime(props.request.createdAt)}
            </time>
            .
          </p>
        </div>
      </header>

      <Show when={message()}>
        {(value) => (
          <Notice tone={value().tone} onDismiss={() => setMessage(null)}>
            {value().text}
          </Notice>
        )}
      </Show>

      {/* The verdict follows the image on small screens, the report box comes after the details */}
      <div class="grid items-start gap-x-6 gap-y-4 lg:grid-cols-[1.2fr_1fr]">
        <div class="flex flex-col gap-4">
          <RequestImage request={props.request} revealed={revealed()} onReveal={setRevealed} />
          <Show when={CAPTIONS[props.request.image.state as keyof typeof CAPTIONS]}>
            {(caption) => (
              <p class="flex items-center gap-2 text-ink-3 text-sm">
                <LockIcon />
                {caption()}
              </p>
            )}
          </Show>
        </div>

        <div class="flex flex-col gap-4 lg:row-span-2">
          <VerdictCard request={props.request} />
          <ChainCard request={props.request} />
          <DetailsCard request={props.request} />
          <Show when={isAdmin()}>
            <Card title="Moderation · admins only">
              <KeyValues
                rows={[
                  ['Address', <code class="break-all">{props.request.remoteAddress ?? '–'}</code>],
                  ['API user', props.request.username ?? 'anonymous'],
                ]}
              />
              <div class="flex flex-wrap gap-2">
                <Button variant="danger" busy={busy()} onClick={remove}>
                  <TrashIcon />
                  Delete request
                </Button>
                <a class={buttonClass()} href="/admin">
                  <ListIcon />
                  All requests
                </a>
              </div>
            </Card>
          </Show>
        </div>

        <Show when={props.request.image.state === 'stored'}>
          <section class="card flex flex-col items-start gap-3 p-6 lg:col-start-1 lg:row-start-2">
            <h2 class="flex items-center gap-2 text-xl">
              <FlagIcon class="size-5 text-danger" />
              Illegal content?
            </h2>
            <p class="text-ink-2">
              Flagged images are kept for a{' '}
              <a class="link" href="/privacy">
                limited time
              </a>{' '}
              so moderators can review them. If this one shows illegal content, report it to delete it now. This cannot
              be undone.
            </p>
            <Button variant="danger" busy={busy()} onClick={purge}>
              <Show when={!busy()}>
                <FlagIcon />
              </Show>
              Report and delete the image
            </Button>
          </section>
        </Show>
      </div>
    </div>
  );
}
