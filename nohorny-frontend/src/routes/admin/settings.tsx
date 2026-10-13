// The settings page of the admin panel, which manages the requester mappings.

import { Meta, Title } from '@solidjs/meta';
import {
  createMemo,
  createSignal,
  createStore,
  Errored,
  For,
  Match,
  onSettled,
  refresh,
  Show,
  Switch,
  useContext,
} from 'solid-js';
import { confirm, Dialog } from '../../components/dialog';
import {
  ArrowRightIcon,
  InfoIcon,
  PencilIcon,
  PlusIcon,
  RefreshIcon,
  SettingsIcon,
  TrashIcon,
} from '../../components/icon';
import { Button, buttonClass, Field, State } from '../../components/ui';
import { AdminContext } from '../../lib/admin';
import {
  describe,
  type Mapping,
  mappingPath,
  type Networks,
  type RequesterType,
  statusOf,
  type User,
} from '../../lib/api';
import { formatDate, requesterLabel } from '../../lib/format';
import { call, SessionExpired } from '../../lib/session';

/** The longest network name the API accepts. */
const MAX_NETWORK_LENGTH = 64;

const MAPPINGS_DESCRIPTION =
  'The requests sent without an account from these addresses are counted and rate-limited as another requester, ' +
  'such as your own servers in a Docker network. The most specific range wins.';

const RANGE_HINT = 'An address, or a range in CIDR notation such as 172.16.0.0/12';

const TYPES: { value: RequesterType; label: string; hint: string }[] = [
  {
    value: 'mindustry-network',
    label: 'Mindustry network',
    hint: 'Counted with the network, and shares its rate limit with the listed servers of the same name.',
  },
  { value: 'user', label: 'User', hint: 'Counted as the account, and limited by its rate limit.' },
  { value: 'local', label: 'Local', hint: 'Counted as local, limited per address like the anonymous clients.' },
  { value: 'anonymous', label: 'Anonymous', hint: 'Counted as anonymous, limited per address.' },
];

const plural = (count: number, noun: string) => `${count} ${noun}${count === 1 ? '' : 's'}`;

export default function Settings() {
  const admin = useContext(AdminContext);
  const mappings = createMemo(() => call<Mapping[]>('/api/mappings'));
  const [busy, setBusy] = createStore<Record<string, boolean>>({});
  const [editing, setEditing] = createSignal<Mapping | null>(null);
  const [creating, setCreating] = createSignal(false);

  /** Runs a row action while `busy` is set, then reloads the mappings. A 404 means the mapping is already gone. */
  async function act(mapping: Mapping, action: () => Promise<string>, failed: string, unreachable: string) {
    setBusy((draft) => {
      draft[mapping.id] = true;
    });
    try {
      admin.notify(await action());
    } catch (error) {
      if (error instanceof SessionExpired) return;
      if (statusOf(error) === 404) admin.notify(`The mapping of ${mapping.range} no longer exists.`, 'error');
      else admin.notify(describe(error, failed, unreachable), 'error');
    } finally {
      setBusy((draft) => {
        delete draft[mapping.id];
      });
      refresh(mappings);
    }
  }

  async function apply(mapping: Mapping) {
    const confirmed = await confirm({
      title: `Apply ${mapping.range} to past requests?`,
      body:
        `The retained requests sent from this range without an account become ${requesterLabel(mapping)}, ` +
        'unless a more specific range contains their address. Their daily statistics move with them. ' +
        'The statistics of the requests already deleted keep their requester.',
      action: 'Apply',
    });
    if (!confirmed) return;
    await act(
      mapping,
      async () => {
        const { requests } = await call<{ requests: number }>(`${mappingPath(mapping.id)}/apply`, { method: 'POST' });
        return requests === 0
          ? `No past request of ${mapping.range} to change.`
          : `${plural(requests, 'request')} of ${mapping.range} changed to ${requesterLabel(mapping)}.`;
      },
      'Could not apply the mapping',
      'Could not reach the server, the requests were not changed.',
    );
  }

  async function remove(mapping: Mapping) {
    const confirmed = await confirm({
      title: `Delete the mapping of ${mapping.range}?`,
      body: 'The next requests of this range are identified as usual. The requests it already changed keep their requester.',
      action: 'Delete mapping',
    });
    if (!confirmed) return;
    await act(
      mapping,
      async () => {
        await call(mappingPath(mapping.id), { method: 'DELETE' });
        return `Mapping of ${mapping.range} deleted.`;
      },
      'Could not delete the mapping',
      'Could not reach the server, the mapping was not deleted.',
    );
  }

  return (
    <>
      <Title>Settings · NoHorny admin</Title>
      <Meta name="robots" content="noindex, nofollow" />
      <h1 class="text-3xl">Settings</h1>
      <section class="flex flex-col gap-4" aria-labelledby="mappings-title">
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-1">
            <h2 id="mappings-title" class="text-xl">
              Requester mappings
            </h2>
            <button
              type="button"
              class={buttonClass('quiet', 'icon')}
              aria-label="About requester mappings"
              aria-describedby="mappings-description"
              data-tooltip={MAPPINGS_DESCRIPTION}>
              <InfoIcon />
            </button>
            <p id="mappings-description" class="sr-only">
              {MAPPINGS_DESCRIPTION}
            </p>
          </div>
          <Button
            variant="primary"
            size="icon"
            aria-label="New mapping"
            data-tooltip="New mapping"
            onClick={() => setCreating(true)}>
            <PlusIcon />
          </Button>
        </div>
        <div class="card overflow-hidden">
          <Errored
            fallback={(error, reset) => (
              <Show when={!(error() instanceof SessionExpired)}>
                <State title="Could not load the mappings" tone="error">
                  <p class="text-ink-2">{describe(error(), 'The server answered with an error')}</p>
                  <Button onClick={() => reset()}>Retry</Button>
                </State>
              </Show>
            )}>
            <Show
              when={mappings().length > 0}
              fallback={
                <State icon={<SettingsIcon />} title="No mappings">
                  <p class="text-ink-2">The requests are identified by their account and address.</p>
                </State>
              }>
              <ul class="divide-y divide-line">
                <For each={mappings()} keyed={(mapping) => mapping.id}>
                  {(mapping) => (
                    <li class="grid grid-cols-1 items-center gap-x-4 gap-y-3 px-4 py-5 sm:grid-cols-[minmax(0,1fr)_auto] sm:px-6">
                      <div class="flex min-w-0 flex-col gap-1.5">
                        <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                          <code class="font-mono text-sm [overflow-wrap:anywhere]">{mapping().range}</code>
                          <ArrowRightIcon class="size-4 shrink-0 text-ink-3" />
                          <span class="[overflow-wrap:anywhere]">{requesterLabel(mapping())}</span>
                        </span>
                        <span class="text-ink-3 text-xs">Created {formatDate(mapping().createdAt)}</span>
                      </div>
                      <div class="-ml-3 flex flex-wrap gap-1 sm:ml-0">
                        <Button
                          size="sm"
                          variant="quiet"
                          disabled={busy[mapping().id]}
                          data-tooltip="Change the retained requests of this range"
                          onClick={() => void apply(mapping())}>
                          <RefreshIcon />
                          Apply
                        </Button>
                        <Button
                          size="sm"
                          variant="quiet"
                          disabled={busy[mapping().id]}
                          data-tooltip="Edit the mapping"
                          onClick={() => setEditing(mapping())}>
                          <PencilIcon />
                          Edit
                        </Button>
                        <Button
                          size="icon"
                          variant="quiet-danger"
                          aria-label={`Delete the mapping of ${mapping().range}`}
                          data-tooltip="Delete the mapping"
                          disabled={busy[mapping().id]}
                          onClick={() => void remove(mapping())}>
                          <TrashIcon />
                        </Button>
                      </div>
                    </li>
                  )}
                </For>
              </ul>
            </Show>
          </Errored>
        </div>
      </section>

      <Dialog
        open={creating() || editing() !== null}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        label={editing() ? 'Edit mapping' : 'New mapping'}>
        <MappingForm
          mapping={editing()}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSaved={() => refresh(mappings)}
        />
      </Dialog>
    </>
  );
}

/** The dialog renders its content only while open, so every opening starts from the mapping or an empty form. */
function MappingForm(props: { mapping: Mapping | null; onClose: () => void; onSaved: () => void }) {
  const admin = useContext(AdminContext);
  const initial = props.mapping;
  const [form, setForm] = createStore({
    range: initial?.range ?? '',
    type: initial?.type ?? ('mindustry-network' as RequesterType),
    network: initial?.type === 'mindustry-network' ? (initial.name ?? '') : '',
    user: initial?.type === 'user' ? (initial.name ?? '') : '',
  });
  const [touched, setTouched] = createStore({ range: false, name: false });
  const [busy, setBusy] = createSignal(false);
  const [error, setError] = createSignal<string | null>(null);
  // Suggestions only, the form works without them
  const [users, setUsers] = createSignal<string[]>([]);
  const [networks, setNetworks] = createSignal<string[]>([]);
  call<User[]>('/api/users').then(
    (list) => setUsers(list.map((account) => account.username).toSorted()),
    () => {},
  );
  call<Networks>('/api/stats/networks?range=90d').then(
    (stats) => setNetworks(stats.networks.map((network) => network.name)),
    () => {},
  );
  let rangeInput!: HTMLInputElement;
  let nameInput!: HTMLInputElement | HTMLSelectElement;

  const rangeProblem = () => (form.range.trim() ? null : 'Enter an address or a range');
  const nameProblem = () => {
    if (form.type === 'user') return form.user ? null : 'Choose a user';
    if (form.type !== 'mindustry-network') return null;
    const name = form.network.trim();
    if (!name) return 'Enter a network name';
    return name.length <= MAX_NETWORK_LENGTH ? null : `Use at most ${MAX_NETWORK_LENGTH} characters`;
  };

  // Closed while saving, the outcome must not close or edit the form opened next. The list still refreshes
  let open = true;
  onSettled(() => () => {
    open = false;
  });

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    setTouched((draft) => {
      draft.range = true;
      draft.name = true;
    });
    if (rangeProblem()) return rangeInput.focus();
    if (nameProblem()) return nameInput.focus();
    setBusy(true);
    setError(null);
    const name = form.type === 'user' ? form.user : form.type === 'mindustry-network' ? form.network.trim() : null;
    const json = { range: form.range.trim(), type: form.type, name };
    try {
      const saved = initial
        ? await call<Mapping>(mappingPath(initial.id), { method: 'PUT', json })
        : await call<Mapping>('/api/mappings', { method: 'POST', json });
      admin.notify(`${saved.range} is now mapped to ${requesterLabel(saved)}.`);
      if (open) props.onClose();
      props.onSaved();
    } catch (failure) {
      if (failure instanceof SessionExpired || !open) return;
      const status = statusOf(failure);
      if (status === 409) {
        setError(`${json.range} is already mapped, edit its mapping instead.`);
        rangeInput.focus();
      } else if (status === 404 && initial) {
        props.onClose();
        props.onSaved();
        admin.notify(`The mapping of ${initial.range} no longer exists.`, 'error');
      } else {
        setError(
          describe(failure, 'Could not save the mapping', 'Could not reach the server, the mapping was not saved.'),
        );
      }
    } finally {
      setBusy(false);
    }
  }

  const typeHint = () => TYPES.find((type) => type.value === form.type)?.hint;

  return (
    <form class="flex flex-col gap-4 p-6" novalidate onSubmit={submit}>
      <h2 class="text-xl">{initial ? 'Edit mapping' : 'New mapping'}</h2>
      <Field
        ref={rangeInput}
        label="Addresses"
        hint={RANGE_HINT}
        error={touched.range ? rangeProblem() : null}
        autofocus
        autocomplete="off"
        autocapitalize="none"
        spellcheck={false}
        class="font-mono"
        placeholder="172.18.0.0/16"
        value={form.range}
        onInput={(event) =>
          setForm((draft) => {
            draft.range = event.currentTarget.value;
          })
        }
        onBlur={(event) =>
          event.currentTarget.value &&
          setTouched((draft) => {
            draft.range = true;
          })
        }
      />
      <div class="flex flex-col gap-1.5">
        <label for="mapping-type" class="text-ink-2 text-sm">
          Counted as
        </label>
        <select
          id="mapping-type"
          class="field"
          aria-describedby="mapping-type-note"
          value={form.type}
          onChange={(event) =>
            setForm((draft) => {
              draft.type = event.currentTarget.value as RequesterType;
            })
          }>
          <For each={TYPES}>{(type) => <option value={type.value}>{type.label}</option>}</For>
        </select>
        <p id="mapping-type-note" class="text-ink-3 text-xs">
          {typeHint()}
        </p>
      </div>
      <Switch>
        <Match when={form.type === 'mindustry-network'}>
          <Field
            ref={(element) => {
              nameInput = element;
            }}
            label="Network"
            hint="A listed network to join it, or any other name"
            error={touched.name ? nameProblem() : null}
            list="mapping-networks"
            autocomplete="off"
            maxlength={MAX_NETWORK_LENGTH}
            placeholder="Chaotic Neutral"
            value={form.network}
            onInput={(event) =>
              setForm((draft) => {
                draft.network = event.currentTarget.value;
              })
            }
          />
          <datalist id="mapping-networks">
            <For each={networks()}>{(network) => <option value={network} />}</For>
          </datalist>
        </Match>
        <Match when={form.type === 'user'}>
          <div class="flex flex-col gap-1.5">
            <label for="mapping-user" class="text-ink-2 text-sm">
              User
            </label>
            <select
              ref={(element) => {
                nameInput = element;
              }}
              id="mapping-user"
              class="field"
              aria-invalid={touched.name && nameProblem() ? 'true' : undefined}
              aria-describedby="mapping-user-note"
              value={form.user}
              onChange={(event) =>
                setForm((draft) => {
                  draft.user = event.currentTarget.value;
                })
              }>
              <option value="" disabled>
                Choose a user
              </option>
              <For each={users()}>
                {(username) => (
                  <option value={username} selected={username === form.user}>
                    {username}
                  </option>
                )}
              </For>
            </select>
            <p id="mapping-user-note" class={['text-xs', touched.name && nameProblem() ? 'text-danger' : 'text-ink-3']}>
              {(touched.name && nameProblem()) || 'The mapping is deleted with the account'}
            </p>
          </div>
        </Match>
      </Switch>
      <Show when={error()}>
        <p role="alert" class="text-danger text-sm">
          {error()}
        </p>
      </Show>
      <div class="mt-2 flex justify-end gap-2">
        <Button onClick={() => props.onClose()}>Cancel</Button>
        <Button type="submit" variant="primary" busy={busy()}>
          {initial ? 'Save mapping' : 'Create mapping'}
        </Button>
      </div>
    </form>
  );
}
