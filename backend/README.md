# Alloca backend scaffold

This directory is a deliberately minimal Spring Boot foundation for the Alloca
team. It provides the build, dependencies, application bootstrap, health endpoint,
business-time bean, module boundaries and constructor-injected service/controller
shells. It does **not** implement a Phase 1 feature.

## What is already set up

- Java 17, Spring Boot, Maven Wrapper and the core Spring dependencies.
- Future-ready dependencies for validation, security, JWT resource server, JPA,
  Flyway and PostgreSQL.
- Module packages: `auth`, `resource`, `booking`, `notification`, `analytics`,
  `floormap` and `common`.
- Empty controller/service shells with TODO guidance tied to the Jira issue keys.
- `/actuator/health` for a basic startup check and database health check.
- A UTC `Clock` and `Asia/Ho_Chi_Minh` `ZoneId` bean for future business-time logic.
- Supabase PostgreSQL `DataSource`, JPA validation mode and Flyway are wired to
  environment variables; the migration directory intentionally has no schema yet.

## Intentionally not implemented

- Database migrations, entities, repositories and DTOs.
- Login, JWT issuance/validation, CORS policy and role/scope authorization.
- Resource, schedule, booking, notification, analytics and floor-map workflows.
- API endpoint mappings and success responses.
- Scheduled jobs, R2/email integrations, exports and frontend API integration.

The temporary security configuration permits requests so the clean scaffold can
start locally. It must be replaced as part of Sprint 1 Auth & RBAC work, before
the backend is deployed. The first Sprint 1 task should add the initial Flyway
migration and persistence model from `docs/database/erd.mmd`.

## Package conventions

Organize backend code by feature (`auth`, `resource`, `booking`, and so on), with
`controller`, `dto`, `service`, `domain`, and `repository` as sibling packages
inside each feature. Add a package when its implementation is needed.

- Request/response DTOs belong in `com.alloca.<module>.dto`, for example
  `auth.dto`, `resource.dto`, and `booking.dto`. Do not add an `api` package or
  an `api/dto` level.
- Entities, enums, and value objects belong in `<module>.domain`. A time interval
  with overlap behavior is a domain value object, even if it is a Java record.
- Shared response types (`ErrorResponse`, `PageResponse`) belong in `common.dto`.
  Shared exceptions, error codes, and the global exception handler belong in
  `common.exception`. Feature-specific DTOs and exceptions stay in their feature's
  `dto` and `exception` packages.
- Mirror production packages under `src/test/java`; update imports in both
  production and test code when moving a type.

See `docs/architecture/README.md` for the package tree and responsibilities.

## How to work from a Jira item

1. Read `docs/backlog/README.md` for the issue acceptance criteria.
2. Read the linked rule in `docs/business-rules.md`, `docs/specs/` and
   `docs/api/openapi.yaml`.
3. Add the required migration, entity, repository, DTO, service behavior,
   controller route and tests together.
4. Remove or update the relevant TODO only when that acceptance criterion is done.

Examples:

- `ALC-8`, `ALC-9`, `ALC-25` - Auth & RBAC.
- `ALC-29` to `ALC-33` - Resource management.
- `ALC-34` to `ALC-41` - Booking and concurrency.
- `ALC-42`, `ALC-44` to `ALC-47` - Notifications.
- `ALC-48` to `ALC-53` - Analytics and alerts.
- `ALC-54` to `ALC-57` - Floor map.

## Commands

Booking concurrency and cancellation (ALC-36, ALC-38/39/40) use PostgreSQL row
locks. Creation locks the resource before checking its state, schedule and overlap;
cancellation locks resource then booking. Booking, history and in-app notifications
commit together. Notifications use the existing unique event/recipient key and
are persisted directly, without an external call or an in-memory delivery event.

ALC-41 adds scoped booking list/detail/history through `BookingQueryController`
and `BookingQueryService`. The frontend My Bookings screen and shared booking
form now call these APIs together with availability, creation and cancellation.
See [the ALC-41 verification report](../docs/test-results/booking-alc-41-2026-11-08.md)
for executed backend/frontend checks and the isolated live HTTP smoke command.

`POST /api/v1/bookings/{bookingId}/cancel` accepts an optional `reason` (up to
500 characters), returns 204 on success, and returns 409 for an already-cancelled,
completed or started booking. The active owner, a Manager in the owner's current
department, or an Admin may cancel. Creator and resource department do not grant
extra cancellation rights.

Run unit/controller tests and real PostgreSQL integration tests:

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml test
.\backend\mvnw.cmd -f .\backend\pom.xml verify
```

`verify` requires Docker and runs Testcontainers against isolated PostgreSQL 16,
including `BookingConcurrencyIT`. It checks 20 overlapping requests, adjacency,
rollback, cancellation races, notification deduplication and races with resource
status, schedule and deletion. No configured application database is used by that test.
The test classpath selects Docker API 1.44 for Docker Engine 25+ compatibility.

Weekly schedule `LocalTime` fields use direct JDBC time mapping so changing the
server timezone does not shift operating hours. Existing databases populated with
the old mapping should have their `resource_schedules` values checked against the
intended local hours before deployment; re-save affected schedules with their
intended values. There is no automatic data shift because the original writer's
timezone is not recorded.

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml test
.\backend\mvnw.cmd -f .\backend\pom.xml spring-boot:run
```

The application imports `backend/.env` for local development and reads the same
variables from the Render environment in deployment. Add the first migration
before implementing database-backed features. Keep real secrets out of source
control.
