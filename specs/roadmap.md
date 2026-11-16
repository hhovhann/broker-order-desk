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

## Backlog (not built, and said so)
- FIX gateway and direct market access for institutions (a QuickFIX/J adapter in front of `OrderService`)
- Fundamentals and ratios (analytics), news and disclosures feeds
- Corporate actions: dividends, subscription rights; zakat and purification reports
- Bank transfer integration (cash in and out), statements as PDF
- Order types beyond limit (market, IOC, stop), amend order
- Market calendar and trading-hours enforcement; Arabic UI (RTL)
- Horizontal scaling by sharding symbols across engine instances; outbox to a message broker for market-data fan-out
