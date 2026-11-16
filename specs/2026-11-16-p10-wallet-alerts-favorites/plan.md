# Phase 10 Plan - Wallet, alerts, favorites

1. Wallet rules live in `WalletService` with the account locked
2. Alert evaluation runs in `@TransactionalEventListener(AFTER_COMMIT)` and a `REQUIRES_NEW` transaction so a failed alert can never undo a trade
