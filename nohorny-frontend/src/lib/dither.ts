// The background of the pages: a mesh of soft blobs, quantised to the --dither-* tones of the theme and
// ordered-dithered with an 8 by 8 Bayer matrix, the dither pixel artists use. It reads as a glow from afar and as pixel
// art up close. It is drawn at one canvas pixel per dither pixel and scaled up, so it only repaints on a resize or a
// change of theme.
//
// After the "Dithered mesh" of the xpdustry.com gradient lab, itself after grainient.supply and ditther
// (https://ditther.com). The ordered dither is Bryce Bayer's, from 1973.

/**
 * Paints the #dither canvas and repaints it on a resize or a change of theme.
 *
 * Document.tsx inlines its source in a script right after the canvas, so the background shows as the page is parsed
 * instead of after the hydration. It must stay self-contained: no imports and nothing from the module scope.
 */
export function installDither() {
  /** The size of a dither pixel, in CSS pixels. */
  const PIXEL = 4;
  /** How long a resize settles before the background repaints. A paint takes tens of milliseconds. */
  const RESIZE_MILLIS = 150;
  /** The tones, from the page background up to the brightest, read from these custom properties. */
  const TONES = ['--bg', '--dither-1', '--dither-2', '--dither-3'];
  /** The blobs: their centre in fractions of the viewport, their radius in fractions of its larger side, their weight. */
  const BLOBS = [
    [0.88, 0.12, 0.5, 1],
    [0.08, 0.92, 0.45, 0.75],
    [0.4, -0.05, 0.28, 0.45],
  ];

  const canvas = document.getElementById('dither');
  if (!(canvas instanceof HTMLCanvasElement)) return;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) return;

  // The thresholds of the 8 by 8 Bayer matrix: the bits of x ^ y and y interleaved, the lowest ones as the most
  // significant
  const bayer: number[] = [];
  for (let y = 0; y < 8; y++)
    for (let x = 0; x < 8; x++) {
      let value = 0;
      for (let bit = 0; bit < 3; bit++) value = (value << 2) | ((((x ^ y) >> bit) & 1) << 1) | ((y >> bit) & 1);
      bayer.push((value + 0.5) / 64);
    }

  /** Resolves a CSS colour, like oklch(), to its RGB channels through the canvas. */
  function rgb(ctx: CanvasRenderingContext2D, color: string) {
    ctx.clearRect(0, 0, 1, 1);
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, 1, 1);
    return ctx.getImageData(0, 0, 1, 1).data;
  }

  function paint() {
    if (!(canvas instanceof HTMLCanvasElement) || !context) return;
    const style = getComputedStyle(document.documentElement);
    const colors = TONES.map((tone) => style.getPropertyValue(tone).trim());
    // The stylesheet is not applied yet, as with the dev server injecting it from a module
    if (colors.some((color) => color === '')) return void window.addEventListener('load', paint, { once: true });
    const width = Math.ceil(window.innerWidth / PIXEL);
    const height = Math.ceil(window.innerHeight / PIXEL);
    canvas.width = width;
    canvas.height = height;
    const palette = colors.map((color) => rgb(context, color));
    const steps = palette.length - 1;
    const side = Math.max(width, height);
    const image = context.createImageData(width, height);
    for (let y = 0; y < height; y++)
      for (let x = 0; x < width; x++) {
        // How bright the mesh is here, each blob fading out smoothly to its radius
        let field = 0;
        for (const [bx, by, radius, weight] of BLOBS) {
          const dx = (x - bx * width) / (radius * side);
          const dy = (y - by * height) / (radius * side);
          const distance = dx * dx + dy * dy;
          if (distance < 1) field += weight * (1 - distance) * (1 - distance);
        }
        const tone = palette[Math.min(steps, Math.floor(Math.min(1, field) * steps + bayer[(y % 8) * 8 + (x % 8)]))];
        const at = (y * width + x) * 4;
        image.data[at] = tone[0];
        image.data[at + 1] = tone[1];
        image.data[at + 2] = tone[2];
        image.data[at + 3] = 255;
      }
    context.putImageData(image, 0, 0);
  }

  let timer: ReturnType<typeof setTimeout> | undefined;
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(paint, RESIZE_MILLIS);
  };
  paint();
  // The canvas stretches with the window until the resize settles
  window.addEventListener('resize', schedule);
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', schedule);
}
