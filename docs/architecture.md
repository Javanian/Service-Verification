# Architecture and decisions

## Data flow

Browser → same-origin Spring Security session/CSRF boundary → API → Jobs/Photos services → JDBC transaction → PostgreSQL. Angular is packaged as static resources in the application JAR. Spring Boot starts Flyway before the owner bootstrap.

`accounts` stores local credentials and roles. A `job` owns 1–30 `units` and one technician assignment. Each unit has three checks, observations/actions, and current before/after photo references. `photos` is append-only through the application, so replacing a current image does not break an old report. `reports` stores an approved JSON snapshot plus approver/time; a database trigger rejects updates/deletes. `events` records workflow and evidence changes. This is an operational history, not a tamper-evident forensic audit.

Every mutation locks the parent job using `SELECT ... FOR UPDATE`. Clients also submit the last `version`; a mismatch produces 409. The lock makes checking completeness, changing state and taking the approved snapshot atomic. A second simultaneous writer loses cleanly rather than overwriting the first.

State transitions:

```text
DRAFT ──submit──> SUBMITTED ──approve──> APPROVED
                     │                     │
               request changes       explicit revision
                     ↓                     ↓
             CHANGES_REQUESTED           DRAFT (revision + 1)
                     └──submit──> SUBMITTED
```

Only owners create jobs/technicians and review/revise. Owners or the assigned technician may document/submit. Submitted and approved records reject edits. The assignment and invoice reference are intentionally fixed after creation; create the job carefully. Correcting these fields is outside this release. A later revision carries forward the saved evidence; the owner must ensure any changed observations are accurately documented before reapproval.

## ADR 001: Modular monolith and JDBC

One deployment unit fits one small business. JDBC makes authorization queries and locking explicit. Spring Security handles authentication/session lifecycle rather than custom tokens. The cost is writing SQL and mapping a small set of response objects manually. Multi-tenant SaaS and multiple application replicas are not supported design goals.

## ADR 002: Private images in PostgreSQL

Image metadata and bytes participate in the same transaction; no public bucket, orphan-file cleanup or paid service is needed. Inputs are limited to 5 MB/12 megapixels and decoded with a format-aware reader before allocation. JPEG re-encoding drops source metadata and active file content. Originals are not retained. Re-encoding does not prove a photo is truthful. Old images remain for historical reports; storage quotas and a retention policy are launch prerequisites.

An upload has a client-generated UUID retry key and source SHA-256. Retrying the same unit/kind/bytes returns success without another photo/version increment. Reusing a key for different content returns 409. Retries after a record becomes locked are rejected. The UI keeps the File object and retry key in memory and preserves unsaved checklist inputs during uploads. Refreshing the page discards unsaved inputs/files; there is no offline queue.

## ADR 003: Snapshot revisions instead of editable final reports

Approval takes a snapshot of job fields and units and keeps permanent photo IDs. A later revision increments the job revision and reopens the working copy without mutating previous report rows. Printing uses the snapshot endpoint. This is stronger than a UI-only lock but does not protect against a privileged database operator.

## ADR 004: Browser printing instead of a PDF service

Print CSS produces a portable invoice attachment without headless-browser infrastructure in production. Browser QA generates an actual PDF and checks print controls are hidden. Pagination and printer behavior can differ between browsers; Chromium is verified, other browsers need a print acceptance check before rollout.

## ADR 005: Exact versions and a controlled release candidate

The frontend lockfile, pinned Boot parent, Maven wrapper checksum and image digests constrain builds. Docker packaging skips tests because the independent Verify workflow runs them against PostgreSQL first. A manual workflow exports a container artifact with the source commit label. It has no deployment or registry permissions. Do not equate a successful build with production approval.
