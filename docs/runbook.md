# Operations and handover

## Intended operating model

One private instance per small business. One owner bootstraps from environment; technicians are created through the owner UI. The current release has no invitation email, password self-service, account deactivation, reassignment or multi-business tenancy. Decide and implement account recovery and staff departure procedures before customer rollout. Do not recover an account by deleting business data. A database administrator can replace an account's BCrypt hash through a separately reviewed maintenance procedure; changing OWNER_PASSWORD in the environment only affects first bootstrap.

## Configuration

- `DB_URL`: PostgreSQL JDBC URL, default `jdbc:postgresql://localhost:5432/serviceproof`.
- `DB_USER`: database login, default `serviceproof`.
- `DB_PASSWORD`: required secret, no default.
- `OWNER_USERNAME`, `OWNER_PASSWORD`: required on an empty database; no bundled demo owner.
- `COOKIE_SECURE`: defaults true. Set false only for loopback HTTP development.
- `PHOTO_JOB_MAX_BYTES`, `PHOTO_TOTAL_MAX_BYTES`: positive byte limits; defaults 104857600 and 1073741824. A 507 leaves stored evidence unchanged; resolve capacity before retrying.
- `TEST_DB_URL`: test-only database, default `jdbc:postgresql://localhost:5432/serviceproof_test`; its name must end in `_test` because integration fixtures truncate it.

Keep `.env` untracked and readable only by the operator. Do not paste credentials into issue trackers, screenshots or support logs. Compose requires nonempty bootstrap secrets even after initialization; a managed deployment should inject secrets from its own secret manager instead.

## Local startup, shutdown and diagnosis

`docker compose up --build -d` starts a private PostgreSQL network and the application. The database health check gates startup. `docker compose logs app` shows Flyway and startup failures. `GET /api/session` responds once the app is listening; this checks web availability, not database health. `docker compose down` stops services without deleting the named volume. **Do not use `down -v` unless intentionally destroying a disposable environment.**

A 401 means sign in again. A 403 after session expiry requires a fresh session/CSRF token. A 409 means reload and reconcile; do not blindly resubmit a stale write. Repeated upload failure: use JPEG/PNG ≤5 MB and ≤12 megapixels; retry the retained file, or choose a smaller file. No background/offline retry exists. A requested correction reopens the job; approval requires resubmission.

## Backups and restore

PostgreSQL contains accounts, all photos and snapshots. Back up the database and verify restoration to a separate database before serving customers. Example local operator command (output contains private data):

```bash
docker compose exec -T db pg_dump -U serviceproof -Fc serviceproof > serviceproof.backup
```

Encrypt backups, restrict access, choose retention and run a documented restore drill. Restore with `pg_restore` into a **new empty database**, apply no ad-hoc report edits, then compare job/report counts and inspect sample photo/report integrity. A disposable local backup/restore drill verifies account/job/unit/photo/report counts and photo/report hashes; the deployed environment still needs an encrypted offsite recovery drill. SQL report immutability does not defend against privileged administrators or destructive schema operations.

## Release procedure

1. Require the Verify workflow to succeed for the exact source commit.
2. Review changes, dependency advisories, database migration compatibility and backup readiness.
3. Manually run Build release candidate for that same commit. It exports a container tarball tagged/labeled with the SHA and makes no external deployment.
4. Test the container in an isolated staging environment, including login, database access, upload, approval and print.
5. A real production deployment needs separate owner approval, hosting/security setup and operational ownership. None is performed here.

Forward migration is preferred. Older application versions may not understand new schemas; never promise rollback by merely changing the image tag. Keep the previous image and a tested database recovery plan.

## Launch blockers

- Actual customer demand, report acceptance and willingness to pay are unvalidated.
- Independent security review remains outstanding. Dated npm/container scans and the documented conditional Spring advisory assessment are in the security review; rescan at every release.
- HTTPS, reverse-proxy/body-size policy and distributed edge rate limits are not deployed. The built-in limiter permits 20 login requests and 120 public session/static requests per remote address per minute for one instance; it does not trust forwarded client headers and may group users behind a proxy.
- Password rotation/recovery, account deactivation and staff offboarding need a secure operator workflow.
- Default photo quotas are implemented (100 MiB/job, 1 GiB/instance, retained history included). Retention/deletion policy, privacy consent, encrypted/offsite backups and deployed recovery drills still need an owner. Photos are retained to preserve approved history; storage can grow.
- Database encryption at rest, monitoring/alerting, service uptime, load tests, accessibility audit and cross-browser printing are not verified.
- No malware scanner is included; format decoding/re-encoding and input limits reduce file risk without certifying uploads safe against every decoder vulnerability.
- Verify the exact release commit in GitHub Actions before rollout. No production deployment is performed by this repository.

This is a tested portfolio workflow, not a certified or production-ready service.
