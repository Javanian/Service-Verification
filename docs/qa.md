# Verification record

Executed in a fresh Linux cloud container on 2026-10-07. No builds ran on the user's Windows computer. This directory was created separately from all configured repositories.

## Verified locally

| Check | Result / evidence |
| --- | --- |
| Kotlin / JVM 21 compilation and executable JAR | Pass; Spring Boot 3.5.16 |
| PostgreSQL integration tests | 5 pass, 0 failures; PostgreSQL 17.6 with Flyway V1 |
| Frontend TypeScript | Pass with strict types and Angular strict templates |
| Frontend unit tests | 2 pass: completeness and editability |
| Angular production build | Pass; approximately 205 kB initial raw JavaScript/CSS |
| Chromium end-to-end | 3 passed against the final runtime container: full workflow, invalid authentication/private-file denial, expired-session recovery |
| Mobile layout | 390 px viewport, no horizontal body overflow; screenshot retained |
| Print | Print button invokes printing; print controls hidden; PDF generated |
| Demo safety | Missing explicit opt-in and non-loopback URL rejected; local fictional job creation verified |
| Compose | Configuration validation passed; database port is not exposed |
| Container | Multi-stage build and browser smoke passed; non-root UID 10001, read-only root filesystem, 768 MB memory limit |

The five backend tests cover: incomplete submit; correction reason and resubmission; owner-only approval; submitted/final locks; preservation of revision 1 after edits and revision 2 approval; direct SQL snapshot update rejection; technician job/list/photo isolation; unauthenticated photo denial; CSRF rejection; private no-store JPEG response; idempotent upload retry; retry-key content conflict; invalid active-file upload rejection; stale edit conflict; input bounds; and two truly simultaneous writes returning one 200 and one 409.

Browser tests drive real forms and sessions against PostgreSQL. They force a network error during upload, reuse the retained upload retry, preserve unsaved actions, save/submit, request corrections, resubmit, approve, verify locked controls, print, deny another technician's photo access, create a new revision and verify the old snapshot still shows the original observations. Credentials in tests are explicitly disposable local fixtures, not real customer credentials.

Screenshots and generated PDF use fictional names and synthetic flat-color test photos. They prove rendering/workflow behavior, not successful real-world service.

## Environment adaptations

Maven needed the cloud HTTP proxy. Docker builds needed the environment CA and Java truststore plus proxy DNS mapping. These were supplied outside the repository as BuildKit secret mounts; TLS verification was not disabled and trust material is not embedded in the runtime image. The optional secret mounts are harmless on ordinary networks. Browser download domains were blocked, so QA used installed `/usr/bin/chromium` through Playwright. CI installs the Playwright-pinned Chromium build; that exact browser build has not yet run here.

## Not run / blocked

GitHub CLI authentication failed. `gh repo create Javanian/service-proof --private` returned `Forbidden`. The connected GitHub profile independently identified Javanian and the commit email; its exposed tools include no repository-creation endpoint. Therefore no repository URL, pushed SHA, remote CI result or release artifact is claimed. GitHub Actions definitions are present, pinned to verified upstream commit SHAs, but must run after authenticated creation and push.

No production deployment, paid services, independent penetration test, full accessibility audit, performance/load test, cross-browser printing, backup restore drill or customer demand validation was performed. These remain launch prerequisites, not hidden completed checks.

## Final container evidence

Image ID: `sha256:5ad5ffde13d03af8161c00faedc290b7fc6a9ff6af2ac2be444f527272ea9c0a`. Browser tests ran against this image on loopback port 8081. The image was built from the implementation in this initial source commit; the image intentionally contains no Git credentials or cloud proxy trust material. GitHub workflow YAML parses locally, but remote execution remains blocked.
