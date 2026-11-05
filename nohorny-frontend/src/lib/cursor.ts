// Request identifiers are UUIDv7, so they sort by creation time: the first 48 bits are the milliseconds since the epoch.
// The `before` cursor of GET /api/requests accepts any UUID, which lets the admin panel start the list at a time or at
// the end of an identifier prefix without a dedicated endpoint.

const HEX = /^[0-9a-f]{1,32}$/;

function hyphenate(hex: string): string {
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)]
    .filter(Boolean)
    .join('-');
}

/** The cursor before the requests created at `millis` or later. */
export function cursorBefore(millis: number): string {
  return hyphenate(Math.max(0, Math.floor(millis)).toString(16).padStart(12, '0').padEnd(32, '0'));
}

/**
 * Reads an identifier, or its beginning, from what an admin pastes: '01a10c55', a full identifier or a request link.
 * Returns it in the UUID format, or null.
 */
export function parsePrefix(input: string): string | null {
  const text = input.trim().toLowerCase();
  const id = text.match(/\/requests\/([0-9a-f-]+)/)?.[1] ?? text;
  const hex = id.replaceAll('-', '');
  return HEX.test(hex) ? hyphenate(hex) : null;
}

/** The cursor right after the last identifier starting with `prefix`, null when none comes after it. */
export function cursorAfterPrefix(prefix: string): string | null {
  // One past the largest identifier with this prefix, since the cursor is excluded. Nothing is past ffff…
  const next = (BigInt(`0x${prefix.replaceAll('-', '').padEnd(32, 'f')}`) + 1n).toString(16).padStart(32, '0');
  return next.length > 32 ? null : hyphenate(next);
}

/** The value of a datetime-local input showing `millis` in the local time zone. */
export function toDatetimeLocalValue(millis: number): string {
  const date = new Date(millis);
  return new Date(millis - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
