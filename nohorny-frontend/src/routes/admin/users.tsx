// The users page of the admin panel, which manages the accounts, their roles, passwords and rate limits.

import { Meta, Title } from '@solidjs/meta';
import { createMemo, createSignal, createStore, Errored, For, Loading, refresh, Show, useContext } from 'solid-js';
import { confirm, Dialog } from '../../components/dialog';
import { GaugeIcon, KeyIcon, PlusIcon, ShieldMinusIcon, ShieldPlusIcon, TrashIcon } from '../../components/icon';
import { Button, Chip, Field, Spinner, State } from '../../components/ui';
import { AdminContext } from '../../lib/admin';
import { describe, statusOf, type User, userPath } from '../../lib/api';
import { formatDate } from '../../lib/format';
import { call, expire, SessionExpired, session } from '../../lib/session';

const USERNAME_RULE = /^[a-z0-9_.-]{3,32}$/;

const BOOTSTRAP = 'The bootstrap administrator is configured on the server';

const USERNAME_HINT = '3 to 32 lowercase letters, digits, _ . or -';

const PASSWORD_LENGTH = 8;

/** The bounds the API accepts for a rate limit. */
const MAX_RATE_LIMIT = 100_000;

const RATE_LIMIT_HINT = 'Classifications per minute, shared by all the servers using the account';

/** Why a rate limit is refused, or null. Blank is only allowed where the server default applies. */
function rateLimitProblem(value: string, optional: boolean): string | null {
  if (!value.trim()) return optional ? null : 'Enter a limit';
  const limit = Number(value);
  return Number.isInteger(limit) && limit >= 1 && limit <= MAX_RATE_LIMIT
    ? null
    : `Use a whole number from 1 to ${MAX_RATE_LIMIT.toLocaleString('en')}`;
}

const plural = (count: number, noun: string) => `${count} ${noun}${count === 1 ? '' : 's'}`;

export default function Users() {
  const admin = useContext(AdminContext);
  const users = createMemo(async () => {
    const list = await call<User[]>('/api/users');
    return list.toSorted((a, b) => (a.username < b.username ? -1 : a.username > b.username ? 1 : 0));
  });
  const [busy, setBusy] = createStore<Record<string, boolean>>({});
  const [editing, setEditing] = createSignal<User | null>(null);
  const [limiting, setLimiting] = createSignal<User | null>(null);
  const [creating, setCreating] = createSignal(false);

  const isSelf = (account: User) => account.username === session.session?.username;
  const adminCount = () => users().filter((account) => account.admin).length;

  /** Why the API would refuse to remove the admin role, or null. */
  function demoteBlocked(account: User): string | null {
    if (!account.admin) return null;
    if (account.bootstrap) return `${BOOTSTRAP} and stays an administrator.`;
    if (isSelf(account)) return 'You cannot remove your own administrator role.';
    if (adminCount() <= 1) return 'At least one administrator is required.';
    return null;
  }

  /** Why the API would refuse to delete the account, or null. */
  function deleteBlocked(account: User): string | null {
    if (account.bootstrap) return `${BOOTSTRAP} and cannot be deleted.`;
    if (isSelf(account)) return 'You cannot delete your own account.';
    return null;
  }

  /** Runs a row action while `busy` is set, then reloads the accounts. A 404 means the account is already gone. */
  async function act(account: User, action: () => Promise<string>, failed: string, unreachable: string) {
    setBusy((draft) => {
      draft[account.username] = true;
    });
    try {
      admin.notify(await action());
    } catch (error) {
      if (error instanceof SessionExpired) return;
      if (statusOf(error) === 404) admin.notify(`User ${account.username} no longer exists.`, 'error');
      else admin.notify(describe(error, failed, unreachable), 'error');
    } finally {
      setBusy((draft) => {
        delete draft[account.username];
      });
      refresh(users);
    }
  }

  function toggleAdmin(account: User) {
    const promote = !account.admin;
    void act(
      account,
      async () => {
        await call(`${userPath(account.username)}/admin`, { method: 'PUT', json: { admin: promote } });
        return promote
          ? `${account.username} is now an administrator.`
          : `${account.username} is no longer an administrator.`;
      },
      'Could not change the role',
      'Could not reach the server, the role was not changed.',
    );
  }

  async function remove(account: User) {
    const confirmed = await confirm({
      title: `Delete user ${account.username}?`,
      body: 'The account is removed and its sessions end immediately. The requests it submitted are kept.',
      action: 'Delete user',
    });
    if (!confirmed) return;
    await act(
      account,
      async () => {
        await call(userPath(account.username), { method: 'DELETE' });
        return `User ${account.username} deleted.`;
      },
      'Could not delete the user',
      'Could not reach the server, the user was not deleted.',
    );
  }

  return (
    <>
      <Title>Users · NoHorny admin</Title>
      <Meta name="robots" content="noindex, nofollow" />
      <div class="flex items-center justify-between gap-4">
        <div class="flex flex-col gap-1">
          <h1 class="text-3xl">Users</h1>
          <Loading fallback={<p class="text-ink-3 text-sm">&nbsp;</p>}>
            <p class="text-ink-3 text-sm">
              {plural(users().length, 'account')}, {plural(adminCount(), 'administrator')}
            </p>
          </Loading>
        </div>
        <Button variant="primary" size="icon" aria-label="New user" title="New user" onClick={() => setCreating(true)}>
          <PlusIcon />
        </Button>
      </div>
      <section class="card overflow-hidden" aria-label="Accounts">
        <Errored
          fallback={(error, reset) => (
            <Show when={!(error() instanceof SessionExpired)}>
              <State title="Could not load the users" tone="error">
                <p class="text-ink-2">{describe(error(), 'The server answered with an error')}</p>
                <Button onClick={() => reset()}>Retry</Button>
              </State>
            </Show>
          )}>
          <Loading
            fallback={
              <div class="grid h-40 place-items-center text-ink-3">
                <Spinner class="size-5" />
              </div>
            }>
            <ul class="divide-y divide-line">
              <For each={users()} keyed={(account) => account.username}>
                {(account) => (
                  <li class="grid grid-cols-[2.5rem_minmax(0,1fr)] items-center gap-x-4 gap-y-3 px-4 py-5 sm:grid-cols-[2.5rem_minmax(0,1fr)_auto] sm:px-6">
                    <span
                      aria-hidden="true"
                      class="grid size-10 shrink-0 place-items-center rounded-full border border-line bg-surface-2 text-ink-2 uppercase">
                      {account().username[0]}
                    </span>
                    <div class="flex min-w-0 flex-col gap-1.5">
                      <span class="flex flex-wrap items-center gap-x-2 gap-y-1.5">
                        <span class="[overflow-wrap:anywhere]">{account().username}</span>
                        <Show when={isSelf(account())}>
                          <Chip>you</Chip>
                        </Show>
                        <Show when={account().admin}>
                          <Chip class="border-line-strong text-ink">admin</Chip>
                        </Show>
                        <Show when={account().bootstrap}>
                          <Chip title={`${BOOTSTRAP}.`}>bootstrap</Chip>
                        </Show>
                      </span>
                      <span class="text-ink-3 text-xs">
                        Created {formatDate(account().createdAt)} · {account().rateLimit.toLocaleString('en')} per
                        minute
                      </span>
                    </div>
                    <div class="col-start-2 -ml-3 flex flex-wrap gap-1 sm:col-start-3 sm:ml-0">
                      <Button
                        size="sm"
                        variant="quiet"
                        disabled={busy[account().username] || (account().admin && demoteBlocked(account()) !== null)}
                        title={
                          account().admin
                            ? (demoteBlocked(account()) ?? 'Remove the administrator role')
                            : 'Make administrator'
                        }
                        onClick={() => toggleAdmin(account())}>
                        <Show when={account().admin} fallback={<ShieldPlusIcon />}>
                          <ShieldMinusIcon />
                        </Show>
                        {account().admin ? 'Demote' : 'Promote'}
                      </Button>
                      <Button
                        size="sm"
                        variant="quiet"
                        disabled={busy[account().username] || account().bootstrap}
                        title={
                          account().bootstrap
                            ? `${BOOTSTRAP}, change its password in the configuration.`
                            : 'Set a new password'
                        }
                        onClick={() => setEditing(account())}>
                        <KeyIcon />
                        Password
                      </Button>
                      <Button
                        size="sm"
                        variant="quiet"
                        disabled={busy[account().username]}
                        title="Change the rate limit"
                        onClick={() => setLimiting(account())}>
                        <GaugeIcon />
                        Limit
                      </Button>
                      <Button
                        size="icon"
                        variant="quiet-danger"
                        aria-label={`Delete ${account().username}`}
                        title={deleteBlocked(account()) ?? `Delete ${account().username}`}
                        disabled={busy[account().username] || deleteBlocked(account()) !== null}
                        onClick={() => remove(account())}>
                        <TrashIcon />
                      </Button>
                    </div>
                  </li>
                )}
              </For>
            </ul>
          </Loading>
        </Errored>
      </section>

      <CreateUserDialog open={creating()} onClose={() => setCreating(false)} onCreated={() => refresh(users)} />
      <PasswordDialog account={editing()} onClose={() => setEditing(null)} />
      <RateLimitDialog account={limiting()} onClose={() => setLimiting(null)} onChanged={() => refresh(users)} />
    </>
  );
}

function CreateUserDialog(props: { open: boolean; onClose: () => void; onCreated: () => void }) {
  return (
    <Dialog open={props.open} onClose={props.onClose} label="New user">
      <CreateUserForm onClose={props.onClose} onCreated={props.onCreated} />
    </Dialog>
  );
}

/** The dialog renders its content only while open, so every opening starts from an empty form. */
function CreateUserForm(props: { onClose: () => void; onCreated: () => void }) {
  const admin = useContext(AdminContext);
  const [form, setForm] = createStore({ username: '', password: '', admin: false, rateLimit: '' });
  /** The fields to check, the ones left once and all of them after a submit. */
  const [touched, setTouched] = createStore({ username: false, password: false, rateLimit: false });
  const [busy, setBusy] = createSignal(false);
  const [error, setError] = createSignal<string | null>(null);
  let usernameInput!: HTMLInputElement;
  let passwordInput!: HTMLInputElement;
  let rateLimitInput!: HTMLInputElement;

  const usernameProblem = () => {
    if (!form.username) return 'Enter a username';
    return USERNAME_RULE.test(form.username) ? null : USERNAME_HINT;
  };
  const passwordProblem = () => {
    if (!form.password) return 'Enter a password';
    return form.password.length >= PASSWORD_LENGTH ? null : `Use at least ${PASSWORD_LENGTH} characters`;
  };

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    touch('username');
    touch('password');
    touch('rateLimit');
    if (usernameProblem()) return usernameInput.focus();
    if (passwordProblem()) return passwordInput.focus();
    if (rateLimitProblem(form.rateLimit, true)) return rateLimitInput.focus();
    setBusy(true);
    setError(null);
    try {
      const { rateLimit, ...fields } = form;
      const json = rateLimit.trim() ? { ...fields, rateLimit: Number(rateLimit) } : fields;
      const account = await call<User>('/api/users', { method: 'POST', json });
      admin.notify(`User ${account.username} created${account.admin ? ' as an administrator' : ''}.`);
      props.onClose();
      props.onCreated();
    } catch (failure) {
      if (failure instanceof SessionExpired) return;
      if (statusOf(failure) === 409) {
        setError(`The username ${form.username} is already taken, choose another one.`);
        usernameInput.focus();
      } else {
        setError(
          describe(failure, 'Could not create the user', 'Could not reach the server, the user was not created.'),
        );
      }
    } finally {
      setBusy(false);
    }
  }

  function touch(key: 'username' | 'password' | 'rateLimit') {
    setTouched((draft) => {
      draft[key] = true;
    });
  }

  return (
    <form class="flex flex-col gap-4 p-6" novalidate onSubmit={submit}>
      <h2 class="text-xl">New user</h2>
      <Field
        ref={usernameInput}
        label="Username"
        hint={USERNAME_HINT}
        error={touched.username ? usernameProblem() : null}
        autofocus
        autocomplete="off"
        autocapitalize="none"
        spellcheck={false}
        value={form.username}
        onInput={(event) =>
          setForm((draft) => {
            draft.username = event.currentTarget.value;
          })
        }
        onBlur={(event) => event.currentTarget.value && touch('username')}
      />
      <Field
        ref={passwordInput}
        label="Password"
        type="password"
        hint={`At least ${PASSWORD_LENGTH} characters`}
        error={touched.password ? passwordProblem() : null}
        autocomplete="new-password"
        value={form.password}
        onInput={(event) =>
          setForm((draft) => {
            draft.password = event.currentTarget.value;
          })
        }
        onBlur={(event) => event.currentTarget.value && touch('password')}
      />
      <Field
        ref={rateLimitInput}
        label="Rate limit"
        type="number"
        inputmode="numeric"
        min={1}
        max={MAX_RATE_LIMIT}
        placeholder="Server default"
        hint={`${RATE_LIMIT_HINT}, blank for the server default`}
        error={touched.rateLimit ? rateLimitProblem(form.rateLimit, true) : null}
        value={form.rateLimit}
        onInput={(event) =>
          setForm((draft) => {
            draft.rateLimit = event.currentTarget.value;
          })
        }
        onBlur={(event) => event.currentTarget.value && touch('rateLimit')}
      />
      <label class="flex items-start gap-3 text-sm">
        <input
          type="checkbox"
          class="mt-0.5 size-4 shrink-0"
          checked={form.admin}
          onChange={(event) =>
            setForm((draft) => {
              draft.admin = event.currentTarget.checked;
            })
          }
        />
        <span class="flex flex-col gap-0.5">
          Administrator
          <span class="text-ink-3 text-xs">Gets access to this admin panel.</span>
        </span>
      </label>
      <Show when={error()}>
        <p role="alert" class="text-danger text-sm">
          {error()}
        </p>
      </Show>
      <div class="mt-2 flex justify-end gap-2">
        <Button onClick={() => props.onClose()}>Cancel</Button>
        <Button type="submit" variant="primary" busy={busy()}>
          Create user
        </Button>
      </div>
    </form>
  );
}

function PasswordDialog(props: { account: User | null; onClose: () => void }) {
  const admin = useContext(AdminContext);
  const [busy, setBusy] = createSignal(false);
  const [problem, setProblem] = createSignal<string | null>(null);
  const [error, setError] = createSignal<string | null>(null);

  function close() {
    setError(null);
    setProblem(null);
    props.onClose();
  }

  async function submit(event: SubmitEvent, account: User) {
    event.preventDefault();
    const password = String(new FormData(event.currentTarget as HTMLFormElement).get('password'));
    if (password.length < PASSWORD_LENGTH) {
      setProblem(password ? `Use at least ${PASSWORD_LENGTH} characters` : 'Enter a password');
      (event.currentTarget as HTMLFormElement).password.focus();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await call(`${userPath(account.username)}/password`, { method: 'PUT', json: { password } });
      props.onClose();
      // The server ends the sessions of an account whose password changed, the current session included
      if (account.username === session.session?.username) {
        expire('Your password was changed, sign in with the new one.');
        return;
      }
      admin.notify(`Password of ${account.username} changed.`);
    } catch (failure) {
      if (failure instanceof SessionExpired) return;
      if (statusOf(failure) === 404) {
        props.onClose();
        admin.notify(`User ${account.username} no longer exists.`, 'error');
      } else {
        setError(
          describe(
            failure,
            'Could not change the password',
            'Could not reach the server, the password was not changed.',
          ),
        );
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={props.account !== null} onClose={close} label="Set a password">
      <Show when={props.account}>
        {(account) => (
          <form class="flex flex-col gap-4 p-6" novalidate onSubmit={(event) => submit(event, account())}>
            <div class="flex flex-col gap-1">
              <h2 class="text-xl [overflow-wrap:anywhere]">Set the password of {account().username}</h2>
              <p class="text-ink-2 text-sm">
                {account().username === session.session?.username
                  ? 'You will be signed out and sign in again with the new password.'
                  : 'Their sessions end and they sign in again with the new password.'}
              </p>
            </div>
            <Field
              label="New password"
              name="password"
              type="password"
              hint={`At least ${PASSWORD_LENGTH} characters`}
              error={problem()}
              autofocus
              autocomplete="new-password"
              onInput={() => setProblem(null)}
            />
            <Show when={error()}>
              <p role="alert" class="text-danger text-sm">
                {error()}
              </p>
            </Show>
            <div class="mt-2 flex justify-end gap-2">
              <Button onClick={close}>Cancel</Button>
              <Button type="submit" variant="primary" busy={busy()}>
                Save password
              </Button>
            </div>
          </form>
        )}
      </Show>
    </Dialog>
  );
}

function RateLimitDialog(props: { account: User | null; onClose: () => void; onChanged: () => void }) {
  const admin = useContext(AdminContext);
  const [busy, setBusy] = createSignal(false);
  const [problem, setProblem] = createSignal<string | null>(null);
  const [error, setError] = createSignal<string | null>(null);

  function close() {
    setError(null);
    setProblem(null);
    props.onClose();
  }

  async function submit(event: SubmitEvent, account: User) {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const value = String(new FormData(form).get('rateLimit'));
    const invalid = rateLimitProblem(value, false);
    if (invalid) {
      setProblem(invalid);
      form.rateLimit.focus();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const rateLimit = Number(value);
      await call(`${userPath(account.username)}/rate-limit`, { method: 'PUT', json: { rateLimit } });
      close();
      admin.notify(`${account.username} is now limited to ${rateLimit.toLocaleString('en')} per minute.`);
      props.onChanged();
    } catch (failure) {
      if (failure instanceof SessionExpired) return;
      if (statusOf(failure) === 404) {
        close();
        admin.notify(`User ${account.username} no longer exists.`, 'error');
        props.onChanged();
      } else {
        setError(
          describe(failure, 'Could not change the limit', 'Could not reach the server, the limit was not changed.'),
        );
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={props.account !== null} onClose={close} label="Change the rate limit">
      <Show when={props.account}>
        {(account) => (
          <form class="flex flex-col gap-4 p-6" novalidate onSubmit={(event) => submit(event, account())}>
            <div class="flex flex-col gap-1">
              <h2 class="text-xl [overflow-wrap:anywhere]">Rate limit of {account().username}</h2>
              <p class="text-ink-2 text-sm">Applies from their next classification, their sessions are kept.</p>
            </div>
            <Field
              label="Rate limit"
              name="rateLimit"
              type="number"
              inputmode="numeric"
              min={1}
              max={MAX_RATE_LIMIT}
              hint={RATE_LIMIT_HINT}
              error={problem()}
              value={account().rateLimit}
              autofocus
              onInput={() => setProblem(null)}
            />
            <Show when={error()}>
              <p role="alert" class="text-danger text-sm">
                {error()}
              </p>
            </Show>
            <div class="mt-2 flex justify-end gap-2">
              <Button onClick={close}>Cancel</Button>
              <Button type="submit" variant="primary" busy={busy()}>
                Save limit
              </Button>
            </div>
          </form>
        )}
      </Show>
    </Dialog>
  );
}
