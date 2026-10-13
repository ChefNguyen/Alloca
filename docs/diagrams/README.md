# Alloca diagrams

This directory contains the versioned Mermaid source diagrams for Alloca. The
business rules in [../business-rules.md](../business-rules.md) are canonical for
Phase 1 behavior; the API contract is `../api/openapi.yaml` and verification is
specified by `../test-plan.md`.

All HTTP examples use the `/api/v1` prefix. Business time is
`Asia/Ho_Chi_Minh`; API timestamps include an offset and responses are
normalized to UTC.

| Diagram | File | Purpose |
| --- | --- | --- |
| Use cases | [use-case.mmd](use-case.mmd) | Phase-1 actors, capabilities, and scope |
| System design | [system-design.mmd](system-design.mmd) | Application layers, durable notification handling, and integrations |
| RBAC overview | [rbac-overview.mmd](rbac-overview.mmd) | Role inheritance and owner-department authorization scope |
| Booking activity | [booking-activity.mmd](booking-activity.mmd) | Booking validation, row lock, and conflict decisions |
| Booking concurrency | [booking-concurrency-sequence.mmd](booking-concurrency-sequence.mmd) | Serializing simultaneous booking attempts |
| Authentication | [auth-sequence.mmd](auth-sequence.mmd) | Active-account checks and credential-version refresh invalidation |
| Notifications | [notification-sequence.mmd](notification-sequence.mmd) | Durable notifications, reminder catch-up, completion, and alert routing |
| Utilization alerts | [utilization-alert-flow.mmd](utilization-alert-flow.mmd) | Seven-completed-local-day calculation, active-alert lifecycle, and recipients |
| Floor map | [floor-map-flow.mmd](floor-map-flow.mmd) | Map configuration plus day/interval availability flow |
| Booking status | [booking-status-state.mmd](booking-status-state.mmd) | Phase-1 booking state transitions |
| Resource status | [resource-status-state.mmd](resource-status-state.mmd) | Resource suspension and safe soft deletion |
| Components | [component.mmd](component.mmd) | Logical application architecture |
| Data model | [erd.mmd](erd.mmd) | Persistent entities, deduplication, durable outbox, and settings |
| Deployment | [deployment.mmd](deployment.mmd) | Canonical free-tier deployment: Vercel, Render, Supabase, R2, optional HTTPS email |

## Key model constraints

- Booking intervals are 30-minute aligned, 30 minutes through 8 hours, on one
  local day, strictly future, and within 30 days. They must fit the current
  operating schedule and cannot overlap a `CONFIRMED` booking.
- A Manager's booking scope is the current department of the booking **owner**.
  Shared resources are bookable by all eligible users; a department-owned
  resource does not itself restrict who may book it.
- Day-map states are green (all valid slots free), yellow (some free), red (no
  valid slots), gray (suspended), and hidden for deleted resources. A selected
  interval is binary bookable/not bookable and must have at least 30 free
  minutes.
- Scheduler work on Render Free is best effort. Its restart catch-up is
  idempotent: complete overdue bookings, send only eligible reminders within
  five minutes of due time, and evaluate only the current alert period.
- In-app confirmation and cancellation notifications are transactionally
  durable (or durable-outbox backed); optional email is sent only after commit.

## Deployment boundary

[deployment.mmd](deployment.mmd) is the deployment source of truth: Vercel
Hobby hosts the frontend, Render Free hosts Spring Boot, Supabase Free provides
PostgreSQL, and Cloudflare R2 stores floor-map images. An HTTPS email provider
is optional. Docker Compose is suitable only for local development or testing,
not the approved deployment architecture.

## Rendering and validation

All diagrams are Mermaid source. Edit `.mmd` files rather than generated image
artifacts. If Mermaid CLI is available, render each file to validate syntax:

```powershell
Get-ChildItem docs/diagrams -Filter *.mmd | ForEach-Object {
  npx --yes @mermaid-js/mermaid-cli -c docs/diagrams/theme.json -i $_.FullName -o "$($_.FullName).svg"
}
```

Generated SVG/PNG files are build artifacts and should not be committed.
