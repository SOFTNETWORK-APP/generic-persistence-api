# Flyway Migrations

## Convention

Each entity type that uses `ColumnMappedJdbcStateProvider` has its own
migration folder: `db/migration/{tablename}/`

## Naming

`V{version}__{description}.sql` (double underscore)

Examples:
- `V1__create_api_keys.sql`
- `V2__add_revoked_at_column.sql`

## Database Compatibility

- Production: PostgreSQL 16+
- Tests: H2 (use PostgreSQL-compatible subset)
- Avoid: PostgreSQL-specific syntax not supported by H2
  (e.g., `GENERATED ALWAYS AS IDENTITY`, `jsonb`)

## Flyway Metadata Tables

Each migration folder gets its own Flyway history table:
`flyway_schema_history_{tablename}`. This prevents conflicts when
multiple entities use Flyway in the same database.
