# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew bootRun                                      # run the app
./gradlew build                                        # full build (compile + test)
./gradlew test                                         # run all tests
./gradlew test --tests "br.com.investlog.server.ServerApplicationTests"  # single test class
./gradlew generateJooq                                 # regenerate jOOQ sources from the Liquibase schema
```

`spring-boot-docker-compose` is a `developmentOnly` dependency wired to `compose.yaml`, so
`bootRun` automatically starts the local Postgres container (`investlog-postgres`, db/user/pass
`sa_investlog`, see `compose.yaml`) — no manual `docker compose up` needed for local dev.

`generateJooq` (and therefore `build`/`bootRun`/`test`, which depend on it via
`compileKotlin`) requires Docker running locally: it starts a throwaway `postgres:17-alpine`
Testcontainer, applies `db/changelog/db.changelog-master.xml` to it via Liquibase, generates
jOOQ Kotlin sources from it, then tears the container down.

## Architecture

`Application.kt` is the `@SpringBootApplication` bootstrap. Beyond that, the application layer
has:

- `shared/security` — cross-cutting current-user resolution: `CurrentUser` (domain model),
  `UserRepository` (queries/updates `system.users` via the generated jOOQ `USERS` table), and
  `CurrentUserProvider`/`FixedCurrentUserProvider` (resolves the current user; fixed to a dev
  user for now).
- `config` — `WebMvcConfig` (path-segment API versioning, prefixes `@RestController`s under
  `/private/{version}`) and `GlobalExceptionHandler` (RFC 7807 `ProblemDetail` error responses).
- `profile` — the first business module: `GET`/`PATCH /private/v1/profile`, following a
  `rest/{controllers,dtos}` + `domain/services` layout that future modules will follow.

The persistence schema itself is fully defined (see below).

- Kotlin 2.3.21 / Spring Boot 4.1.0, JVM 25 toolchain. Root package: `br.com.investlog.server`.
- Web: `spring-boot-starter-webmvc` (servlet MVC, not WebFlux).
- Persistence: PostgreSQL via `spring-boot-starter-jooq` + `spring-boot-starter-liquibase`. The
  full schema — `system`/`finances` Postgres schemas, `system.users`, the `finances` portfolio
  tables (wallets, stock/crypto/fund holdings + lots/contributions, type lists, currency rates),
  the `finances.wallet_kind` enum, and the `finances.holdings_overview` materialized view — is
  defined in `src/main/resources/db/changelog/db.changelog-master.xml` and applied
  automatically by Liquibase on startup. `application.yaml` configures `spring.liquibase.*`
  (tracking tables renamed to `database_changelog`/`database_changelog_lock`);
  `application-prod.yaml` adds `spring.datasource.*` via `DB_HOST`/`DB_PORT`/`DB_NAME`/
  `DB_USER`/`DB_PASSWORD` env vars; `application-dev.yaml` relies on `spring-boot-docker-compose`
  auto-detection.
- jOOQ codegen (`nu.studer.jooq` plugin, configured in `build.gradle.kts`) generates Kotlin
  sources for the `system`/`finances` schemas into `build/generated-sources/jooq/main` (package
  `br.com.investlog.server.jooq`, gitignored) as part of `compileKotlin` — see `generateJooq`/
  `startJooqDb`/`stopJooqDb`. `shared/security/UserRepository` is the first consumer, querying
  and updating `system.users` via the generated `USERS` table.
- `jackson-module-kotlin` for Kotlin-aware JSON (de)serialization. `kotlin-reflect` is on the
  classpath for frameworks that need it (jOOQ/Jackson/Spring).
- Other starters: `actuator`, `mail`, `validation`.

### Testing

- JUnit 5 (`useJUnitPlatform()`, parallel forks = available processors) + `kotlin-test-junit5`.
- `TestcontainersConfiguration` (`src/test/.../TestcontainersConfiguration.kt`) registers a
  Postgres `@ServiceConnection` testcontainer; `ServerApplicationTests` imports it for
  `@SpringBootTest`.
- `TestServerApplication` is an alternate `main` that boots the app with
  `TestcontainersConfiguration` applied, for running locally against a throwaway
  Testcontainers-managed Postgres.

### Packaging

`bootJar` is configured with layered jars (`dependencies`, `spring-boot-loader`,
`snapshot-dependencies`, `application`), output as `server.jar`. `bootBuildImage` targets
JVM 25 and produces `investlog/server:v<version>`.
