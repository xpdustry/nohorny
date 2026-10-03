# NoHorny server HTTP API

Everything the server exposes, for the plugin, the bundled pages and scripts alike.
All JSON endpoints live under `/api`. Pages are static files that consume this API.

## Conventions

- Timestamps are ISO-8601 UTC strings, for example `2026-10-03T12:34:56.789Z`.
- Request identifiers are UUIDv7 strings. They sort chronologically and are unguessable.
- Errors return `{"message": "..."}` with the matching HTTP status.
- Ratings are `SAFE`, `WARN` or `NSFW`. A failed classification has a `null` rating.
  Filters and stats use the lowercase buckets `safe`, `warn`, `nsfw` and `failed`.
- Authentication under `/api` is either HTTP Basic or the session cookie obtained from `POST /login`.
  Basic requests are stateless and skip CSRF. Browser requests must echo the `XSRF-TOKEN` cookie as the
  `X-XSRF-TOKEN` header on `POST`, `PUT` and `DELETE`, including `/login` and `/logout`. Any `GET` without an
  `Authorization` header sets that cookie, so fetch `/api/session` or `/api/stats` first.
  `POST /api/classify` and `POST /api/requests/{id}/purge` never need it.
- Unauthenticated access to a protected endpoint answers `401`, an authenticated caller lacking the admin role
  gets `403`, and a missing or stale CSRF token gets `403` with the message `invalid csrf token`.
  Responses never redirect and never send `WWW-Authenticate`, so browsers never prompt.
- Public `/api/requests/**` endpoints are rate limited per client address and answer `429` with a
  `Retry-After` header when exceeded. Administrators are not limited.
- `/requests/**` and `/api/**` carry `X-Robots-Tag: noindex, nofollow`.

## Plugin API

### `GET /api/status`

Public. Returns `{"message": "<motd>"}`.

### `POST /api/classify`

Public, or authenticated when `nohorny.security.api-default-policy` is `DENY_ALL`.
Body is a raw `image/png` or `image/jpeg`, other content types get `415` and undecodable bytes get `400`.
The optional `X-NoHorny-Version` header records the plugin version.

```json
{
  "classifier": "vit",
  "rating": "NSFW",
  "confidence": 0.97,
  "identifier": "0192f0a1-7c3e-7d2a-9b1e-2f4a6c8e0b1d",
  "url": "https://nohorny.xpdustry.com/requests/0192f0a1-7c3e-7d2a-9b1e-2f4a6c8e0b1d"
}
```

`url` is `null` when `nohorny.public-url` is not configured or when recording the request failed.
Every classification records a request.
The image bytes are only stored when the final rating is `WARN` or `NSFW` or when the classification failed.

## Statistics

### `GET /api/stats`

Public. Aggregates only, no per-request data.

```json
{
  "total": 123456,
  "ratings": {"safe": 120000, "warn": 2000, "nsfw": 1400, "failed": 56},
  "last24Hours": {
    "total": 980,
    "ratings": {"safe": 950, "warn": 20, "nsfw": 9, "failed": 1}
  },
  "updatedAt": "2026-10-03T12:34:56.789Z"
}
```

All-time counters survive request retention. The last 24 hours are computed from retained requests.

### `GET /api/stats/stream`

Public. Server-sent events. Emits one `stats` event with the payload above on connect,
then again after each classification, coalesced to at most one event per second.
A comment line is sent every 15 seconds as a heartbeat.

```text
event: stats
data: {"total":123457,...}
```

## Requests

### Request object

```json
{
  "id": "0192f0a1-7c3e-7d2a-9b1e-2f4a6c8e0b1d",
  "createdAt": "2026-10-03T12:34:56.789Z",
  "durationMillis": 143,
  "successful": true,
  "rating": "NSFW",
  "confidence": 0.97,
  "classifier": "sight-engine",
  "error": null,
  "version": "4.0.0",
  "client": {"type": "mindustry-server", "name": "Chaotic Neutral"},
  "image": {"state": "stored", "mediaType": "image/jpeg", "url": "/api/requests/0192f0a1-7c3e-7d2a-9b1e-2f4a6c8e0b1d/image"},
  "steps": [
    {"classifier": "vit", "rating": "NSFW", "confidence": 0.93, "durationMillis": 61, "error": null},
    {"classifier": "sight-engine", "rating": "NSFW", "confidence": 0.97, "durationMillis": 82, "error": null}
  ],
  "remoteAddress": "203.0.113.7",
  "username": "alice"
}
```

- `classifier`, `rating` and `confidence` are the final verdict, taken from the last successful step.
  `confidence` is the NSFW score of that classifier from `0.0` to `1.0`, which the thresholds turn into the rating.
  A safe result with a score of `0.4` is therefore not "40% confident", it is 40% of the way to the warn threshold.
- `successful` is `false` and `error` holds the fully qualified exception class name when the first classifier failed.
  A failure later in the chain falls back to the previous step and still counts as successful.
- `client.type` is `mindustry-server`, `localhost` or `unknown`. `client.name` lists the public server
  names resolved from the remote address, or repeats the type.
- `image.state` is `none` (safe result, never stored), `stored`, `expired` (image retention elapsed)
  or `purged` (removed by hand). `image.url` is only present when the state is `stored`.
  `image.mediaType` is `null` for `none` and stays set after expiry or purge.
- `remoteAddress` and `username` are omitted unless the caller is an administrator.
  `username` is `null` for anonymous submissions.

### `GET /api/requests/{id}`

Public to anyone holding the identifier. Returns the request object or `404`.

### `GET /api/requests/{id}/image`

Public to anyone holding the identifier. Returns the stored bytes with their media type and
`Cache-Control: no-store`. Returns `410` when the image was never stored, expired or was purged, `404` for unknown identifiers.

### `POST /api/requests/{id}/purge`

Public to anyone holding the identifier, strictly rate limited. Deletes the image bytes immediately and keeps
the request as a tombstone with `image.state` set to `purged`, whatever the previous state. Idempotent.
Returns the updated request object.
Intended for reporting illegal content that must not be retained.

### `GET /api/requests?rating=&before=&limit=`

Administrators only. Newest first.

- `rating` filters by bucket: `safe`, `warn`, `nsfw` or `failed`. Omit for all, anything else is a `400`.
- `before` is the identifier of the last item of the previous page. A malformed identifier is a `400`.
- `limit` defaults to `20` and is clamped between `1` and `100`.

```json
{
  "items": [{"...": "request objects"}],
  "nextCursor": "0192f0a1-7c3e-7d2a-9b1e-2f4a6c8e0b1d"
}
```

`nextCursor` is `null` on the last page.

### `DELETE /api/requests/{id}`

Administrators only. Removes the request entirely, image and tombstone included. Returns `204`, also for unknown identifiers.

## Session

### `POST /login`

Form encoded `username` and `password` with the `X-XSRF-TOKEN` header. Returns `204` with the session cookie
and a rotated `XSRF-TOKEN` cookie on success, `401` otherwise.

### `POST /logout`

Requires the `X-XSRF-TOKEN` header. Returns `204` and invalidates the session.

### `GET /api/session`

Returns `{"username": "alice", "admin": true, "bootstrap": false}` for an authenticated caller, `401` otherwise.
`bootstrap` is `true` for the bootstrap administrator, see below.

Sessions are expired when their user is deleted, or when an administrator changes its password or role,
the next request answers `401`.

## Users

Administrators only. The bootstrap administrator is the account configured with `nohorny.security.admin.username`
and `nohorny.security.admin.password`. It is created on startup when missing, and its password and role are restored
to the configured ones on every startup. It cannot be demoted nor deleted through the API.
Its password can be changed, but the next restart resets it to the configured one.

### User object

```json
{"username": "alice", "admin": true, "createdAt": "2026-10-03T12:34:56.789Z", "bootstrap": false}
```

### `GET /api/users`

Returns the array of user objects, sorted by username.

### `POST /api/users`

JSON body `{"username": "alice", "password": "correct horse", "admin": false}`, `admin` defaults to `false`.
Returns `201` with the user object.

- `username` is 3 to 32 characters among lowercase letters, digits, `_`, `.` and `-`.
- `password` is at least 8 characters.
- `400` with the violated rule as message when the body is invalid, `409` when the username is taken.

### `PUT /api/users/{username}/password`

JSON body `{"password": "..."}`, same rule as above. Returns `204`, `400` for an invalid password, `404` for unknown users.

### `PUT /api/users/{username}/admin`

JSON body `{"admin": true}`. Returns `204`, `400` without `admin`, `404` for unknown users.
Demoting the bootstrap administrator, your own account or the last administrator answers `409`.

### `DELETE /api/users/{username}`

Returns `204`, `404` for unknown users.
Deleting the bootstrap administrator or your own account answers `409`.

## Pages

| Path | File | Purpose |
|---|---|---|
| `/` | `index.html` | landing page with the live counter |
| `/requests/{id}` | `request.html` | one request, image blurred until revealed, purge action |
| `/admin` | `admin.html` | login form, then the request table with filters and infinite scroll, and the user management |
| `/assets/*` | | scripts, stylesheet, fonts and images of the pages |
| `/robots.txt` | | disallows `/requests/`, `/api/` and `/admin` |
