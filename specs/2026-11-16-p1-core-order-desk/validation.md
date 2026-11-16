# Phase 1 Validation - Core order desk

- [ ] `OrderBookTest` (7 tests): price priority, time priority, partial fill, limit respected, self-trade, cancel once, best bid
- [ ] `RiskServiceTest` (7 Mockito tests): every rejection and both reservations
- [ ] `BrokerApiTest`: end-to-end over HTTP with real security, exact balances (cash -150,000.00, equity 40.00%, margin call at 32.02%)
- [ ] Validation errors are 400 with field names; anonymous is 401; wrong role and not-the-owner are 403
