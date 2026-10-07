// Formatting of numbers, dates and requests for display.

import type { Bucket, Request, Requester, Step } from './api';

const numberFormat = new Intl.NumberFormat();
const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
const relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

const RATING_LABELS: Record<Bucket, string> = { safe: 'Safe', warn: 'Warn', nsfw: 'NSFW', failed: 'Failed' };

type Maybe<T> = T | null | undefined;

/** Localized integer, '–' when unknown. */
export function number(value: Maybe<number>): string {
  return value == null ? '–' : numberFormat.format(value);
}

/** 0..1 score as '97%', '–' when unknown. */
export function percent(value: Maybe<number>): string {
  return value == null ? '–' : `${Math.round(value * 100)}%`;
}

/** part/total as '4.2%' (one decimal under 10%), '' when unknown. */
export function share(part: Maybe<number>, total: Maybe<number>): string {
  if (!total || part == null) return '';
  const value = (part / total) * 100;
  return `${value > 0 && value < 10 ? value.toFixed(1) : Math.round(value)}%`;
}

/** '143 ms' or '1.25 s', '–' when unknown. */
export function duration(millis: Maybe<number>): string {
  if (millis == null) return '–';
  return millis < 1000 ? `${millis} ms` : `${(millis / 1000).toFixed(2)} s`;
}

/** Localized date and time, '–' when unknown. */
export function formatTime(iso: Maybe<string>): string {
  return iso ? dateFormat.format(new Date(iso)) : '–';
}

/** Localized date only, '–' when unknown. */
export function formatDate(iso: Maybe<string>): string {
  return iso ? new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' }) : '–';
}

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31536000],
  ['month', 2592000],
  ['day', 86400],
  ['hour', 3600],
  ['minute', 60],
];

/** '3 minutes ago', '' when unknown. */
export function relativeTime(iso: Maybe<string>): string {
  if (!iso) return '';
  const seconds = (new Date(iso).getTime() - Date.now()) / 1000;
  for (const [unit, size] of RELATIVE_UNITS) {
    if (Math.abs(seconds) >= size) return relativeFormat.format(Math.round(seconds / size), unit);
  }
  return relativeFormat.format(Math.round(seconds), 'second');
}

const PERIOD_UNITS: [string, number][] = [
  ['day', 86400000],
  ['hour', 3600000],
  ['minute', 60000],
];

/** '14 days' or '36 hours', in the largest unit that divides `millis`. */
export function period(millis: number): string {
  const [unit, size] =
    PERIOD_UNITS.find(([, size]) => millis % size === 0) ??
    PERIOD_UNITS.find(([, size]) => millis >= size) ??
    PERIOD_UNITS[PERIOD_UNITS.length - 1];
  const count = Math.max(1, Math.round(millis / size));
  return `${count} ${unit}${count === 1 ? '' : 's'}`;
}

/** First block of a UUID, for compact display. */
export function shortId(id: string): string {
  return id.split('-')[0];
}

export function bucket(request: Request): Bucket {
  if (!request.successful || !request.rating) return 'failed';
  return request.rating.toLowerCase() as Bucket;
}

export function stepBucket(step: Step): Bucket {
  return step.rating ? (step.rating.toLowerCase() as Bucket) : 'failed';
}

export function ratingLabel(key: Bucket): string {
  return RATING_LABELS[key];
}

/** 'User bob', 'Mindustry network Foo', or 'Anonymous'. The usernames are only sent to the administrators. */
export function requesterLabel(requester: Requester): string {
  switch (requester.type) {
    case 'user':
      return requester.name ? `User ${requester.name}` : 'User account';
    case 'mindustry-network':
      return requester.name ? `Mindustry network ${requester.name}` : 'Unnamed Mindustry server';
    case 'anonymous':
      return 'Anonymous';
  }
}

/** 'vit' for 'vit/onnx-community/nsfw_image_detection-ONNX:1ceb3c7', the model details go in a tooltip. */
export function classifierLabel(id: string): string {
  return id.split('/', 1)[0];
}
