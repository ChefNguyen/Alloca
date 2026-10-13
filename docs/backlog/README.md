# Alloca backlog — synchronization note

Updated 2026-10-11. `Backlog.xlsx` is the Jira export. `Backlog.v1.0.original.xlsx` is preserved before documentation synchronization. This Markdown file is the reviewable source for acceptance criteria until the synchronized workbook export is regenerated; it does not create or modify remote Jira issues.

## Sprint and dependency adjustments

- Keep existing Jira keys, estimates, sprint assignments, and assignees unless the team re-estimates them in Jira.
- `ALC-58` must target the approved cloud demo topology (Vercel + Render Free + Supabase PostgreSQL + Cloudflare R2; optional HTTPS Email API). Docker Compose is only local development/integration-test support.
- `ALC-60` minimal CI should begin with lint/contract validation and unit/integration-test scaffolding early; it must run build and tests on every push once application source exists.
- The critical PostgreSQL concurrency/integration tests under `ALC-63` must be developed with Sprint 2 Booking Engine work, not deferred until feature behavior is unverified. The existing Sprint 3 issue remains the tracking issue; its acceptance work begins in Sprint 2.
- CSV is optional P2 priority. PDF export is out of Phase 1; `ALC-53` must not promise PDF.

## Common Definition of Done

For every issue: validation and negative cases exist; backend performs authorization and object scope checks; API response/error matches `docs/api/openapi.yaml`; data changes are transactional where stated; no secret/token/password is logged; `docs/business-rules.md`, SRS, diagram, contract and test plan remain consistent. A test plan is not evidence that a test ran; attach real output when implementation begins.

## Existing Jira issue acceptance criteria

### Auth & RBAC — ALC-8, ALC-9, ALC-25, ALC-26, ALC-27, ALC-28

- **ALC-8 Login:** active username/password only; BCrypt cost >=12; returns 30-minute access and 7-day refresh token. Invalid credentials and inactive users return401 without account enumeration. Trace: FR-AUTH-01, TC-AUTH-01/02.
- **ALC-9 Refresh:** validates signature, token type, expiry, active user and `credentials_version`; password change invalidates old refresh token. No server-side logout/revocation claim in Phase1. Trace: FR-AUTH-02/06/07, TC-AUTH-01/03/04.
- **ALC-25 RBAC:** backend enforces role plus owner-department object scope. Query parameters can narrow but not expand scope. Manager booking scope follows booking owner, not creator/resource. Trace: FR-AUTH-03/05, TC-SCOPE-01..04.
- **ALC-26 Accounts:** Admin creates/edits/deactivates accounts; deactivate preserves history and rejects protected requests/refresh immediately, even with old access token. Trace: FR-AUTH-04, TC-AUTH-02.
- **ALC-27 Access assignment:** Admin changes role/department with audit; it does not auto-cancel existing booking. Trace: FR-AUTH-05, TC-SCOPE-04.
- **ALC-28 Profile:** current active user updates permitted profile fields and changes password; cannot alter own role/department/isActive. Password change increments credentials version. Trace: FR-AUTH-06/08, TC-AUTH-03/04.

### Resource Management — ALC-29, ALC-30, ALC-31, ALC-32, ALC-33

- **ALC-29 Resources:** Admin CRUDs ROOM/EQUIPMENT/DESK. `DELETED` is soft deletion, allowed only from `SUSPENDED` and only when no `CONFIRMED` booking has `end > now`; department may be null for shared resource. Suspend blocks new booking but preserves old booking. Trace: FR-RES-01/05, TC-RESOURCE-02/03.
- **ALC-30 Schedules:** weekly non-overlapping windows with `start < end`; no window means closed. Replacing schedules locks/checks resource and returns409 + conflicting booking IDs if an ongoing/future `CONFIRMED` booking becomes invalid; no partial update or auto-cancel. Trace: FR-RES-02/06, TC-TIME-03 and TC-RESOURCE-01/04.
- **ALC-31 Zones/department:** Admin assigns optional zone/department. Department indicates alert/report ownership, never who can book. Shared (`department=null`) alert goes to Admin. Delete referenced zone/department is blocked, not cascaded. Trace: FR-RES-03/07, TC-SCOPE-03.
- **ALC-32 Resource list/detail:** active users see non-DELETED resources, details, schedule and availability under pagination. Trace: FR-RES-04, TC-TIME-06.
- **ALC-33 Filtering:** type/zone/status filters narrow the list; map/report/booking scope stays backend controlled. Trace: FR-RES-04.

### Booking Engine & concurrency — ALC-34, ALC-35, ALC-36, ALC-38, ALC-39, ALC-40, ALC-41

- **ALC-34 Availability:** business timezone Asia/Ho_Chi_Minh; returns operating windows and valid30-minute availability. Excludes past time, unavailable state and outside30-day horizon; it is not a reservation. Trace: FR-BOOK-01, TC-TIME-01..06.
- **ALC-35 Create booking:** start/end must be local30-minute boundaries, duration30min–8h, same local day, `start > now`, and `start <= now +30days`. `userId` optional means own booking; creator is authenticated principal and cannot be forged. Exact adjacency is allowed, overlap uses half-open intervals. Trace: FR-BOOK-02/07, TC-TIME-01..05 and TC-SCOPE-01.
- **ALC-36 Concurrency:** PostgreSQL transaction locks resource row before checking `AVAILABLE`, schedule and `CONFIRMED` overlap, then inserts/history/transactionally durable confirmation. Twenty simultaneous **overlapping** requests produce one201 and19 conflicts; different non-overlapping intervals may all succeed. Trace: FR-BOOK-03, NFR-REL-01/PERF-03, TC-CONC-01..05.
- **ALC-38 Own cancellation:** only owner, `CONFIRMED`, strictly before start; state transition/history and notification are idempotent. Trace: FR-BOOK-04, TC-RESOURCE-04.
- **ALC-39 Manager cancellation:** only owner in Manager's current department, before start; direct ID and query filter cannot bypass scope. Trace: FR-BOOK-04, TC-SCOPE-02 and TC-RESOURCE-04.
- **ALC-40 Admin cancellation:** any booking but still only `CONFIRMED` and before start. Trace: FR-BOOK-04, TC-RESOURCE-04.
- **ALC-41 History and delegated booking:** Employee own, Manager owner-department, Admin all; filters/pagination cannot broaden scope. Include delegated create (`userId`) acceptance checks, because no existing dedicated Jira key covers UC-09. Trace: FR-BOOK-05/07, TC-SCOPE-01..04.

### Notification Engine — ALC-42, ALC-44, ALC-45, ALC-46, ALC-47

- **ALC-42 Confirmation:** exactly one in-app confirmation to booking owner; booking and in-app event are atomic or durable-outbox backed. Email does not decide create result. Trace: FR-NOTIF-01, TC-CONC-03/05 and TC-NOTIF-01.
- **ALC-44 Reminder:** system-wide Admin setting default15 minutes, accepted range1–1440; one reminder only when `createdAt <= due <= now < start` and delay <=5min. Late-created booking receives no catch-up reminder. Restart is idempotent. Trace: FR-NOTIF-02/07 and FR-JOB-01/02, TC-JOB-01/02.
- **ALC-45 Cancellation notification:** owner always receives it; distinct creator additionally receives exactly one. Include actor/reason where supplied. Trace: FR-NOTIF-03, TC-NOTIF-02.
- **ALC-46 Alert notification:** departmental resource -> active Managers of owning department; shared resource -> active Admins. If no eligible Manager, persist alert for Admin dashboard without broadcast. Trace: FR-NOTIF-04, TC-STAT-04/05.
- **ALC-47 Email:** optional P2 HTTPS Email API, dispatched after database commit. Failure/retry cannot roll back booking/in-app state. SMTP is not the deployment contract. Trace: FR-NOTIF-05, TC-NOTIF-05.

### Analytics & alerts — ALC-48, ALC-49, ALC-50, ALC-51, ALC-52, ALC-53

- **ALC-48 Utilization API:** “tỷ lệ thời gian đã đặt,” not actual occupancy. Numerator clips `CONFIRMED` + `COMPLETED` to `[from,to)` and excludes `CANCELLED`; denominator is current operating windows. Zero denominator returns N/A/null. Current schedule means prior reports can change after schedule edit; disclose this MVP limitation. Trace: FR-STAT-01/07, TC-STAT-01..03.
- **ALC-49 Trend API:** counts booking by local start date and status, not by creation date. Trace: FR-STAT-02, TC-STAT-06.
- **ALC-50 Alert job:** daily rolling completed local days `[today-7,today)`; breach only when rate > threshold; default85 override per resource. One active OPEN/ACKNOWLEDGED episode per resource/type; continued breach updates without spam, recovery resolves, later breach starts new episode. On restart evaluate current period only. Trace: FR-STAT-03/08, TC-JOB-04 and TC-STAT-04/05.
- **ALC-51 Admin dashboard:** cross-system metrics/trends/alerts with defined data labels and pagination/filter behavior. Trace: FR-STAT-04.
- **ALC-52 Manager report:** resource ownership department scope, not booking-owner scope and not shared/system-wide resources. Trace: FR-STAT-05, TC-SCOPE-03 and TC-STAT-06.
- **ALC-53 Export:** optional CSV only, within report scope; no PDF export in Phase1. Trace: FR-STAT-06.

### Interactive floor map — ALC-54, ALC-55, ALC-56, ALC-57

- **ALC-54 Upload:** Admin only; PNG/JPEG content verification and10MiB maximum; use R2 durable storage, not Render disk. Replacing image rejects/forces repair of marker outside new dimensions. Trace: FR-MAP-01, TC-MAP-01.
- **ALC-55 Positioning:** Admin PATCHes a resource map position; rectangle has positive size and stays within original image. Frontend scales using original dimensions. Trace: FR-MAP-02, TC-MAP-02.
- **ALC-56 Two-stage display:** daily state: green all valid slots free, yellow partly, red none, gray suspended, deleted hidden. After exact interval selection, marker is binary green/red; booking still revalidates. Trace: FR-MAP-03, TC-MAP-03/04.
- **ALC-57 Map booking:** opens prefilled booking form, does not hold a slot, and handles POST409 gracefully. Trace: FR-MAP-04, TC-MAP-04.

### DevOps & documentation — ALC-58, ALC-59, ALC-60, ALC-61, ALC-62, ALC-63

- **ALC-58 Deployment:** Vercel frontend, Render Spring backend, Supabase PostgreSQL TLS/session pooler, R2 map upload, optional HTTPS email. Document secrets/CORS, persistent data behavior, health/redeploy, Render sleep and catch-up limitation. Compose remains local/test only. Trace: deployment.mmd, TC-DEPLOY-01.
- **ALC-59 Git:** repository, branch convention, `.gitignore` for secrets/build artifacts and policy for generated diagrams; no credentials committed.
- **ALC-60 CI:** on push, validate OpenAPI/examples, compile/test when source exists, unit coverage measurement, PostgreSQL integration stage, and Mermaid/SRS documentation checks. Initial docs-only repo may implement lint validation first.
- **ALC-61 OpenAPI:** `/api/v1` contract covers all Phase1 modules, error code shape, object scopes and examples; implementation SpringDoc stays aligned in CI. Trace: NFR-MAIN-02.
- **ALC-62 API test collection:** environments use non-secret variables; includes auth/scope/boundary/conflict/error cases and does not store live refresh tokens. Trace: all TC groups.
- **ALC-63 Testing:** >=70% service unit coverage plus PostgreSQL integration tests for lock, rollback, scope, jobs and notifications. Begin critical integration test development with Sprint2. Trace: test-plan.md.

## Local proposed tasks — no remote Jira key yet

These are gaps exposed by synchronization. Create/link actual Jira issues only after the team chooses ownership and estimates.

1. Durable notification/outbox and unique event-recipient deduplication.
2. Booking completion and restart catch-up scheduler, controllable clock, and operator logging.
3. Notification list/read and Admin reminder-settings HTTP API/UI.
4. Analytics/reports/alert HTTP API/UI, utilization calculation and alert episode lifecycle.
5. Floor map upload, marker write API, day state and selected-interval availability API/UI.
6. Postgres integration test infrastructure and CI service setup.
7. Update BRD citations/source-date and explain requirement-elicitation method; do not invent survey results.

## Traceability

- Canonical rules: `../business-rules.md`.
- SRS: `../SRS.md`; generated `../../SRS.pdf`.
- HTTP: `../api/openapi.yaml` and `../api/README.md`.
- Verification: `../test-plan.md`.
- Diagram deployment: `../diagrams/deployment.mmd`.
