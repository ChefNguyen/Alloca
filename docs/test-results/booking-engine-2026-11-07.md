# Booking Engine verification — 2026-11-07

Backend implementation for ALC-36 and ALC-38/39/40 is complete in the local
`feature/ALC-34-35-booking-engine` workspace. This records executed checks, not a
remote Jira status change or a deployment. Existing workspace changes were retained.

## Executed checks

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -l backend/target/alc-36-38-40-final-verify.log verify
npx.cmd --yes @redocly/cli lint docs/api/openapi.yaml
git diff --check
```

| Check | Result |
| --- | --- |
| Unit/controller tests (Surefire) | 323 passed; 0 failures, errors or skipped |
| Integration tests (Failsafe) | 27 passed; 0 failures, errors or skipped |
| AuthIT | 11 passed |
| BookingConcurrencyIT | 16 passed against isolated PostgreSQL 16 containers |
| Maven verify | BUILD SUCCESS |
| Redocly OpenAPI lint | Valid |
| Git whitespace check | Passed |

Environment: Windows, Java 17, Docker Desktop with Engine 29.4.3,
`postgres:16-alpine`, Spring Boot 3.5.7, Testcontainers 1.21.3. Test-only
`docker-java.properties` selects Docker API 1.44, compatible with Docker Engine
25+. No application/Supabase database was modified by these integration tests.

## Verified behavior

- ALC-36: resource pessimistic write lock precedes resource state, operating
  schedule and overlap checks. READ_COMMITTED lets the waiter see committed bookings.
  Server time is checked again after acquiring the lock.
- Twenty simultaneous overlapping HTTP create requests produce exactly one 201
  and nineteen 409 responses, one booking, one initial history and one confirmation.
  Adjacent requests both succeed.
- Creation is serialized with suspension, schedule replacement and deletion.
  A replacement/delete that follows a successful create protects that booking;
  a create that follows changed resource state/schedule reads the committed change.
- ALC-38/39/40: `POST /api/v1/bookings/{bookingId}/cancel`, optional reason up to
  500 characters, returns 204. Active owner, Manager sharing the owner's current
  non-null department, and Admin are supported. A departmentless Manager can cancel
  their own booking. Creator status and resource ownership confer no extra rights.
- Only CONFIRMED bookings strictly before start can be cancelled. Started,
  cancelled and completed bookings return 409 for every role; retries add no effects.
- Cancellation locks resource then booking. Concurrent requests produce one
  transition/history and one cancellation notification per eligible recipient.
  Cancellation frees the interval for a new booking.
- Booking changes, audit and in-app notifications commit or roll back together.
  Confirmation goes to owner; cancellation goes to owner and distinct original
  creator, active recipients only. Stable event/recipient keys deduplicate retries.
- Unit/controller checks cover invalid input, missing records, inactive actor,
  direct-ID/query scope bypass, stale role/department, boundary times and reason size.
- Availability and create share the exact `now + 30 days` start boundary and
  merge adjacent operating windows, including odd-minute window boundaries.
- Local operating hours persist without timezone conversion on UTC,
  Asia/Ho_Chi_Minh and America/New_York servers. Booking/audit instants remain UTC.

## Deployment note

The PostgreSQL tests exposed an existing LocalTime mapping issue: an 08:00
operating window could be stored as 01:00 on a UTC+7 server when JDBC timezone
was UTC. `ResourceSchedule` now uses direct JDBC LocalTime mapping. Check existing
`resource_schedules` rows against intended local hours before deploying this change,
and re-save any affected schedules with those intended values. No automatic data
shift was made: the original writer's timezone is not recorded.

Four existing AuthIT media-type assertions were corrected to accept the valid
`application/problem+json;charset=UTF-8` response while retaining status/error-code
checks. No tests were disabled or skipped.

Email delivery, notification listing/settings, schedulers and frontend integration
remain separate backlog work. The mandatory in-app booking events needed here are
persisted directly in the booking transaction; no email call holds a resource lock.
