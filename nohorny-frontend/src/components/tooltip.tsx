// Any element with a data-tooltip attribute gets a tooltip, shown at once unlike the native title that waits a second.
// With data-tooltip-overflow, it only shows when the text is cut. It sits in the top layer, so it shows over dialogs.

import { onSettled } from 'solid-js';

/** The gap between the element and its tooltip. */
const ANCHOR_GAP = 6;

/** The closest the tooltip comes to the edges of the window. */
const EDGE_MARGIN = 6;

export function TooltipHost() {
  let tip!: HTMLDivElement;
  let target: HTMLElement | null = null;
  // Created once mounted, the page is also rendered on the server
  let observer: MutationObserver | undefined;
  // Identifies the last fade out, a newer show or hide cancels the leaving of the older ones
  let fade = 0;

  function place() {
    if (target === null) return;
    tip.textContent = target.dataset.tooltip ?? '';
    // Measured from the left edge, so the last position does not squeeze the text against the right one
    tip.style.left = '0px';
    const anchor = target.getBoundingClientRect();
    const { width, height } = tip.getBoundingClientRect();
    const left = Math.min(
      Math.max(EDGE_MARGIN, anchor.left + anchor.width / 2 - width / 2),
      document.documentElement.clientWidth - width - EDGE_MARGIN,
    );
    const above = anchor.top - height - ANCHOR_GAP;
    tip.style.left = `${left}px`;
    tip.style.top = `${above >= EDGE_MARGIN ? above : anchor.bottom + ANCHOR_GAP}px`;
  }

  function show(element: HTMLElement) {
    hide();
    target = element;
    observer?.observe(element, { attributeFilter: ['data-tooltip'] });
    // Read out as a description, unless it repeats the label of an icon button
    if (element.dataset.tooltip !== element.getAttribute('aria-label'))
      element.setAttribute('aria-describedby', tip.id);
    fade++;
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
    const leaving = ++fade;
    // The animations are known once the style is recalculated, which getAnimations() forces
    Promise.allSettled(tip.getAnimations().map((animation) => animation.finished)).then(() => {
      if (leaving === fade) tip.hidePopover();
    });
  }

  const owner = (node: EventTarget | null) => {
    const element = node instanceof Element ? node.closest<HTMLElement>('[data-tooltip]:not([data-tooltip=""])') : null;
    if (element?.hasAttribute('data-tooltip-overflow') && element.scrollWidth <= element.clientWidth) return null;
    return element;
  };

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
      fade++;
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
      class="pointer-events-none fixed inset-auto m-0 max-w-72 whitespace-pre-line rounded-lg bg-ink px-2.5 py-1 text-bg text-sm leading-snug shadow-float [overflow-wrap:anywhere]"
    />
  );
}
