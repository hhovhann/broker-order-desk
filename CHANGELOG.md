# Changelog

Newest first. Each entry says what changed and why, so the history of the decisions is kept with the code.

## 2026-11-16 (phase 8: the full platform)
- **Live stream** (server-sent events over fetch, so it can carry the Authorization header). A change bumps a version and every screen refetches at once; polling stays as a safety net. Payloads carry no money values.
- **Market header and index**: synthetic equal-weight index (10,000 at the previous close), breadth, session totals and the session state in Doha time (Sunday to Thursday, 09:30 to 13:00). Order entry is not blocked outside hours, so the UI says "Simulated session".
- **Charts**: daily candles with volume for any symbol and the index, ranges 1M to ALL, and a compare mode on one percentage axis.
- **Analytics**: statements and ratios (EPS, P/E, P/B, dividend yield, book value, profit/equity). `Ratios` is a pure class with unit tests for losses, zero denominators and overflow.
- **News and disclosures**, market-wide or per company.
- **Favorites**, CSV export, daily limit tooltip on the price board.
- **Wallet**: deposits and withdrawals over simulated rails. Idempotent by client reference; a withdrawal can never reach into the margin loan or into cash reserved by open buy orders.
- **Price alerts** that fire once when a trade prints through the target (evaluated after the trade commits, in its own transaction) and push a pop-up.
- **Liquidity**: member list and an activity chart that separates liquidity-provider trades from ordinary market trades.
- **Open account** form (public, validated, rate limited) and an administrator review list. Services pages state plainly what is not built (FIX gateway, direct market access).
- **Arabic with right-to-left layout** for navigation, header, price board, analytics, wallet and alerts. First draft of the translations; needs review by a native speaker. Ticket and some account screens are still English.
- **Garbage collector benchmark** and the decision to run on generational ZGC (see `docs/GC-BENCHMARK.md`). First version of the benchmark kept 40 million samples in the heap, which distorted every collector; replaced by a small histogram before any number was recorded.
- Fix: stopping the application waited the full 30 s shutdown phase whenever a live stream was open (found by a test JVM that would not exit). `StreamHub` now closes its streams on `ContextClosedEvent`, before the web server is asked to stop; that test class went from 38 s to 8 s.
- Tests: 44 (was 27).

## 2026-11-16
- **Phase 7** Narrated demo video from the real app (React UI) and application messages.
- **Phase 6** Dockerfile (Node, Maven, JRE 25, non-root), Compose with PostgreSQL, Kubernetes manifests (single active replica by design), GitHub Actions.
- **Phase 5** React 19 + TypeScript dashboard: login, price board, trading with depth and ticket, portfolio, margin gauge, statement, liquidity, risk, audit.
- **Phase 4** Price-board statistics, statement, audit trail, `/me`, `/market/rules`.
- **Phase 3** Liquidity-provider two-sided quotes.
- **Phase 2** Idempotent placement, recovery, settlement-failure handling, concurrency invariants test.
  - Fix (D-3): the first order on a symbol rested twice because recovery ran after the order was saved. `engine.ready()` now completes before a new order is persisted, and recovery after a failure only restores orders the actor already handled.
  - Fix: shared in-memory database between test contexts hid the seed data; each context now gets its own database.
- **Phase 1** Core order desk: domain, order book, risk, settlement, REST, security.
