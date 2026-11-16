package dev.brokerdesk;

import dev.brokerdesk.domain.*;
import dev.brokerdesk.engine.MatchingEngine;
import dev.brokerdesk.engine.OrderBook;
import dev.brokerdesk.repo.*;
import dev.brokerdesk.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Hammers the engine from many threads and then checks that money and shares are conserved and every reservation is accounted for. */
@SpringBootTest
class EngineInvariantsTest {
    static final int ACCOUNTS = 6, THREADS = 6, ORDERS_PER_THREAD = 150;
    @Autowired OrderService orders;
    @Autowired AccountRepository accounts;
    @Autowired HoldingRepository holdings;
    @Autowired InstrumentRepository instruments;
    @Autowired OrderRepository orderRepo;
    @Autowired TradeRepository trades;
    @Autowired MatchingEngine engine;

    @Test void concurrentOrdersConserveCashSharesAndReservations() throws Exception {
        instruments.save(new Instrument("STRESS", "stress", 10_000));
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            Account a = accounts.save(new Account("stress" + i, 1_000_000_000L, 0));
            holdings.save(new Holding(a.getId(), "STRESS", 100_000));
            ids.add(a.getId());
        }
        long cashBefore = 1_000_000_000L * ACCOUNTS, sharesBefore = 100_000L * ACCOUNTS;

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> work = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int seed = t;
                work.add(pool.submit(() -> {
                    Random rnd = new Random(seed);
                    for (int i = 0; i < ORDERS_PER_THREAD; i++) {
                        long acct = ids.get(rnd.nextInt(ACCOUNTS));
                        Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
                        var view = orders.place(acct, new OrderService.PlaceCommand("c-" + seed + "-" + i, "STRESS", side, 1 + rnd.nextInt(50), 9_500 + rnd.nextInt(1_001))).join();
                        if (view.status() == OrderStatus.NEW && rnd.nextInt(5) == 0) {
                            try { orders.cancel(acct, view.orderId()).join(); } catch (RuntimeException raceWithFill) { /* filled in between: fine */ }
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> f : work) f.get(120, TimeUnit.SECONDS);
        }

        var accts = accounts.findAllById(ids);
        assertThat(accts.stream().mapToLong(Account::getCashMills).sum()).as("cash is conserved").isEqualTo(cashBefore);
        var held = ids.stream().flatMap(id -> holdings.findByAccountId(id).stream()).filter(h -> h.getSymbol().equals("STRESS")).toList();
        assertThat(held.stream().mapToLong(Holding::getQuantity).sum()).as("shares are conserved").isEqualTo(sharesBefore);

        var all = orderRepo.findBySymbolAndStatusInOrderByIdAsc("STRESS", EnumSet.allOf(OrderStatus.class));
        var open = all.stream().filter(o -> o.getStatus().isOpen()).toList();
        for (Account a : accts) {
            long expected = open.stream().filter(o -> o.getAccountId() == a.getId() && o.getSide() == Side.BUY).mapToLong(BrokerOrder::getReservedMills).sum();
            assertThat(a.getReservedMills()).as("buy reservation of %s equals its open buy orders", a.getOwner()).isEqualTo(expected);
        }
        for (Holding h : held) {
            long expected = open.stream().filter(o -> o.getAccountId() == h.getAccountId() && o.getSide() == Side.SELL).mapToLong(BrokerOrder::remaining).sum();
            assertThat(h.getReservedQty()).as("share reservation equals open sell orders").isEqualTo(expected);
        }
        long boughtQty = all.stream().filter(o -> o.getSide() == Side.BUY).mapToLong(BrokerOrder::getFilledQty).sum();
        long soldQty = all.stream().filter(o -> o.getSide() == Side.SELL).mapToLong(BrokerOrder::getFilledQty).sum();
        long tradedQty = trades.findAll().stream().filter(t -> t.getSymbol().equals("STRESS")).mapToLong(Trade::getQuantity).sum();
        assertThat(boughtQty).isEqualTo(soldQty).isEqualTo(tradedQty).isPositive();

        // The in-memory book equals the open orders in the database, level by level.
        OrderBook.Depth depth = engine.depth("STRESS", 1_000).join();
        Map<Long, Long> bookBids = depth.bids().stream().collect(Collectors.toMap(OrderBook.Level::priceMills, OrderBook.Level::quantity));
        Map<Long, Long> dbBids = open.stream().filter(o -> o.getSide() == Side.BUY).collect(Collectors.groupingBy(BrokerOrder::getLimitPriceMills, Collectors.summingLong(BrokerOrder::remaining)));
        assertThat(bookBids).isEqualTo(dbBids);
    }

    @Test void aRestartedEngineRebuildsTheBookFromTheDatabase() {
        instruments.save(new Instrument("RECOV", "recover", 10_000));
        Account seller = accounts.save(new Account("recov-s", 0, 0));
        holdings.save(new Holding(seller.getId(), "RECOV", 1_000));
        var o = orders.place(seller.getId(), new OrderService.PlaceCommand("r1", "RECOV", Side.SELL, 300, 10_200)).join();
        assertThat(o.status()).isEqualTo(OrderStatus.NEW);

        var freshEngine = new MatchingEngine(orderRepo, null);   // a new process: empty memory, same database
        var depth = freshEngine.depth("RECOV", 5).join();
        assertThat(depth.asks()).singleElement().satisfies(l -> { assertThat(l.priceMills()).isEqualTo(10_200); assertThat(l.quantity()).isEqualTo(300); });
    }
}
