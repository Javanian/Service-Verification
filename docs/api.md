# HTTP API

Same-origin `/api` endpoints return JSON except image bytes and successful login/logout (204). Session cookie authentication; there are no API keys or JWTs. Fetch `GET /api/session` to obtain a CSRF token, then pass it as `X-CSRF-TOKEN` on every POST/PUT. Fetch a fresh token after login or logout because the security context changes. Never log passwords, cookies or CSRF tokens.

| Method / route | Access / behavior |
| --- | --- |
| GET `/session` | Public; `{username, owner, csrf}`; creates a CSRF-bearing session |
| POST `/login` | Form-encoded `username`, `password`; CSRF required; 204 or 401 |
| POST `/logout` | Authenticated session, CSRF; 204 |
| GET `/technicians` | Owner; username list |
| POST `/technicians` | Owner; `{username,password}`; username 3–60 ASCII letters/digits/dot/underscore/hyphen; password 14–72 characters and ≤72 UTF-8 bytes |
| GET `/jobs` | Owner sees all; technicians only assignments |
| POST `/jobs` | Owner; `{customer,location,invoice,technician,units:["label"]}` |
| GET `/jobs/{id}` | Owner or assigned technician; job, units, report revisions and events |
| PUT `/jobs/{id}/units/{unit}` | Owner or assigned technician; `{version,cleaned,drainChecked,coolingChecked,actions}`; editable states only |
| POST `/jobs/{id}/units/{unit}/photos/{before\|after}` | Multipart `file`, `version`, UUID `key`; editable states only; normalized private photo |
| GET `/photos/{id}` | Owner or assigned technician; JPEG; `Cache-Control: no-store` |
| POST `/jobs/{id}/submit` | Owner or assigned technician; `{version}`; all units complete |
| POST `/jobs/{id}/request-changes` | Owner; `{version,reason}`; submitted only |
| POST `/jobs/{id}/approve` | Owner; `{version}`; submitted, complete only |
| POST `/jobs/{id}/revise` | Owner; `{version,reason}`; approved only; increments revision |
| GET `/jobs/{id}/reports/{revision}` | Owner or assigned technician; `{job,approvedBy,approvedAt}` immutable snapshot |

Customer/location/invoice maximums: 160/300/100 characters; all required and nonblank. A job has 1–30 units, each with a nonblank label up to 100 characters. Actions max 2,000 characters; a nonblank action is required at submission. Reason max 1,000 characters; nonblank required for changes/revisions. Each unit must have all three checks true and both images. Report approval repeats server-side completeness checks.

A job response uses database-style fields (`drain_checked`, `before_id`, `created_at`); write commands use the explicitly documented camelCase inputs. UUID IDs are opaque. Every successful mutation returns the updated job including `version`; use that version in the next request. Photo retries reuse the **same** UUID key and bytes. Do not automatically overwrite a 409 response: reload, reconcile inputs and retry deliberately.

Error semantics: 400 invalid input/image; 401 no valid login; 403 role/CSRF denied; 404 missing or inaccessible resource; 409 stale version, locked/invalid transition or conflicting retry key; 413 request too large; 422 incomplete evidence; 429 too many login attempts (Retry-After 60 seconds). Application errors include `{message}`. Security-filter errors may have a generic body; clients must respect the status code.

No public report-sharing endpoint exists. A PDF is an explicit owner/user export through browser printing.
