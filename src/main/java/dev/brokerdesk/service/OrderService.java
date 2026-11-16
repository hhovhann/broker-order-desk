package dev.brokerdesk.service;

import dev.brokerdesk.domain.*;
import dev.brokerdesk.engine.MatchingEngine;
import dev.brokerdesk.repo.InstrumentRepository;
import dev.brokerdesk.repo.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Order lifecycle. Step 1 (one transaction): idempotency, risk check, reservation, persist.
 * Step 2 (engine actor): match and settle. Step 3: read back the final state. The caller gets a CompletableFuture.
 */
@Service
public class OrderService {
    public record PlaceCommand(String clientOrderId, String symbol, Side side, long quantity, long limitPriceMills) {}

    private static final Duration ENGINE_TIMEOUT = Duration.ofSeconds(10);

    private final OrderRepository orders;
    private final InstrumentRepository instruments;
    private final RiskService risk;
    private final MatchingEngine engine;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final StreamHub hub;

    public OrderService(OrderRepository orders, InstrumentRepository instruments, RiskService risk, MatchingEngine engine, AuditService audit, PlatformTransactionManager tm, StreamHub hub) {
        this.hub = hub;
        this.orders = orders; this.instruments = instruments; this.risk = risk; this.engine = engine; this.audit = audit; this.tx = new TransactionTemplate(tm);
    }

    private record Accepted(BrokerOrder order, boolean fresh) {}

    public CompletableFuture<OrderView> place(long accountId, PlaceCommand c) {
        if (instruments.existsById(c.symbol())) engine.ready(c.symbol()).join();   // recovery must finish before the order exists in the database
        Accepted acc;
        try {
            acc = tx.execute(s -> accept(accountId, c));
        } catch (DataIntegrityViolationException duplicate) {   // two identical clientOrderIds raced: the loser re-reads the winner
            BrokerOrder winner = orders.findByAccountIdAndClientOrderId(accountId, c.clientOrderId()).orElseThrow();
            sameRequestOrThrow(winner, c);
            return CompletableFuture.completedFuture(OrderView.of(winner));
        }
        BrokerOrder order = acc.order();
        if (!acc.fresh() || order.getStatus() != OrderStatus.NEW) { if (acc.fresh()) hub.broadcast("market", c.symbol()); return CompletableFuture.completedFuture(OrderView.of(order)); }

        long id = order.getId();
        return engine.place(c.symbol(), new MatchingEngine.Ticket(id, accountId, c.side(), c.limitPriceMills(), c.quantity()))
                .orTimeout(ENGINE_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .thenApply(v -> { hub.broadcast("market", c.symbol()); return OrderView.of(orders.findById(id).orElseThrow()); });
    }

    private Accepted accept(long accountId, PlaceCommand c) {
        var existing = orders.findByAccountIdAndClientOrderId(accountId, c.clientOrderId());
        if (existing.isPresent()) { sameRequestOrThrow(existing.get(), c); return new Accepted(existing.get(), false); }

        BrokerOrder order = new BrokerOrder(accountId, c.clientOrderId(), c.symbol(), c.side(), c.quantity(), c.limitPriceMills());
        RiskService.Decision d = risk.checkAndReserve(accountId, c.symbol(), c.side(), c.quantity(), c.limitPriceMills());
        switch (d) {
            case RiskService.Decision.Accepted a -> order.reservedBuy(a.reservedMills());
            case RiskService.Decision.Rejected r -> order.rejected(r.reason());
        }
        orders.saveAndFlush(order);
        audit.record(order.getStatus() == OrderStatus.REJECTED ? "ORDER_REJECTED" : "ORDER_ACCEPTED",
                c.side() + " " + c.quantity() + " " + c.symbol() + " @ " + c.limitPriceMills() + " cid=" + c.clientOrderId()
                        + (order.getRejectReason() != null ? " reason=" + order.getRejectReason() : ""));
        return new Accepted(order, true);
    }

    private static void sameRequestOrThrow(BrokerOrder o, PlaceCommand c) {
        if (!o.getSymbol().equals(c.symbol()) || o.getSide() != c.side() || o.getQuantity() != c.quantity() || o.getLimitPriceMills() != c.limitPriceMills())
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "clientOrderId already used with different order details");
    }

    public CompletableFuture<OrderView> cancel(long accountId, long orderId) {
        BrokerOrder o = orders.findById(orderId).filter(x -> x.getAccountId() == accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order not found"));
        if (!o.getStatus().isOpen()) throw new ApiException(HttpStatus.CONFLICT, "order is " + o.getStatus() + ", nothing to cancel");
        return engine.cancel(o.getSymbol(), orderId).thenApply(done -> {
            if (!done) throw new ApiException(HttpStatus.CONFLICT, "order is no longer resting");
            hub.broadcast("market", o.getSymbol());
            return OrderView.of(orders.findById(orderId).orElseThrow());
        });
    }

    public OrderView get(long accountId, long orderId) {
        return orders.findById(orderId).filter(x -> x.getAccountId() == accountId).map(OrderView::of)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order not found"));
    }

    public List<OrderView> recent(long accountId) {
        return orders.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(0, 30)).stream().map(OrderView::of).toList();
    }
}
