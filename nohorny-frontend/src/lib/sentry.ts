// Reports the errors of the browser to Sentry, when the server sets nohorny.sentry.dsn. Only the errors are collected:
// no tracing, no replays and no personal data.

import * as Sentry from '@sentry/browser';
import { config } from './config';

// A request URL grants access to the request and a user URL names an account, both are left out of the reports
const redact = (url: string) => url.replace(/\/(requests|users)\/[^/?#]+/g, '/$1/:id');

const redactField = (data: Record<string, unknown> | undefined, key: string) => {
  const value = data?.[key];
  if (data && typeof value === 'string') data[key] = redact(value);
};

export function initSentry() {
  const dsn = config?.sentryDsn;
  if (!dsn) return;
  Sentry.init({
    dsn,
    environment: import.meta.env.MODE,
    dataCollection: {
      userInfo: false,
      cookies: false,
      httpHeaders: { request: { allow: ['User-Agent'] }, response: false },
      httpBodies: [],
      urlQueryParams: false,
    },
    integrations: [
      // The clicks and inputs are described with the labels of their element, which name accounts and addresses
      Sentry.breadcrumbsIntegration({ dom: false }),
      // The generated error boundary of @solidjs/vite-plugin catches the render errors and only logs them
      Sentry.captureConsoleIntegration({ levels: ['error'] }),
    ],
    beforeSend(event) {
      if (event.request?.url) event.request.url = redact(event.request.url);
      return event;
    },
    beforeBreadcrumb(breadcrumb) {
      redactField(breadcrumb.data, 'url');
      redactField(breadcrumb.data, 'from');
      redactField(breadcrumb.data, 'to');
      if (breadcrumb.message) breadcrumb.message = redact(breadcrumb.message);
      return breadcrumb;
    },
  });
}
