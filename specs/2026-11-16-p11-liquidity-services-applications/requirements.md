# Phase 11 Requirements - Liquidity view, services, open account

## Goal
The liquidity role has its own view, and the service pages tell the truth about what exists.

## Scope
- `GET /liquidity/members` and `GET /liquidity/{symbol}/activity` (LP buy and sell against market buy and sell)
- Services: margin terms from live rules, banks by rail, institutions page that marks FIX and direct market access as NOT built, tariff (synthetic), profit purification calculator (illustration)
- Open account: `POST /public/applications` (validated, 5 per minute per client), `GET /admin/applications`
