# platform-storage-postgres

PostgreSQL adapter for `PlatformStore`. Implements `PostgresPlatformStore` in
`platform.storage.postgres` with JDBC mapping for all feature domain types and Flyway
migrations under `src/main/resources/db/migration`. Used by the backend delivery layer; feature
modules depend only on `platform-storage-api`.
