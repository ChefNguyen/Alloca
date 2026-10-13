# Database migrations

`V1__initial_schema.sql` establishes the Alloca Phase 1 baseline from
`docs/database/erd.mmd`. Flyway applies it once and records it in
`flyway_schema_history`.

Every later schema change must use a new immutable Flyway migration. Do not edit
an applied migration or rely on Hibernate schema generation.
