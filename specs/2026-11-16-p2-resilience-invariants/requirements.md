# Phase 2 Requirements - Resilience and invariants

## Goal
Retries, races, crashes and a failing database can never create or destroy money or shares.

## Scope
- Idempotent placement by `(account, clientOrderId)`; same id with different details is 422; a race on the same id is resolved by the unique constraint
- Single-threaded virtual-thread actor per symbol; `CompletableFuture` results with a timeout
- Recovery: the book is rebuilt from open orders at first use; `ready(symbol)` completes before a new order is saved
- Settlement failure: the order is rejected (`SYSTEM_ERROR`), its reservation released in a new transaction, the book rebuilt
- Conservation: cash and shares conserved; each account's reservation equals its open orders; book equals database
