# Phase 4 Requirements - Market data, statement, audit

## Goal
Traders see a live price board and their own statement; administrators see who did what and when.

## Scope
- `GET /market/board`: offer, bid, last, trades, average (VWAP), shares, value, previous close, high, low, change, change in bps, 30-point trend
- `GET /accounts/me/trades`: the caller's executions, newest first
- `GET /admin/audit` (ADMIN): append-only events with actor, action, detail, correlation id (a `ScopedValue` set per request)
- `GET /me` (roles) and `GET /market/rules` (limits for the ticket hints)
