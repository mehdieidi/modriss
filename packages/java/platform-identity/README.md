# platform-identity

Authentication and user lifecycle for the platform. Domain records live under
`platform.identity.domain` (`UserRecord`, `AuthSession`). The primary entry point is
`AuthService` in `platform.identity.application` for registration, login, logout, and session
validation.
