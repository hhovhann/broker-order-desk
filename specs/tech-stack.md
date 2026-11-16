# Tech stack

Chosen to match the job description, one line of reasoning each.

| Area | Choice | Why |
|---|---|---|
| Language | Java 25 (LTS): records, sealed types, pattern-matching switch, virtual threads, `ScopedValue` | JD asks for Java 17+ / latest LTS |
| Framework | Spring Boot 3.5 | Spring MVC, Spring Data JPA, Spring Security, Actuator |
| Persistence | JPA + Flyway; H2 (PostgreSQL mode) for demo and tests, PostgreSQL for real runs | JD: Spring Data; Flyway owns the schema, Hibernate only validates |
| Security | HTTP Basic here, roles TRADER / LIQUIDITY_PROVIDER / RISK / ADMIN, method security with ownership checks | Self-contained; production swaps in the bank's OIDC (resource server) |
| Concurrency | One single-threaded virtual-thread actor per symbol, `CompletableFuture` at the edges, pessimistic locks in id order for accounts | No locks around the book; deadlock-free settlement |
| Tests | JUnit 5, Mockito, AssertJ, Awaitility, Spring Boot Test, `@MockitoSpyBean` | JD: JUnit, Mockito, integration tests |
| Frontend | React 19 + TypeScript + Vite, no UI or chart library | Fast to build, one jar serves API and UI |
| Build | Maven (wrapper) | JD: Maven or Gradle |
| Containers | multi-stage Dockerfile, Compose with PostgreSQL, Kubernetes manifests (probes, non-root, read-only root fs) | JD: Docker, Kubernetes |
| CI | GitHub Actions: UI build, `mvnw verify`, image build | JD: CI/CD |
| Demo | Playwright + local Kokoro voice + ffmpeg | Narrated video from the real app |
