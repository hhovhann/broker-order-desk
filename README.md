# Broker Order Desk

A small, honest **stock-brokerage order desk**: orders in, pre-trade risk, price-time matching, atomic settlement, margin monitoring, an audit trail, and a React trading dashboard. Java 25, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL, Docker, Kubernetes, GitHub Actions.

Built as a work sample for a *Senior Java Software Engineer* role at a Qatar stock-exchange brokerage that finances margin trading and provides liquidity.
**All symbols, prices, balances and people are synthetic.** It is not connected to any exchange and is not investment advice.

**Demo video (about 3 minutes, narrated):** [`demo/out/broker-order-desk-demo.mp4`](demo/out/broker-order-desk-demo.mp4) (captions: `.srt` / `.vtt`)

## Job description, line by line

| The role asks for | Where it is proven |
|---|---|
| Robust, scalable, secure backend services in Java (Spring Boot) | `OrderService`, `MatchingEngine`, `SettlementService`; Java 25 records, sealed types, pattern-matching `switch`, virtual threads, `ScopedValue` |
| Spring MVC, Data, **Security** | REST controllers; JPA repositories with pessimistic locks and aggregate queries; stateless Basic, four roles, `@PreAuthorize` with an **ownership check** (`BrokerApiTest`) |
| RESTful API design and best practices | Versioned `/api/v1`, RFC 7807 problems, bean validation, idempotent order placement by client order id, `201` with a business outcome for risk rejections |
| **Multithreading, concurrency, async** | One virtual-thread actor per symbol (no locks around the book), `CompletableFuture` through Spring MVC, ordered locking; proved by `EngineInvariantsTest` (many concurrent orders: cash, shares and reservations are conserved, book = database) |
| Design patterns and principles (OOP, SOLID, **DDD**) | Aggregates that guard their invariants (`Account.reserve`, `Holding.deliver`, `BrokerOrder.applyFill`), a pure-Java `OrderBook` behind a port, sealed `Decision` types, small services with one reason to change |
| Unit, integration, functional tests (JUnit, **Mockito**) | 27 tests: `OrderBookTest`, `RiskServiceTest` (Mockito), `BrokerApiTest` (end to end with security), `MarketDataTest`, `EngineInvariantsTest`, `SettlementFailureTest` (`@MockitoSpyBean`) |
| Troubleshoot production issues and bottlenecks | Settlement failure injection and recovery; the double-resting bug found by a test and fixed (see `CHANGELOG.md`, D-3); correlation id on every request and audit line |
| Maven or Gradle | Maven wrapper |
| Docker, Kubernetes | Multi-stage `Dockerfile` (non-root), `compose.yaml` with PostgreSQL, `k8s/` with probes, resources, read-only root, and `Recreate` + 1 replica **by design** (single writer per symbol) |
| CI/CD | `.github/workflows/ci.yml`: build the UI, `./mvnw verify`, build the image |
| Git, code reviews, collaborative development | Small commits, specs per feature, decisions and changelog kept next to the code |
| Lead, mentor, establish best practices | `specs/` (mission, tech stack, roadmap, a folder per feature with requirements, plan and validation), `docs/ARCHITECTURE.md`, a spec consistency check (`tools/check_specs.py`) |

## What you can do in it

| Screen | What it shows |
|---|---|
| Prices | Live board: offer and bid, last, trades, average, shares, riyal value, previous close, high, low, trend, change |
| Trading | Symbol list, instrument header, order ticket with live pre-trade hints, orders with cancel, trade tape, order book depth |
| Portfolio / Margin | Cash (negative cash is the margin loan), holdings, buying power, equity against the maintenance level on a gauge, margin-call banner |
| Statement | The account's executions |
| Liquidity | Two-sided quote (bid and offer together) through the normal order path |
| Risk | Margin calls across accounts (risk officers only) |
| Audit | Append-only trail: who did what, when, correlation id (administrators only) |

## Run it

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/25-tem     # any JDK 25
cd web && npm ci && npm run build && cd ..                # builds the UI into src/main/resources/static
./mvnw verify                                             # 27 tests, no Docker needed
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run        # http://localhost:8080
docker compose up --build                                 # PostgreSQL + the desk
cd demo && uv run python make_demo.py                     # re-record the narrated video
python3 tools/check_specs.py                              # specs are consistent
```

Demo users (synthetic, password `demo-pass`): `trader1` (margin account), `trader2` (holds shares), `lp1` (liquidity provider), `risk1`, `admin`.
Prices are integers in **mills** (1/1000 QAR); `12.500` QAR is `12500`.

## How it is organised

```
src/main/java/dev/brokerdesk/
  domain/   aggregates and value types (no Spring)
  engine/   OrderBook (plain Java) and MatchingEngine (actors)
  service/  OrderService, RiskService, SettlementService, MarginService
  web/      controllers, security helpers, problem details
  config/   security, properties, demo data
web/        React 19 + TypeScript dashboard
specs/      mission, tech stack, roadmap, decisions, one folder per feature
```

## Honest limits

A focused sample, not a trading system. **Not built:** FIX or direct market access, real exchange connectivity, market-data feeds, fundamentals and news, corporate actions, order types beyond limit, trading-hours enforcement, bank transfers, Arabic UI. Scaling out needs symbol sharding (the design is described in `docs/ARCHITECTURE.md`, not implemented). Authentication is HTTP Basic to stay self-contained; production would use the bank's OIDC. The Kubernetes manifests were not applied to a live cluster. They are listed in `specs/roadmap.md` under Backlog.
