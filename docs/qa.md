# Verification record

Executed in the isolated Linux cloud workspace on 2026-10-07. No build ran on the user's Windows computer and no existing repository was modified.

| Check | Actual result |
| --- | --- |
| Backend / real PostgreSQL 17.11 | 10 tests passed, zero failures (7 integration, 3 focused safety tests) |
| Frontend | Strict TypeScript, Angular production build and 2 unit tests passed |
| Browser | 4 Chromium tests passed against the packaged same-origin application; container workflow also verified |
| Mobile | 390-pixel checklist has no horizontal page overflow |
| Print | Actual print action tested, controls hidden in print CSS, PDF generated |
| Accessibility | Keyboard-only sign-in and axe WCAG A/AA checks passed on login, jobs and checklist |
| npm audit | Zero reported vulnerabilities after the documented CLI dependency patch |
| Runtime image scan | 0 high, 1 conditional critical, 16 medium, 4 low; conditional advisory assessed in security-review.md |
| Database image scan | Zero reported vulnerabilities in the final non-root PostgreSQL image |
| Backup/restore | Final PostgreSQL image: counts and photo/report hashes matched after restoring a disposable database |
| Container | Multi-stage build passed; application UID 10001, read-only root, 768 MiB limit; database UID 70 |
| GitHub Actions | Workflow included; exact-commit remote result must be checked after this checkpoint is pushed |

Backend tests cover incomplete submission, correction/resubmission, owner-only approval, final locks and historical snapshots, direct SQL immutability, technician isolation, private files, CSRF, retry idempotency/conflicts, invalid image rejection, stale/simultaneous edits, validation, storage quotas/rollback, bounded requests, safe storage errors, and absence of XSLT view beans.

Browser tests exercise the real database-backed UI, force a photo-upload network failure and retry, preserve unsaved inputs, review corrections, approve, print, reject another technician's photo access, start a revision and verify the previous report is unchanged. They also verify invalid login and session-expiry recovery.

Screenshots and PDF are captured from the running application using fictional customers and synthetic flat-color image fixtures. They are not generated design mockups or evidence of real AC service. See `evidence/` for raw results and `docs/security-review.md` for the explicit Spring advisory assessment and remaining lower-severity findings.

Maven/Docker in this cloud needed proxy CA/DNS configuration supplied outside the repository through temporary BuildKit secret mounts. TLS verification stayed enabled. Browser-download domains were blocked, so local browser QA used installed Chromium; CI installs the Playwright-pinned browser. Dependency databases were downloaded from their official GHCR mirrors.

No production deployment, independent penetration test, full accessibility audit, load test, cross-browser print certification, encrypted/offsite recovery drill or customer demand validation is claimed. Operator TLS/secrets/domain, offboarding/recovery and privacy/retention procedures remain launch prerequisites.
