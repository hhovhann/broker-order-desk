package dev.brokerdesk.engine;

import dev.brokerdesk.domain.*;
import dev.brokerdesk.repo.OrderRepository;
import dev.brokerdesk.service.SettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

/**
 * One single-threaded "actor" (a virtual thread behind a one-thread executor) owns each symbol's order book.
 * No locks around the book: every command for a symbol runs one after another on its own thread, different symbols run in parallel.
 * Matching and its database settlement happen in the same task, so settlement order equals matching order.
 * If settlement fails, the order is rejected and the book is rebuilt from the database, which is the source of truth.
 */
@Component
public class MatchingEngine {
    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    public record Ticket(long orderId, long accountId, Side side, long priceMills, long quantity) {}

    private final class Actor {
        final ExecutorService exec = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("book-", 0).factory());
        final OrderBook book = new OrderBook();
        /** Orders this actor is responsible for: recovered at start-up or submitted through it. Only touched on the actor thread. */
        final java.util.Set<Long> known = new java.util.HashSet<>();
        Actor(String symbol) { exec.execute(() -> rebuild(symbol, this, true)); }   // first task: recover open orders from the database
    }

    private final Map<String, Actor> actors = new ConcurrentHashMap<>();
    private final OrderRepository orders;
    private final SettlementService settlement;

    public MatchingEngine(OrderRepository orders, SettlementService settlement) {
        this.orders = orders;
        this.settlement = settlement;
    }

    private Actor actor(String symbol) { return actors.computeIfAbsent(symbol, Actor::new); }

    /**
     * Completes once the symbol's book has been recovered from the database. Callers must wait for it BEFORE saving a new order,
     * otherwise recovery could pick the new, not yet matched order up and rest it without matching it.
     */
    public CompletableFuture<Void> ready(String symbol) {
        return CompletableFuture.runAsync(() -> { }, actor(symbol).exec);
    }

    public CompletableFuture<Void> place(String symbol, Ticket t) {
        Actor a = actor(symbol);
        return CompletableFuture.runAsync(() -> {
            a.known.add(t.orderId);
            OrderBook.Match match = a.book.submit(t.orderId, t.accountId, t.side, t.priceMills, t.quantity);
            try {
                settlement.applyMatch(t.orderId, match);
            } catch (RuntimeException e) {
                log.error("settlement failed for order {}; rejecting it and rebuilding the {} book", t.orderId, symbol, e);
                settlement.failOrder(t.orderId);
                rebuild(symbol, a, false);
                throw e;
            }
        }, a.exec);
    }

    /** True if the order was resting and is now cancelled; false if it was not in the book (already filled, or never rested). */
    public CompletableFuture<Boolean> cancel(String symbol, long orderId) {
        Actor a = actor(symbol);
        return CompletableFuture.supplyAsync(() -> {
            if (a.book.cancel(orderId).isEmpty()) return false;
            settlement.cancelOrder(orderId);
            return true;
        }, a.exec);
    }

    public CompletableFuture<OrderBook.Depth> depth(String symbol, int levels) {
        Actor a = actor(symbol);
        return CompletableFuture.supplyAsync(() -> a.book.depth(levels), a.exec);
    }

    /** Rebuilds the book from the database. After a failure only orders this actor already handled are restored: orders still queued will arrive on their own. */
    private void rebuild(String symbol, Actor a, boolean initial) {
        a.book.clear();
        for (BrokerOrder o : orders.findBySymbolAndStatusInOrderByIdAsc(symbol, OrderStatus.OPEN)) {   // id order = time priority
            if (!initial && !a.known.contains(o.getId())) continue;
            a.known.add(o.getId());
            a.book.rest(o.getId(), o.getAccountId(), o.getSide(), o.getLimitPriceMills(), o.remaining());
        }
    }

    @PreDestroy
    void shutdown() { actors.values().forEach(a -> a.exec.shutdown()); }
}
