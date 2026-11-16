# Phase 10 Requirements - Wallet, alerts, favorites

## Goal
Cash moves safely, the investor is told when a price is reached, and the price board remembers what they follow.

## Scope
- Wallet: `GET /wallet`, `POST /wallet/transfers` (idempotent by client reference, 422 if reused with different details), banks and rails
- Withdrawals: never into the margin loan (409) and never into cash reserved by open buy orders
- Alerts: ABOVE or BELOW a price, fire once after the trade commits, in their own transaction, pushed to the owner; at most 20 active
- Favorites per user, idempotent
