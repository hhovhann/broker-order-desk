# Mission

A work sample for a **Senior Java Software Engineer** role at a Qatar stock-exchange brokerage (margin financing, liquidity provision). It is a small, honest order desk: orders in, risk checks, matching, settlement, margin monitoring, an audit trail, and a trading dashboard.

It exists to show, in code and tests rather than claims:
- backend engineering with Spring Boot, Spring MVC, Spring Data and Spring Security on Java 25
- concurrency and asynchronous design that is **proved** by tests, not asserted
- domain-driven design where invariants live in the domain objects
- delivery hygiene: Maven, Docker, Kubernetes, CI, tests at every level, specs and history kept next to the code

## Not goals
- It is not a real exchange connection, not FIX/DMA, not market data, not advice. All symbols, prices, balances and people are synthetic.
- It does not copy any real broker's brand or data. The dashboard borrows only the *kind* of layout traders expect (price board, depth, order ticket).
