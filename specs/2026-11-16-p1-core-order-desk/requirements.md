# Phase 1 Requirements - Core order desk

## Goal
An order goes in, is risk-checked, matched by price and time, settled atomically, and visible to the right people only.

## Scope
- Domain aggregates protect their invariants: `Account.reserve/release`, `Holding.reserve/deliver`, `BrokerOrder.applyFill/cancel`
- `OrderBook`: price priority, then time priority, trades at the maker price, partial fills, cancel, self-trade prevention
- `RiskService`: unknown symbol, daily price band, max order value, buying power (cash + margin line - reserved), shares available (no short selling)
- `SettlementService.applyMatch`: orders, accounts, holdings, trades and last price change in ONE transaction; accounts locked in id order
- `MarginService`: positions marked at last price, equity / market value against the maintenance level, margin call flag
- REST: `POST/GET/DELETE /api/v1/orders`, `/accounts/me`, `/accounts/{id}/margin`, `/risk/margin-calls`, `/market/**`; RFC 7807 errors; bean validation
- Security: stateless Basic, roles, `@PreAuthorize` with an ownership check, BCrypt
