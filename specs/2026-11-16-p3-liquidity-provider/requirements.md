# Phase 3 Requirements - Liquidity provider

## Goal
A liquidity provider can post a bid and an offer together, through exactly the same risk, matching and settlement as any order.

## Scope
- `POST /api/v1/quotes` (role LIQUIDITY_PROVIDER): bid below offer, both placed with derived client ids
- Liquidity providers can also read their account and trade normally
