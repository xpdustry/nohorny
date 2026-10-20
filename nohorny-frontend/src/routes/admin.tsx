// The layout of the admin panel. It shows the sign in form until an administrator signs in, then the tabs and the page.

import { Meta, Title } from '@solidjs/meta';
import { type RouteSectionProps, useIsRouting } from '@solidjs/router';
import type { JSX } from '@solidjs/web';
import { createMemo, createSignal, Loading, Match, onSettled, Show, Switch } from 'solid-js';
import {
  ArrowUpIcon,
  ListIcon,
  LockIcon,
  LogOutIcon,
  SettingsIcon,
  ShieldIcon,
  TriangleAlertIcon,
  UsersIcon,
} from '../components/icon';
import { Brand, Button, buttonClass, Field, LoadingSpinner, Notice, State } from '../components/ui';
import { AdminContext, createAdmin } from '../lib/admin';
import { checkSession, login, logout, session } from '../lib/session';

export default function AdminLayout(props: RouteSectionProps) {
  // The session is only known in the browser. Waiting for it holds the Loading boundary below, so the prerendered page
  // is its fallback, and the panel reveals at once with the data of its page
  const checked = createMemo(() => checkSession().then(() => true), { ssrSource: 'client' });
  const [retrying, setRetrying] = createSignal(false);
  async function retry() {
    setRetrying(true);
    await checkSession(true);
    setRetrying(false);
  }
  const [signOutFailure, setSignOutFailure] = createSignal<string | null>(null);
  async function signOut() {
    setSignOutFailure(null);
    try {
      await logout();
    } catch (error) {
      setSignOutFailure((error as Error).message);
    }
  }
  return (
    <>
      <Title>NoHorny admin</Title>
      <Meta name="robots" content="noindex, nofollow" />
      <Loading fallback={<LoadingSpinner class="min-h-dvh" size="size-6" />}>
        <Show when={checked()}>
          <Switch>
            <Match when={session.status === 'ready' && session.session?.admin}>
              <AdminShell>{props.children}</AdminShell>
            </Match>
            <Match when={session.status === 'ready'}>
              <Gate>
                <State icon={<ShieldIcon />} title="Administrators only">
                  <p class="text-ink-2">
                    You are signed in as <b class="font-normal text-ink">{session.session?.username}</b>, which is not
                    an administrator.
                  </p>
                  <Button onClick={() => void signOut()}>
                    <LogOutIcon />
                    Sign out
                  </Button>
                  <Show when={signOutFailure()}>
                    <p role="alert" class="text-danger text-sm">
                      {signOutFailure()}
                    </p>
                  </Show>
                </State>
              </Gate>
            </Match>
            <Match when={session.status === 'signed-out'}>
              <Gate>
                <Login />
              </Gate>
            </Match>
            <Match when={session.status === 'error'}>
              <Gate>
                <State icon={<TriangleAlertIcon />} title="Something went wrong" tone="error">
                  <p role="alert" class="text-ink-2">
                    {session.error}
                  </p>
                  <Button variant="primary" busy={retrying()} onClick={() => void retry()}>
                    Retry
                  </Button>
                </State>
              </Gate>
            </Match>
          </Switch>
        </Show>
      </Loading>
    </>
  );
}

/** The centered card holding the sign in form and the error states. */
function Gate(props: { children: JSX.Element }) {
  return (
    <main class="reveal grid min-h-dvh place-items-center p-4">
      <div class="flex w-full max-w-sm flex-col items-center gap-6">
        <Brand tag="Admin" />
        <div class="card w-full">{props.children}</div>
        <a href="/" class="text-ink-3 text-sm hover:text-ink">
          Back to the home page
        </a>
      </div>
    </main>
  );
}

function Login() {
  const [busy, setBusy] = createSignal(false);
  const [error, setError] = createSignal<string | null>(null);
  let password!: HTMLInputElement;

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const data = new FormData(form);
    setBusy(true);
    setError(null);
    try {
      // On success the form stays busy, until the panel replaces it with the data of its page
      await login(String(data.get('username')), String(data.get('password')));
    } catch (failure) {
      setBusy(false);
      setError((failure as Error).message);
      password.value = '';
      password.focus();
    }
  }

  return (
    <form class="flex flex-col gap-4 p-6" onSubmit={submit}>
      <h1 class="flex items-center gap-2 text-2xl">
        <LockIcon class="size-5 text-ink-3" />
        Sign in
      </h1>
      <Show when={session.notice}>
        <p role="status" class="rounded-xl border border-line bg-surface-2 px-3 py-2 text-ink-2 text-sm">
          {session.notice}
        </p>
      </Show>
      <Field
        label="Username"
        name="username"
        autocomplete="username"
        autocapitalize="none"
        spellcheck={false}
        required
        autofocus
      />
      <Field ref={password} label="Password" name="password" type="password" autocomplete="current-password" required />
      <Show when={error()}>
        <p role="alert" class="text-danger text-sm">
          {error()}
        </p>
      </Show>
      <Button type="submit" variant="primary" busy={busy()} class="mt-1 justify-center">
        Sign in
      </Button>
    </form>
  );
}

const TAB = [
  'inline-flex items-center gap-2 rounded-full border border-transparent px-3.5 py-1.5 text-ink-2 text-sm no-underline transition',
  'hover:bg-surface-2 hover:text-ink aria-[current=page]:border-accent-edge aria-[current=page]:bg-accent-soft aria-[current=page]:text-on-accent-soft [&_svg]:size-4',
];

function AdminShell(props: { children: JSX.Element }) {
  const admin = createAdmin();
  const routing = useIsRouting();
  return (
    <AdminContext value={admin}>
      <div class="reveal flex min-h-dvh flex-col">
        <header class="sticky top-0 z-40 border-line border-b bg-bg">
          {/* The previous page stays until the next one is ready, the bar tells that the click was heard */}
          <span aria-hidden="true" class={['progress-bar', { active: routing() }]} />
          <div class="wrap flex h-16 items-center gap-3">
            <Brand tag="Admin" />
            <nav aria-label="Admin sections" class="ml-4 flex gap-1">
              <a href="/admin" class={TAB}>
                <ListIcon />
                <span class="max-sm:sr-only">Requests</span>
              </a>
              <a href="/admin/users" class={TAB}>
                <UsersIcon />
                <span class="max-sm:sr-only">Users</span>
              </a>
              <a href="/admin/settings" class={TAB}>
                <SettingsIcon />
                <span class="max-sm:sr-only">Settings</span>
              </a>
            </nav>
            <span class="flex-1" />
            <span class="hidden text-ink-2 text-sm lg:inline">
              Signed in as <b class="font-normal text-ink">{session.session?.username}</b>
            </span>
            <button
              type="button"
              class={buttonClass('default', 'icon')}
              aria-label="Sign out"
              data-tooltip="Sign out"
              onClick={() => logout().catch((error: Error) => admin.notify(error.message, 'error'))}>
              <LogOutIcon />
            </button>
          </div>
        </header>
        <main id="main" class="wrap flex flex-1 flex-col gap-6 py-8">
          {props.children}
        </main>
        {/* Floats over the page, so the outcome of an action shows wherever the row was */}
        <div class="pointer-events-none fixed inset-x-4 bottom-4 z-40 flex items-end justify-end gap-2">
          <Show when={admin.status()}>
            {(status) => (
              <div class="pop-in pointer-events-auto w-full rounded-xl shadow-float sm:w-md">
                <Notice tone={status().tone} onDismiss={() => admin.notify(null)}>
                  {status().text}
                </Notice>
              </div>
            )}
          </Show>
          <BackToTop />
        </div>
      </div>
    </AdminContext>
  );
}

/** How far the page scrolls, in heights of the window, before the button shows. */
const BACK_TO_TOP_SCREENS = 1;

/** Fades in once the page is scrolled down, back to its top. */
function BackToTop() {
  const [shown, setShown] = createSignal(false);
  onSettled(() => {
    const update = () => setShown(window.scrollY > window.innerHeight * BACK_TO_TOP_SCREENS);
    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  });
  // Stays in the page while hidden, so it fades out as well as in. The visibility flips after the fade, and keeps the
  // hidden button out of the focus order and the accessibility tree
  return (
    <button
      type="button"
      class={[
        'grid size-10 shrink-0 place-items-center rounded-full border border-line-strong bg-surface/60 text-ink shadow-float backdrop-blur-md hover:bg-surface-2/80 pointer-coarse:size-11 [&_svg]:size-4',
        'transition-[opacity,translate,visibility,background-color] duration-200 ease-out motion-reduce:translate-y-0',
        shown() ? 'pointer-events-auto' : 'invisible translate-y-2 opacity-0',
      ]}
      aria-label="Back to the top"
      data-tooltip="Back to the top"
      onClick={() =>
        window.scrollTo({
          top: 0,
          behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
        })
      }>
      <ArrowUpIcon />
    </button>
  );
}
