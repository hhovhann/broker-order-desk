# Roadmap

Phases are small and shippable. Each has a folder `specs/YYYY-MM-DD-pN-<name>/` with `requirements.md`, `plan.md`, `validation.md`.
A finished phase gets `Status: done YYYY-MM-DD` directly under its heading; `tools/check_specs.py` then expects every box in its `validation.md` ticked.
History of what changed and why is in `CHANGELOG.md`; decisions are in `specs/decisions.md`.

## Phase 1 - Core order desk
Status: done 2026-11-16
- Domain model (Account, Holding, Order, Trade, Instrument), price-time order book, pre-trade risk, settlement, REST API, Spring Security roles

## Phase 2 - Resilience and invariants
Status: done 2026-11-16
- Idempotent orders, single-writer engine, recovery from the database, settlement-failure handling, concurrency stress test

## Phase 3 - Liquidity provider
Status: done 2026-11-16
- Two-sided quotes through the normal order path, role-restricted

## Phase 4 - Market data, statement, audit
Status: done 2026-11-16
- Price board statistics and sparklines, account statement, append-only audit trail, `/me`, rules endpoint

## Phase 5 - React dashboard
Status: done 2026-11-16
- Login, price board, trading screen with depth and order ticket, portfolio, margin gauge, statement, liquidity, risk, audit

## Phase 6 - Delivery: Docker, Kubernetes, CI
Status: done 2026-11-16
- Multi-stage image, Compose with PostgreSQL, Kubernetes manifests, GitHub Actions

## Phase 7 - Demo video and outreach
Status: done 2026-11-16
- Narrated video of the real app, README mapped to the job description, application messages

## Phase 8 - Live stream and market header
Status: done 2026-11-16
- Every screen updates the moment something changes, and the header always shows the state of the market.

## Phase 9 - Charts, analytics, news
Status: done 2026-11-16
- Staff can look at a company the way analysts do: price history, fundamentals, announcements.

## Phase 10 - Wallet, alerts, favorites
Status: done 2026-11-16
- Cash moves safely, the investor is told when a price is reached, and the price board remembers what they follow.

## Phase 11 - Liquidity view, services, open account
Status: done 2026-11-16
- The liquidity role has its own view, and the service pages tell the truth about what exists.

## Phase 12 - Arabic and right-to-left
Status: done 2026-11-16
- The interface can be used in Arabic with a mirrored layout.

## Phase 13 - Garbage collector benchmark
Status: done 2026-11-16
- The collector for the matching path is chosen from measurements, with the limits of those measurements written down.

## Backlog (not built, and said so)
- FIX gateway and direct market access for institutions (a QuickFIX/J adapter in front of `OrderService`)
- Real fundamentals and news feeds (the screens exist; the data is synthetic)
- Corporate actions: dividends, subscription rights; zakat and purification reports
- Real bank integration (the wallet is simulated), statements as PDF, electronic signature
- Order types beyond limit (market, IOC, stop), amend order
- Trading-hours enforcement (the session state exists; order entry is not blocked); Arabic for every screen and a native review
- Horizontal scaling by sharding symbols across engine instances; outbox to a message broker for market-data fan-out
