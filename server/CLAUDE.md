# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew bootRun                                      # run the app
./gradlew build                                        # full build (compile + test)
./gradlew test                                         # run all tests
./gradlew test --tests "br.com.investlog.server.ServerApplicationTests"  # single test class
```

`spring-boot-docker-compose` is a `developmentOnly` dependency wired to `compose.yaml`, so
`bootRun` automatically starts the local Postgres container (`investlog-postgres`, db/user/pass
`sa_investlog`, see `compose.yaml`) — no manual `docker compose up` needed for local dev.

## Architecture

This is currently a **skeleton** Spring Boot project — `Application.kt` is just the
`@SpringBootApplication` bootstrap and `application.yaml` only sets `spring.application.name`.
There are no controllers, entities, repositories or services yet.

- Kotlin 2.3.21 / Spring Boot 4.1.0, JVM 25 toolchain. Root package: `br.com.investlog.server`.
- Web: `spring-boot-starter-webmvc` (servlet MVC, not WebFlux).
- Persistence (dependencies present, not yet wired up): PostgreSQL via
  `spring-boot-starter-jooq`, with schema migrations intended via
  `spring-boot-starter-liquibase` — `src/main/resources/db/changelog` exists but is currently
  empty.
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
