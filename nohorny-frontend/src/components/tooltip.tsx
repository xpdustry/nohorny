// The tooltips of the app, shown at once on hover and on keyboard focus, unlike the native title that waits a second.
// Any element with a data-tooltip attribute gets one. A single host listens on the document and floats the text over
// the element, in the top layer so it also shows over the dialogs. It follows the attribute while shown, so a label
// changing under the pointer, like "Copied", updates in place.

import { onSettled } from 'solid-js';

/** The gap between the element and its tooltip, and the closest the tooltip comes to the edges of the window. */
const GAP = 6;

/** How long the tooltip fades out before leaving the top layer, in milliseconds. Matches the transition of App.css. */
const FADE_MILLIS = 120;

export function TooltipHost() {
  let tip!: HTMLDivElement;
  let target: HTMLElement | null = null;
  // Created once mounted, the page is also rendered on the server
  let observer: MutationObserver | undefined;
  let closing: ReturnType<typeof setTimeout> | undefined;

  /** Above the element and centered, below when there is no room above, kept inside the window. */
  function place() {
    if (target === null) return;
    tip.textContent = target.dataset.tooltip ?? '';
    // Measured from the left edge, so the last position does not squeeze the text against the right one
    tip.style.left = '0px';
    const anchor = target.getBoundingClientRect();
    const { width, height } = tip.getBoundingClientRect();
    const left = Math.min(
      Math.max(GAP, anchor.left + anchor.width / 2 - width / 2),
      document.documentElement.clientWidth - width - GAP,
    );
    const above = anchor.top - height - GAP;
    tip.style.left = `${left}px`;
    tip.style.top = `${above >= GAP ? above : anchor.bottom + GAP}px`;
  }

  function show(element: HTMLElement) {
    hide();
    target = element;
    observer?.observe(element, { attributeFilter: ['data-tooltip'] });
    // Read out as a description, unless it repeats the label of an icon button
    if (element.dataset.tooltip !== element.getAttribute('aria-label'))
      element.setAttribute('aria-describedby', tip.id);
    // A tooltip still fading out comes back from where it is
    clearTimeout(closing);
    if (!tip.matches(':popover-open')) tip.showPopover();
    tip.dataset.open = '';
    place();
  }

  function hide() {
    if (target === null) return;
    observer?.disconnect();
    target.removeAttribute('aria-describedby');
    target = null;
    // Faded out here rather than in CSS, Firefox does not animate a popover leaving the top layer
    delete tip.dataset.open;
    closing = setTimeout(() => tip.hidePopover(), FADE_MILLIS);
  }

  const owner = (node: EventTarget | null) =>
    node instanceof Element ? node.closest<HTMLElement>('[data-tooltip]:not([data-tooltip=""])') : null;

  onSettled(() => {
    observer = new MutationObserver(() => (target?.dataset.tooltip ? place() : hide()));
    // Touch has no hover, a tap would leave the tooltip behind
    const over = (event: PointerEvent) => {
      if (event.pointerType !== 'mouse') return;
      const element = owner(event.target);
      if (element === target) return;
      if (element === null) hide();
      else show(element);
    };
    const out = (event: PointerEvent) => event.relatedTarget === null && hide();
    const focus = (event: FocusEvent) => {
      const element = owner(event.target);
      if (element?.matches(':focus-visible')) show(element);
    };
    const blur = (event: FocusEvent) => owner(event.target) === target && hide();
    const key = (event: KeyboardEvent) => event.key === 'Escape' && hide();
    document.addEventListener('pointerover', over);
    document.addEventListener('pointerout', out);
    document.addEventListener('focusin', focus);
    document.addEventListener('focusout', blur);
    document.addEventListener('keydown', key);
    window.addEventListener('scroll', place, { capture: true, passive: true });
    window.addEventListener('resize', hide);
    return () => {
      hide();
      clearTimeout(closing);
      document.removeEventListener('pointerover', over);
      document.removeEventListener('pointerout', out);
      document.removeEventListener('focusin', focus);
      document.removeEventListener('focusout', blur);
      document.removeEventListener('keydown', key);
      window.removeEventListener('scroll', place, { capture: true });
      window.removeEventListener('resize', hide);
    };
  });

  return (
    <div
      ref={tip}
      id="tooltip"
      role="tooltip"
      popover="manual"
      class="pointer-events-none fixed inset-auto m-0 max-w-72 rounded-lg bg-ink px-2.5 py-1 text-bg text-sm leading-snug shadow-float [overflow-wrap:anywhere]"
    />
  );
}
