import { Title } from '@solidjs/meta';
import { createMemo, createSignal, Errored, onSettled, refresh, Show } from 'solid-js';
import { runAction } from '../lib/action';
import { api, apiEmpty, describe, type Request, requestPath, statusOf } from '../lib/api';
import { relativeTime, shortId } from '../lib/format';
import { checkSession, isAdmin } from '../lib/session';
import { ArrowLeftIcon, ImageOffIcon, SearchXIcon, TrashIcon, TriangleAlertIcon } from './icon';
import { ClassificationCard, confirmImageDeletion, confirmRequestDeletion, DetailsCard, RequestImage } from './request';
import { Button, buttonClass, Notice, State } from './ui';

const CAPTIONS = {
  expired: 'The image was deleted when its retention period ended.',
  deleted: 'The image was deleted.',
};

export default function RequestPage(props: { id: string }) {
  onSettled(() => void checkSession());

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
          {/*
           * No Loading boundary of its own: the one of the app holds the swap from the fallback of the route, so the
           * page shows a single loading state until the request is here
           */}
          <div class="reveal">
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
          </div>
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

  const act = (action: () => Promise<string | undefined>, failed: (error: unknown) => string | undefined) =>
    runAction(
      {
        busy: (on) => {
          setBusy(on);
          if (on) setMessage(null);
        },
        report: (text, tone) => setMessage({ tone, text }),
      },
      action,
      failed,
    );

  /** Anyone can delete the image. The API rate limits the deletions. */
  async function deleteImage() {
    if (!(await confirmImageDeletion(props.request))) return;
    await act(
      async () => {
        await apiEmpty(`${path()}/image`, { method: 'DELETE' });
        setRevealed(false);
        props.onChange();
        return 'The image was deleted.';
      },
      (error) => {
        if (statusOf(error) === 404) {
          props.onMissing();
          return;
        }
        if (statusOf(error) === 429) return 'Too many deletions from your address, try later.';
        return describe(
          error,
          'The deletion failed, reload the page and try again',
          'Could not reach the server, the image was not deleted.',
        );
      },
    );
  }

  async function remove() {
    if (!(await confirmRequestDeletion(props.request))) return;
    await act(
      async () => {
        await apiEmpty(path(), { method: 'DELETE' });
        props.onDeleted();
        return undefined;
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
            <time datetime={props.request.createdAt} data-tooltip={props.request.createdAt}>
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

      <div class="grid items-start gap-x-6 gap-y-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <div class="flex flex-col gap-4">
          <RequestImage request={props.request} revealed={revealed()} onReveal={setRevealed} />
          <div class="flex flex-wrap items-center gap-2">
            <Show
              when={CAPTIONS[props.request.image.state as keyof typeof CAPTIONS]}
              fallback={
                <Show when={props.request.image.state === 'stored'}>
                  <Button variant="danger" busy={busy()} onClick={deleteImage}>
                    <Show when={!busy()}>
                      <ImageOffIcon />
                    </Show>
                    Delete image
                  </Button>
                </Show>
              }>
              {(caption) => (
                <p class="flex flex-1 items-center gap-2 text-ink-3 text-sm">
                  <ImageOffIcon />
                  {caption()}
                </p>
              )}
            </Show>
            <Show when={isAdmin()}>
              <Button variant="solid-danger" busy={busy()} onClick={remove}>
                <Show when={!busy()}>
                  <TrashIcon />
                </Show>
                Delete request
              </Button>
            </Show>
          </div>
        </div>

        <div class="flex flex-col gap-4">
          <ClassificationCard request={props.request} />
          <DetailsCard request={props.request} />
        </div>
      </div>
    </div>
  );
}
