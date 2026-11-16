# Changelog

Newest first. Each entry says what changed and why, so the history of the decisions is kept with the code.

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
