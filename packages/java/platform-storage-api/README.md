# platform-storage-api

Persistence port for the Modless platform. Defines `PlatformStore`, a generic JSON and blob
repository with path-oriented access. Feature modules depend on this interface; concrete
storage adapters (for example Postgres) implement it without pulling application services.
