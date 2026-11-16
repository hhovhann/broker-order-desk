# Phase 2 Plan - Resilience and invariants

1. Stress test with 6 virtual threads x 150 random orders across 6 accounts, cancels mixed in
2. Failure injected with `@MockitoSpyBean` on `SettlementService`
