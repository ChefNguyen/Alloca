# Alloca

Alloca is a workspace resource and booking management application. The repository
contains a React frontend, a Spring Boot backend foundation, and the product
documents that define the team backlog, API contract, data model, and diagrams.

## Repository layout

- `frontend/` - React, TypeScript, Vite UI mock and future client application.
- `backend/` - Spring Boot scaffold for the module implementations in the sprints.
- `docs/` - Product, architecture, API, database, backlog, and diagram documents.
- `design.md` - UI design direction used for the frontend work.
- `AGENTS.md` - Repository conventions for coding assistants and contributors.

## Prerequisites

- Node.js 20 or later and npm.
- Java 17.

## Run locally

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

Use `npm run lint` and `npm run build` before opening a pull request.

### Backend

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml spring-boot:run
```

The initial health check is available at `http://localhost:8080/actuator/health`.
Run backend verification with:

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml test
```

## Development accounts

Demo accounts for local development and manual testing (one per role). The backend only
authenticates against the database, so seed them once after the first start:

```bash
./backend/scripts/run-dev-seed.sh
```

The script is idempotent: running it again resets these accounts to the values below. It reads
the connection from `backend/.env` (or `backend/env`) and never prints credentials.

| Role     | Username    | Password         | Department  |
|----------|-------------|------------------|-------------|
| ADMIN    | `admin`     | `Admin@12345`    | -           |
| MANAGER  | `manager1`  | `Manager@12345`  | Engineering |
| EMPLOYEE | `employee1` | `Employee@12345` | Engineering |

The seed also adds one department (Engineering) and two zones (West Wing with a floor map,
No Map Zone) so resources can be created in the Administration page.

> These passwords are public. Use them only against a local or throwaway database and never
> seed them into a shared, staging or production environment. Seed data lives in
> `backend/scripts/dev-seed.sql` and is intentionally not a Flyway migration.

## Team workflow

The backend is intentionally a scaffold. Implement each work item end to end:
read its acceptance criteria in `docs/backlog/`, follow the specifications and
OpenAPI contract in `docs/`, then add migration, API, domain logic, and tests in
the same pull request. Existing TODOs point to the relevant Sprint work items.

Do not commit `node_modules`, build output, Maven caches, local environment files,
or coding-agent state. The root `.gitignore` already excludes these paths.
