-- Fix: restore DEFAULT current_timestamp on columns that lost their default
-- after the initial Flyway migration was applied to Supabase.
-- Root cause: Supabase Table Editor may strip DEFAULT expressions when
-- columns are inspected or modified through the dashboard UI.

ALTER TABLE departments
    ALTER COLUMN created_at SET DEFAULT current_timestamp;

ALTER TABLE users
    ALTER COLUMN created_at SET DEFAULT current_timestamp,
    ALTER COLUMN updated_at SET DEFAULT current_timestamp;
