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

## Team workflow

The backend is intentionally a scaffold. Implement each work item end to end:
read its acceptance criteria in `docs/backlog/`, follow the specifications and
OpenAPI contract in `docs/`, then add migration, API, domain logic, and tests in
the same pull request. Existing TODOs point to the relevant Sprint work items.

Do not commit `node_modules`, build output, Maven caches, local environment files,
or coding-agent state. The root `.gitignore` already excludes these paths.
