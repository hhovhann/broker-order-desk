# Decisions

- **D-1 Single writer per symbol.** Each order book is owned by one virtual-thread actor. No locks around the book, matching and settlement happen in the same task so settlement order equals matching order. Cost: one active engine process per symbol shard (Kubernetes `Recreate`, replicas 1).
- **D-2 The database is the source of truth.** The in-memory book can always be rebuilt from open orders. After a settlement failure the order is rejected and the book is rebuilt from orders this actor already handled.
- **D-3 Recovery before persistence.** `engine.ready(symbol)` must complete before a new order is saved, otherwise recovery could rest a not-yet-matched order without matching it. Found by `BrokerApiTest` (a first order rested twice); fixed 2026-11-16.
- **D-4 Reserve up front.** Buys reserve buying power at the limit price, sells reserve shares. Fills release the reservation exactly; price improvement therefore returns money, never loses it.
- **D-5 Money is `long` mills (1/1000 QAR), never floating point.** Formatting happens only in the UI.
- **D-6 Lock accounts in id order** (`lockAll ... order by id`) so concurrent settlements cannot deadlock.
- **D-7 Risk rejections are business outcomes**, returned as `201` with `status=REJECTED` and a reason, not as HTTP errors. Malformed input is `400`, ownership violations `403`.
- **D-8 No UI or chart library.** The charts are small SVG components; fewer moving parts, no supply-chain surface.
- **D-9 Synthetic everything.** Invented tickers and names, so nothing can be mistaken for real market data or a real broker.
