# Alloca Layout and Feature Brief

This file defines information architecture and product layout only. Do not use
it to choose colors, typography, rounded corners, icon style, hover colors, or
other visual tokens. Those must be inferred from the attached visual reference
and the Stitch prompt.

## Application Shell

Desktop application shell:

```text
Left sidebar (expanded, collapsible) | Top header
                                     | Page title + contextual filters
                                     | Main content grid
```

The sidebar is persistent on desktop and becomes a drawer on mobile. The header
is persistent and contains global actions. Content pages use a page title, local
filters where relevant, then the main working area.

## Sidebar Menu

```text
OVERVIEW
  Dashboard

RESOURCES & BOOKINGS
  Resource Directory
  Floor Map
  My Bookings

OPERATIONS
  Notifications

INSIGHTS                         [MANAGER, ADMIN]
  Utilization
  Utilization Alerts

ADMINISTRATION                   [ADMIN]
  Resources & Schedules
  Zones & Floor Maps
  Users & Roles
  Departments
  System Settings
```

`Resource Directory` is the central place to find meeting rooms, desks, and
shared equipment. Use filters instead of separate navigation items for each
resource type.

## Main Header

The header contains:

- Collapse-sidebar control and current page context.
- Global search for resources and bookings.
- Contextual selectors when relevant: building/zone, date, and time range.
- Notification bell with unread count.
- User avatar and profile menu.
- One primary action: `Book a resource`.

## Page Layouts

### Dashboard

1. Page title and context filters.
2. KPI row: available resources, utilization, bookings, alerts.
3. Main operational grid: upcoming bookings/activity, availability summary, and
   utilization insights.
4. Smaller side panels for alerts, notifications, or quick resource details.

### Resource Directory

Search and filter toolbar, resource list/table, availability status, and a
direct `Book` action. It is list/table-first on every viewport.

### Booking

Resource selection, date/time selection, availability validation, then booking
confirmation. Keep entered form values after validation or conflict errors.

### Floor Map

Optional 2D companion to the resource list. User selects zone/date, then a time
range, then can open a bookable resource marker. It must always provide a list
view fallback. No 3D or WebGL in Phase 1.

## Role Visibility

- `EMPLOYEE`: Dashboard, Resource Directory, Floor Map, My Bookings,
  Notifications.
- `MANAGER`: Employee menus plus Utilization and Utilization Alerts.
- `ADMIN`: All menus, including Administration.

## Product Boundary

Keep the prototype focused on resource discovery, booking, booking management,
notifications, utilization, alerts, and admin management. Do not add payments,
SSO, calendar sync, check-in, AI features, or 3D maps.
