# Architecture

```
 React dashboard (served by the same jar)
        |  HTTPS + Basic (OIDC in production)
 Spring MVC controllers  --  Spring Security (roles, ownership)  --  ScopedValue correlation id
        |
 OrderService ── tx 1: idempotency, risk check, reserve, persist ──────────────► PostgreSQL
        |                                                                          ▲
        |  engine.ready(symbol) first, then:                                        |
        └──► MatchingEngine: one virtual-thread actor per symbol                    |
                OrderBook (price-time, in memory)  ──► SettlementService ──── tx 2 ─┘
                (match and settle in the same task)    orders, accounts, holdings, trades, audit
```

## Why it is built this way

**Single writer per symbol.** Matching is inherently sequential per instrument. One thread per book removes locks from the hot path and makes behaviour deterministic. Different symbols run in parallel. Virtual threads make one actor per symbol cheap.

**The database stays the source of truth.** The book is a cache of open orders. It is rebuilt at first use, and after a settlement failure. Risk reservations (cash and shares) are stored, so a crash cannot lose them.

**Reserve, match, settle.** A buy reserves buying power at its limit price, a sell reserves shares. A fill releases the reservation exactly and moves cash and shares at the maker's price, so a better price is returned, never kept. Because reservations are exact, the invariants are checkable: cash conserved, shares conserved, each account's reservation equals its open orders, and the book equals the database (`EngineInvariantsTest`).

**Deadlock-free settlement.** Accounts are locked with `PESSIMISTIC_WRITE` in id order; holdings are only changed after their account is locked.

**Idempotency at the edge.** `(account, clientOrderId)` is unique; a retry returns the original order; a different body under the same id is rejected.

**Security.** Roles decide what a user may call; ownership checks decide whose data. Risk rejections are business outcomes (`201` with a reason); input errors are `400`; forbidden is `403`.

## Scaling honestly
One active process per symbol shard. In Kubernetes that is `replicas: 1` with `Recreate`. To scale out: shard symbols across deployments behind a routing layer, and publish market data through an outbox to a broker. Both are in the backlog; neither is claimed here.

## Failure modes that are tested
| Failure | What happens | Test |
|---|---|---|
| Client retries an order | Same order returned, no double charge | `BrokerApiTest` |
| Two threads, same order id | Unique constraint; the loser re-reads the winner | covered by the unique index |
| Database fails during settlement | Order rejected, reservation released, book rebuilt, next order works | `SettlementFailureTest` |
| Process restart | Book rebuilt from open orders | `EngineInvariantsTest` |
| Heavy concurrent load | Money, shares and reservations conserved | `EngineInvariantsTest` |
