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

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml test
.\backend\mvnw.cmd -f .\backend\pom.xml spring-boot:run
```

The application imports `backend/.env` for local development and reads the same
variables from the Render environment in deployment. Add the first migration
before implementing database-backed features. Keep real secrets out of source
control.
