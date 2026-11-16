# Phase 1 Plan - Core order desk

1. Model first, framework second: `OrderBook` is plain Java with no Spring so it is trivially unit-tested
2. Flyway `V1__init.sql` with check constraints (`reserved_qty <= quantity`)
3. Risk runs inside the order transaction; the engine step is separate and asynchronous
