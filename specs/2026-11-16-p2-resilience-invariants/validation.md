# Phase 2 Validation - Resilience and invariants

- [x] `EngineInvariantsTest.concurrentOrdersConserveCashSharesAndReservations`: cash conserved, shares conserved, reservations equal open orders, bought = sold = traded, book = database
- [x] `EngineInvariantsTest.aRestartedEngineRebuildsTheBookFromTheDatabase`
- [x] `SettlementFailureTest`: failed settlement rejects the order, releases the reservation, restores the maker, and the next order succeeds
- [x] `BrokerApiTest`: resending an order id returns the same order and does not charge twice; a different body is 422
