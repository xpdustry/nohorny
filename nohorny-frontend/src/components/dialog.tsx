import type { JSX } from '@solidjs/web';
import { createEffect, createSignal, onSettled, Show } from 'solid-js';
import { Button } from './ui';

export function Dialog(props: {
  open: boolean;
  onClose: () => void;
  label: string;
  /** Rendered only while the dialog is open, so every opening mounts them afresh, forms included. */
  children: JSX.Element;
  class?: string;
  /** Pins the dialog below the navigation bar instead of centering it, so a change of its height does not move its controls. */
  pinned?: boolean;
  /** Receives the key events of this dialog only, not those of a dialog opened inside it. */
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
        props.pinned ? 'mx-auto mt-20 mb-auto max-h-[calc(100dvh-7rem)]' : 'm-auto max-h-[calc(100dvh-2rem)]',
        props.class ?? 'w-[min(28rem,calc(100vw-2rem))]',
      ]}
      onCancel={(event) => {
        event.preventDefault();
        props.onClose();
      }}
      onClick={(event) => event.target === dialog && props.onClose()}
      onKeyDown={(event) => {
        if ((event.target as Element).closest('dialog') === dialog) props.onKeyDown?.(event);
      }}>
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
