# Security review and scan decisions

This is a focused engineering review, not an independent penetration test or production certification. Raw npm and Trivy reports are retained in `evidence/` and CI artifacts.

## Safeguards in the application

- Spring Security sessions, BCrypt password hashes, CSRF validation and owner/assigned-technician authorization. Cookies are HttpOnly and SameSite=Strict; Secure defaults on.
- Per-address login limit: 20 requests/minute. Public session/static requests: 120/minute. The bounded in-memory map holds at most 10,000 active buckets. It does not trust forwarded addresses; a deployment edge still needs its own limits.
- JSON bodies are limited to 64 KiB even when streamed. Login forms are limited to 16 KiB. Multipart requests are limited to 6 MB, individual images to 5 MB and 12 megapixels. Only decodable JPEG/PNG input is accepted, then re-encoded to JPEG without original metadata.
- Retained image bytes are limited to 100 MiB per job and 1 GiB per instance by default. Historical images count toward both quotas. An advisory transaction lock serializes quota admission across jobs; per-job row locks still guard workflow state. Rejection returns 507 without changing evidence/version. Environment variables can change limits deliberately; setting them lower than existing usage blocks new uploads without deleting reports.
- Database/transaction failures return a generic retryable 503 without SQL, connection strings or submitted data. Application logs record only the failure class. Photo retries reuse an idempotency key; other uncertain writes require reloading before retrying.
- App runtime runs as UID 10001 with a read-only root filesystem, bounded memory and no Linux capabilities. The database image runs directly as PostgreSQL UID 70 and removes the unused root-switch helper.

## Dependency fixes

The initial scan identified fixable advisories. The release pins Jackson BOM 2.21.7, Tomcat 10.1.60 and PostgreSQL JDBC 42.7.13 over the Spring Boot 3.5.16 managed defaults. These are compatible maintenance-line updates and are verified by PostgreSQL integration and browser tests. The JRE base changed to the pinned Ubuntu Noble image. PostgreSQL moved to 17.11 on Alpine; its unused `gosu` helper was removed because its embedded Go library produced avoidable findings. Database initialization and restore are tested with the resulting non-root image.

An optional Angular CLI dependency had [GHSA-6qxp-vccf-f47h](https://github.com/advisories/GHSA-6qxp-vccf-f47h). `package.json` overrides `@modelcontextprotocol/sdk` to the patched 1.32.1; no MCP/OAuth functionality is exposed by this application. npm audit is part of CI. Keep the override until the CLI's dependency catches up.

## Contextual Spring advisory assessment

Trivy reports **CVE-2026-47884** for `org.springframework:spring-webmvc:6.2.19`. The [upstream advisory](https://spring.io/security/cve-2026-47884/) describes a required combination of XSLT view rendering, an implicit view name and a catch-all MVC view mapping. Upstream rates it medium; the scanner's database rates it critical.

This application uses JSON `@RestController` endpoints and static Angular resources. It configures no `XsltView` or `XsltViewResolver`, and a backend test checks both bean types remain absent. The welcome page has an explicit static forward. The described vulnerable execution path is absent in this release. This is a configuration-based assessment, not a claim that the dependency is universally patched.

`check-scan.mjs` allows only this exact advisory/package/version combination while retaining the raw finding and explanation. Every other high/critical finding fails the scan gate. Any future MVC view/template change requires revisiting the assessment and upgrading to the free patched Spring 7 line or another patched supported line. No scanner finding is silently removed from the raw report.

## Operator prerequisites

Independent review, TLS/domain setup, edge limits, private secret distribution, secure password recovery/deactivation, backup encryption/offsite storage, retention/consent policy and operational monitoring are still required. Automated dependency findings change over time: a passing dated scan is not a guarantee of future safety. See the runbook and dated QA record.
