# SLC release boundary

Customer: an owner of a small AC servicing business coordinating technicians.

Promise: produce a usable per-job service evidence report, grouped by AC unit, reviewed by the owner, printable as an attachment to an external invoice.

Entry: the owner creates technician credentials and a job with customer, location, invoice reference and unit labels. Required input: checklist, service actions/observations, before and after photos for every unit. Key action: technician submits; owner reviews, requests a reasoned correction or approves. Result: immutable approved revision, private evidence and a print/PDF flow. Recovery: failed photo upload retry, explicit incomplete-unit rejection, stale-version conflict, requested corrections and a new revision after approval.

Delight choice: a mobile-friendly per-unit checklist, visible completion count, preserved inputs during uploads, direct review notes and a clean handover report. Automated checks establish behavior and layout, not that users actually love it.

Observable acceptance:

- An owner creates and assigns a job without editing a database.
- A technician sees only assigned jobs and cannot access another technician’s photos.
- Submitting one incomplete unit is rejected even if the client bypasses the UI.
- A failed upload can be retried without duplicate evidence.
- Submitted evidence is locked; correction includes an actionable reason.
- Approval creates a printable snapshot. Starting a new revision preserves the old one.
- At 390 pixels wide, the checklist has no horizontal page overflow.
- The report includes unit checks, actions, both photos, invoice reference, revision and approval account/time.

Excluded: billing/payments, warranties adjudication, GPS, e-signature, offline synchronization, native Android, payroll, inventory, routing and marketplace matching. No promise of independently verified identities or tamper-proof images.

Demand is unvalidated. Sejasa’s warranty material is only background context for service documentation. Next question: will three small service-business owners use this report on their next real job and attach it to the invoice, and would they pay to keep doing so? Observe completion time, missed evidence, correction loops, print usefulness and privacy concerns before expanding scope.

A complete narrow workflow is separate from production readiness. See the explicit operational and security launch blockers in the runbook.
