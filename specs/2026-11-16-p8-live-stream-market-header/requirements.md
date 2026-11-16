# Phase 8 Requirements - Live stream and market header

## Goal
Every screen updates the moment something changes, and the header always shows the state of the market.

## Scope
- `GET /api/v1/stream` (server-sent events): `hello`, `market` (symbol), `alert` (to one user), `ping`
- Order placement, cancel and settlement broadcast `market`; committed trades are published as `TradeExecuted` events
- `GET /market/summary`: synthetic index (10,000 at the previous close), change, breadth, trades, shares, value, session state in Doha time
- Frontend: one `LiveProvider` bumps a version; every poller refetches on it; polling stays as a safety net
