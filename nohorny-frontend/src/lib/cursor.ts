// Request identifiers are UUIDv7, so they sort by creation time: the first 48 bits are the milliseconds since the epoch.
// The `before` cursor of GET /api/requests accepts any UUID, which lets the admin panel start the list at a time or at
// the end of an identifier prefix without a dedicated endpoint.

const HEX = /^[0-9a-f]{1,32}$/;

/** Formats up to 32 hex digits with the hyphens of a UUID, '01a10c55ef3b' gives '01a10c55-ef3b'. */
function hyphenate(hex: string): string {
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)]
    .filter(Boolean)
    .join('-');
}

/** The cursor before the requests created at `millis` or later. */
export function cursorAt(millis: number): string {
  return hyphenate(Math.max(0, Math.floor(millis)).toString(16).padStart(12, '0').padEnd(32, '0'));
}

/**
 * Reads an identifier, or its beginning, from what an admin pastes: '01a10c55', a full identifier or a request link.
 * Returns the prefix in the UUID format and the cursor right after the last identifier starting with it, or null.
 */
export function parsePrefix(input: string): { prefix: string; cursor: string | null } | null {
  const text = input.trim().toLowerCase();
  const id = text.match(/\/requests\/([0-9a-f-]+)/)?.[1] ?? text;
  const hex = id.replaceAll('-', '');
  if (!HEX.test(hex)) return null;
  // One past the largest identifier with this prefix, since the cursor is excluded. Nothing is past ffff…
  const next = (BigInt(`0x${hex.padEnd(32, 'f')}`) + 1n).toString(16).padStart(32, '0');
  return { prefix: hyphenate(hex), cursor: next.length > 32 ? null : hyphenate(next) };
}

/** The value of a datetime-local input showing `millis` in the local time zone. */
export function toLocalInput(millis: number): string {
  const date = new Date(millis);
  return new Date(millis - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
