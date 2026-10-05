// The page scrollbar, drawn over the content so the layout keeps its width whether the page scrolls or not.
// App.css hides the native one. The thumb shows while scrolling and while the pointer is over the right edge.

import { createSignal, onSettled, Show } from 'solid-js';

/** The shortest thumb, in pixels, so it stays easy to grab on long pages. */
const MIN_THUMB = 32;

/** How long the thumb stays visible after the page stops scrolling, in milliseconds. */
const LINGER_MILLIS = 900;

export function OverlayScrollbar() {
  // A measure equal to the last one does not notify, so an unchanged page does not redraw the thumb
  const [thumb, setThumb] = createSignal<{ top: number; height: number } | null>(null, {
    equals: (previous, next) => previous?.top === next?.top && previous?.height === next?.height,
  });
  const [active, setActive] = createSignal(false);
  const [dragging, setDragging] = createSignal(false);
  let hide: ReturnType<typeof setTimeout> | undefined;

  const metrics = () => {
    const root = document.documentElement;
    const view = root.clientHeight;
    const height = Math.max(MIN_THUMB, (view * view) / root.scrollHeight);
    return { root, view, height, scrollable: root.scrollHeight - view, travel: view - height };
  };

  function update() {
    const { root, height, scrollable, travel } = metrics();
    setThumb(scrollable < 1 ? null : { top: (root.scrollTop / scrollable) * travel, height });
  }

  function flash() {
    setActive(true);
    clearTimeout(hide);
    hide = setTimeout(() => setActive(false), LINGER_MILLIS);
  }

  onSettled(() => {
    const onScroll = () => {
      update();
      flash();
    };
    const observer = new ResizeObserver(update);
    observer.observe(document.body);
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', update);
    // The observer measures the page once it starts observing, so the first layout read is not part of this scope
    return () => {
      observer.disconnect();
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', update);
      clearTimeout(hide);
    };
  });

  /** Drags the thumb, the page follows the pointer. */
  function grab(event: PointerEvent) {
    event.preventDefault();
    const target = event.currentTarget as HTMLElement;
    const { root, scrollable, travel } = metrics();
    const startY = event.clientY;
    const startScroll = root.scrollTop;
    target.setPointerCapture(event.pointerId);
    setDragging(true);
    const move = (moved: PointerEvent) =>
      root.scrollTo({ top: startScroll + ((moved.clientY - startY) * scrollable) / travel, behavior: 'instant' });
    const release = () => {
      setDragging(false);
      target.removeEventListener('pointermove', move);
      target.removeEventListener('pointerup', release);
      target.removeEventListener('pointercancel', release);
    };
    target.addEventListener('pointermove', move);
    target.addEventListener('pointerup', release);
    target.addEventListener('pointercancel', release);
  }

  /** A click on the track scrolls a page towards the click, like the native scrollbar. */
  function page(event: MouseEvent) {
    const current = thumb();
    if (!current || event.target !== event.currentTarget) return;
    const { root, view } = metrics();
    root.scrollBy({ top: event.clientY < current.top ? -view * 0.9 : view * 0.9 });
  }

  return (
    <Show when={thumb() !== null}>
      <div
        aria-hidden="true"
        class="group fixed inset-y-0 right-0 z-50 w-3"
        onClick={page}
        onPointerEnter={() => setActive(true)}
        onPointerLeave={() => !dragging() && flash()}>
        <div
          class={[
            'absolute right-0.5 w-1.5 touch-none rounded-full bg-line-strong transition-[opacity,width,background-color] duration-200',
            'group-hover:w-2 group-hover:bg-ink-3',
            active() || dragging() ? 'opacity-100' : 'opacity-0',
            { 'w-2 bg-ink-3': dragging() },
          ]}
          style={{ top: `${thumb()?.top ?? 0}px`, height: `${thumb()?.height ?? 0}px` }}
          onPointerDown={grab}
        />
      </div>
    </Show>
  );
}
