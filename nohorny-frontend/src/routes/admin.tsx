// The layout of the admin panel. It shows the sign in form until an administrator signs in, then the tabs and the page.

import { Meta, Title } from '@solidjs/meta';
import type { RouteSectionProps } from '@solidjs/router';
import type { JSX } from '@solidjs/web';
import { createSignal, Match, onSettled, Show, Switch } from 'solid-js';
import { ListIcon, LockIcon, LogOutIcon, ShieldIcon, TriangleAlertIcon, UsersIcon } from '../components/icon';
import { Brand, Button, buttonClass, Field, Notice, Spinner, State } from '../components/ui';
import { AdminContext, createAdmin } from '../lib/admin';
import { checkSession, login, logout, session } from '../lib/session';

export default function AdminLayout(props: RouteSectionProps) {
  onSettled(() => void checkSession());
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
      <Switch>
        <Match when={session.status === 'ready' && session.session?.admin}>
          <AdminShell>{props.children}</AdminShell>
        </Match>
        <Match when={session.status === 'ready'}>
          <Gate>
            <State icon={<ShieldIcon />} title="Administrators only">
              <p class="text-ink-2">
                You are signed in as <b class="font-normal text-ink">{session.session?.username}</b>, which is not an
                administrator.
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
              <Button variant="primary" onClick={() => void checkSession(true)}>
                Retry
              </Button>
            </State>
          </Gate>
        </Match>
        <Match when>
          <div class="grid min-h-dvh place-items-center text-ink-3">
            <Spinner class="size-6" />
          </div>
        </Match>
      </Switch>
    </>
  );
}

/** The centered card holding the sign in form and the error states. */
function Gate(props: { children: JSX.Element }) {
  return (
    <main class="grid min-h-dvh place-items-center p-4">
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
      await login(String(data.get('username')), String(data.get('password')));
    } catch (failure) {
      setError((failure as Error).message);
      password.value = '';
      password.focus();
    } finally {
      setBusy(false);
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
  return (
    <AdminContext value={admin}>
      <div class="flex min-h-dvh flex-col">
        <header class="sticky top-0 z-40 border-line border-b bg-bg">
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
        <div class="pointer-events-none fixed inset-x-4 bottom-4 z-40 flex justify-center sm:left-auto sm:w-md">
          <Show when={admin.status()}>
            {(status) => (
              <div class="pop-in pointer-events-auto w-full rounded-xl shadow-float">
                <Notice tone={status().tone} onDismiss={() => admin.notify(null)}>
                  {status().text}
                </Notice>
              </div>
            )}
          </Show>
        </div>
      </div>
    </AdminContext>
  );
}
