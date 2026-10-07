// Modal dialogs built on <dialog>, and the confirmation dialog of the app.

import type { JSX } from '@solidjs/web';
import { createEffect, createSignal, onSettled, Show } from 'solid-js';
import { Button } from './ui';

/** A modal <dialog>, shown while `open`. Escape and a click on the backdrop call `onClose`. */
export function Dialog(props: {
  open: boolean;
  onClose: () => void;
  label: string;
  children: JSX.Element;
  class?: string;
  /** Pins the dialog below the navigation bar instead of centering it, so a change of its height does not move its controls. */
  top?: boolean;
  onKeyDown?: (event: KeyboardEvent) => void;
}) {
  let dialog!: HTMLDialogElement;
  createEffect(
    () => props.open,
    (open) => {
      if (open && !dialog.open) dialog.showModal();
      else if (!open && dialog.open) dialog.close();
    },
  );
  onSettled(() => () => dialog.open && dialog.close());
  return (
    <dialog
      ref={dialog}
      aria-label={props.label}
      class={[
        'overflow-y-auto overscroll-contain rounded-2xl border border-line bg-surface p-0 text-ink',
        props.top ? 'mx-auto mt-20 mb-auto max-h-[calc(100dvh-7rem)]' : 'm-auto max-h-[calc(100dvh-2rem)]',
        props.class ?? 'w-[min(28rem,calc(100vw-2rem))]',
      ]}
      onCancel={(event) => {
        event.preventDefault();
        props.onClose();
      }}
      onClick={(event) => event.target === dialog && props.onClose()}
      onKeyDown={(event) => props.onKeyDown?.(event)}>
      <Show when={props.open}>{props.children}</Show>
    </dialog>
  );
}

interface Confirmation {
  title: string;
  body: string;
  action: string;
  resolve: (confirmed: boolean) => void;
}

const [pending, setPending] = createSignal<Confirmation | null>(null);
// The pending confirmation. A write to the signal shows only after the next flush, but this variable changes at once
let current: Confirmation | null = null;

/** Asks the user to confirm a destructive action. Resolves to true if they confirm. */
export function confirm(options: { title: string; body: string; action: string }): Promise<boolean> {
  current?.resolve(false);
  return new Promise((resolve) => {
    current = { ...options, resolve };
    setPending(current);
  });
}

function settle(confirmed: boolean) {
  current?.resolve(confirmed);
  current = null;
  setPending(null);
}

/** Renders the confirmation dialog. App.tsx mounts it once. */
export function ConfirmHost() {
  return (
    <Dialog open={pending() !== null} onClose={() => settle(false)} label={pending()?.title ?? 'Confirm'}>
      <div class="flex flex-col gap-3 p-6">
        <h2 class="text-xl">{pending()?.title}</h2>
        <p class="text-ink-2">{pending()?.body}</p>
        <div class="mt-3 flex justify-end gap-2">
          <Button autofocus onClick={() => settle(false)}>
            Cancel
          </Button>
          <Button variant="solid-danger" onClick={() => settle(true)}>
            {pending()?.action}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}
