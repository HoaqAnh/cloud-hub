# CloudHub backend

Java sources live under `src/main/java/com/cloudhub`. Tests mirror the feature packages under `src/test/java/com/cloudhub`.

```text
com/cloudhub/
├── CloudHubApplication.java
├── auth/
│   ├── web/
│   ├── application/
│   └── persistence/
├── cloud/
│   ├── account/{web,application,persistence}/
│   ├── oauth/{web,application,persistence}/
│   ├── files/{web,application}/
│   ├── search/{web,application}/
│   └── transfer/{web,application,persistence}/
├── provider/
│   ├── CloudProvider.java
│   ├── ProviderRegistry.java
│   ├── google/
│   ├── dropbox/
│   └── onedrive/
├── security/
└── web/
```

## Dependency rules

- Feature `web` packages bind HTTP parameters and DTOs, read the session identity, call application services, and adapt the results to HTTP. Streaming headers and response bodies also belong here. The shared `web` package handles health, API errors, request logs, and shared HTTP response types.
- Application services coordinate operations and own transaction boundaries. Files, search, and transfers use the account application layer to check ownership and obtain access tokens. OAuth delegates account linking to the same layer. Application services can be tested directly with fake repositories or cloud providers.
- JPA entities and repositories live in the feature that owns their tables. Auth owns users, accounts own connected accounts and their tokens, OAuth owns state, and transfers own pending moves. PostgreSQL migrations live in `src/main/resources/db/migration`.
- The `provider` package defines the common cloud contract and value types. Application services resolve a `CloudProvider` from `ProviderRegistry` using `ProviderType`. Each adapter owns its provider-specific HTTP requests, responses, and API behavior.
- `security` owns the Spring Security filter chain, session identity, and token encryption. Keep servlet and MVC types at the HTTP boundary.

OAuth consumes state in its own transaction before exchanging a code, then persists the account in a separate transaction. Access-token refresh uses short read and write transactions around the provider call. A move retry locks the pending move until the source step completes so duplicate retries do not replay a completed move.

Run the backend test suite from this directory with `bash ./mvnw clean verify`. The default test profile uses H2 and mocked cloud providers for local coverage. CI also checks startup and migrations against PostgreSQL.
