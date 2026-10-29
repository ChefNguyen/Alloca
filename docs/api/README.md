# Alloca API Contract

`openapi.yaml` is the source of truth for the Phase 1 HTTP API covering Auth &
RBAC, Resource Management, Booking Engine, Notifications, Floor Maps, and
Analytics/Reports/Alerts. The detailed scheduling semantics are maintained in
[`../business-rules.md`](../business-rules.md); this contract defines their HTTP
representation and validation outcomes.

## Conventions

- Base path: `/api/v1`.
- Authentication: JWT access token in `Authorization: Bearer <token>`.
- Access token lifetime: 30 minutes. Refresh token lifetime: 7 days.
- Identifiers: UUID strings.
- All response timestamps are ISO 8601 in UTC (`Z`). Business dates, operating
  schedules, booking-boundary checks, and report windows are interpreted in
  `Asia/Ho_Chi_Minh` (UTC+07:00).
- Pagination: zero-based `page`, `size` from 1 to 100, and `sort=field,direction`.
- Errors: a stable machine-readable `code` plus a Vietnamese `message`.
- Cancellation is a state transition through `POST /bookings/{id}/cancel`; booking
  records are never deleted.

## RBAC and notifications

| Role | Users | Resources and maps | Bookings |
| --- | --- | --- | --- |
| `EMPLOYEE` | Own profile and notification list/read state | View active resources, map availability, and availability details | Create/view/cancel bookings they own |
| `MANAGER` | Read users in own department | Same as Employee | Manage bookings whose **owner** belongs to their department; owner department, not creator department, determines this scope |
| `ADMIN` | Full user and department management; configure global reminder lead time | Full zone/resource/schedule/map management | Create for, view, and cancel any booking |

A resource with a null `department` is shared and can be booked by every active
user. A department-owned resource is also bookable across departments; its owner
department instead controls report/alert ownership. Role scope is enforced by the
backend; query parameters only narrow a permitted scope.

Booking events notify the booking owner: confirmation, reminder, and cancellation.
When the creator is different from the owner, a cancellation additionally notifies
the creator exactly once; the owner never receives a duplicate notification. For
resource alerts, shared-resource alerts go to Admins; department-resource alerts go
to Managers in that resource's department.

## Booking and schedule rules

1. Booking starts and ends must fall on 30-minute local boundaries. The duration
   must be from 30 minutes through 8 hours inclusive, and the complete interval
   must stay within one `Asia/Ho_Chi_Minh` calendar day.
2. The start must not be in the past and must be no more than 30 local calendar
   days ahead. The interval must be within the resource's operating schedule.
3. Creation locks the resource row with `SELECT ... FOR UPDATE`, checks overlap
   inside the same transaction, and returns `409 BOOKING_TIME_CONFLICT` when
   another `CONFIRMED` booking overlaps.
4. A cancellation is allowed only while the booking is `CONFIRMED` and before its
   start. Only the active owner, a Manager in the owner's department, or an Admin
   may cancel within their scope. A distinct creator receives the cancellation
   notification but gains no cancellation authority merely by creating the booking.
5. Replacing a resource schedule is rejected if it would make any current or future
   `CONFIRMED` booking invalid. The MVP keeps no historical snapshots of schedules.
6. Soft deletion requires `SUSPENDED` status and no ongoing or future `CONFIRMED`
   booking. Non-admin users cannot see `DELETED` resources.

**Implementation defaults** (not separately user-approved decisions): the global,
Admin-configured reminder lead time is 15 minutes and accepts 1–1,440 minutes. A
booking created after its normal reminder moment receives no reminder. A catch-up
job sends only a previously eligible reminder, no more than five minutes after it
became due and strictly before the booking start. Confirmation/cancellation changes
and their in-app notifications must be atomic, either in the same transaction or
through a durable transactional outbox. Every event/recipient pair yields one
in-app notification.

## Reporting defaults

Reports use scheduled time, not actual usage. Utilization numerator includes only
`CONFIRMED` and `COMPLETED` booking intervals, clips each interval to the requested
report period, and excludes `CANCELLED`. The denominator is operating-schedule time
within the same period. A zero denominator yields `null` utilization, never `0`.

The utilization alert default is `85` percent and may be overridden per resource.
The **implementation-default** alert evaluation window is the seven completed local
days `[today - 7 days, today)` in `Asia/Ho_Chi_Minh`; the threshold is breached
only when utilization is strictly greater than the configured threshold. At most one
active (`OPEN` or `ACKNOWLEDGED`) alert exists per resource/type: continued breach
updates it without re-notifying, while recovery resolves it and a later breach starts
a new episode. The report exposes that denominator schedules are current schedules,
so historical rates can be recalculated after a schedule change.

## Authentication lifecycle

Disabled accounts are rejected immediately by login, refresh, and authenticated
requests, even if a previously issued access token has not expired. Changing a
password invalidates refresh tokens issued before that change. Access tokens live
only in frontend memory. Login sets the refresh token in the host-only
`alloca_refresh` HttpOnly cookie, never in JSON. Refresh reads that cookie without
a JSON body. On reload, the frontend calls refresh then `/auth/me` before routing.
`POST /auth/logout` expires the cookie; the frontend clears memory too. Copied JWTs
are not revoked by logout. Login, refresh and logout require a trusted `Origin`
and `X-Requested-With: Alloca`. See [cookie configuration](../specs/browser-auth.md).

## Traceability and validation

| Requirement group | Contract coverage | Expected contract tests |
| --- | --- | --- |
| Booking boundaries, horizon, duration, and conflicts | `BookingCreateRequest`, `POST /bookings` | Boundary acceptance/rejection, same-day/horizon cases, concurrent conflict |
| Scope and cancellation recipients | `POST /bookings`, `POST /bookings/{bookingId}/cancel`, Notifications | Owner-department Manager cases; distinct owner/creator notification de-duplication |
| Schedule/deletion protections | resource schedules and deletion endpoints | Invalidating schedule and ongoing/future booking rejections |
| Maps and availability | floor-map upload/availability and resource availability endpoints | PNG/JPEG/content/type and 10 MiB limit; stage-one daily map status then stage-two interval check |
| Utilization and alerts | Analytics, Reports, Alerts endpoints | Clipping, status filtering, zero denominator, seven-day and threshold edge cases |
| Authentication lifecycle | login, cookie refresh/logout, change password, and protected endpoints | Immediate disabled-account rejection; prior-refresh invalidation; CSRF rejection; cookie clearing |

Validate the contract with Redocly CLI:

```powershell
npx --yes @redocly/cli lint docs/api/openapi.yaml
```

Example payloads are stored in [`examples`](examples).
