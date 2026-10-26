-- Development/demo seed data: sample accounts per role, one department and two zones.
-- NOT a Flyway migration on purpose: the passwords below are public (see README), so this must
-- never run automatically against a production database.
-- Idempotent: safe to run repeatedly; it resets the three demo accounts to the documented state.
--
-- Run (reads the connection from backend/env without printing it):
--   see "Development accounts" in the root README.

insert into departments (id, name) values
    ('11111111-1111-7111-8111-111111111111', 'Engineering')
on conflict (id) do nothing;

insert into zones (id, name, floor, building, floor_map_url, floor_map_width, floor_map_height) values
    ('22222222-2222-7222-8222-222222222222', 'West Wing', 'Level 3', 'Tower A',
     'https://example.com/map.png', 1600, 1000),
    ('33333333-3333-7333-8333-333333333333', 'No Map Zone', 'Level 1', 'Tower A', null, null, null)
on conflict (id) do nothing;

-- bcrypt cost 12, the same encoder as SecurityConfig.
insert into users (id, username, email, password_hash, full_name, role, is_active, department_id) values
    ('a0000000-0000-7000-8000-000000000001', 'admin', 'admin@alloca.test',
     '$2y$12$Kgo8/Oce37yu9EYWsU3epu9bmFf23ld5vRUl2ooT7n9aCNCTGkzGG',
     'Admin User', 'ADMIN', true, null),
    ('a0000000-0000-7000-8000-000000000002', 'manager1', 'manager@alloca.test',
     '$2y$12$5Jwi1Kr3Kq6atUdjz4sva.H/QrZvTJ43TDYkzmsyEzUkXnc7UOvyu',
     'Manager User', 'MANAGER', true, '11111111-1111-7111-8111-111111111111'),
    ('a0000000-0000-7000-8000-000000000003', 'employee1', 'employee@alloca.test',
     '$2y$12$KSSDZHCegxl3y19V6OnwsODEL9gO7aKuSdVWQLyrt0UtGkMI7q2sy',
     'Employee User', 'EMPLOYEE', true, '11111111-1111-7111-8111-111111111111')
on conflict (username) do update set
    email = excluded.email,
    password_hash = excluded.password_hash,
    full_name = excluded.full_name,
    role = excluded.role,
    is_active = true,
    department_id = excluded.department_id,
    updated_at = current_timestamp;
