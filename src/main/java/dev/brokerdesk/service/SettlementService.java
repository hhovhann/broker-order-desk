package dev.brokerdesk.service;

import dev.brokerdesk.domain.*;
import dev.brokerdesk.engine.OrderBook;
import dev.brokerdesk.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Turns matches into money and shares, atomically: orders, accounts, holdings, trades and the last price move together or not at all. */
@Service
public class SettlementService {
    private final OrderRepository orders;
    private final AccountRepository accounts;
    private final HoldingRepository holdings;
    private final TradeRepository trades;
    private final InstrumentRepository instruments;
    private final AuditService audit;
    private final org.springframework.context.ApplicationEventPublisher events;

    public SettlementService(OrderRepository orders, AccountRepository accounts, HoldingRepository holdings, TradeRepository trades,
                             InstrumentRepository instruments, AuditService audit, org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.orders = orders; this.accounts = accounts; this.holdings = holdings; this.trades = trades; this.instruments = instruments; this.audit = audit;
    }

    @Transactional
    public void applyMatch(long takerOrderId, OrderBook.Match match) {
        BrokerOrder taker = orders.findById(takerOrderId).orElseThrow();
        Set<Long> ids = new TreeSet<>();
        ids.add(taker.getAccountId());
        match.fills().forEach(f -> ids.add(f.makerAccountId()));
        Map<Long, Account> locked = new HashMap<>();
        accounts.lockAll(ids).forEach(a -> locked.put(a.getId(), a));

        Instrument instrument = instruments.findById(taker.getSymbol()).orElseThrow();
        for (OrderBook.Fill f : match.fills()) {
            BrokerOrder maker = orders.findById(f.makerOrderId()).orElseThrow();
            BrokerOrder buy = f.takerSide() == Side.BUY ? taker : maker, sell = buy == taker ? maker : taker;
            Account buyer = locked.get(buy.getAccountId()), seller = locked.get(sell.getAccountId());
            long notional = Math.multiplyExact(f.priceMills(), f.quantity());

            buyer.release(buy.applyFill(f.quantity(), f.priceMills()));   // reservation was made at the limit price
            buyer.debit(notional);
            holding(buyer.getId(), taker.getSymbol()).add(f.quantity());
            sell.applyFill(f.quantity(), f.priceMills());
            holding(seller.getId(), taker.getSymbol()).deliver(f.quantity());
            seller.credit(notional);

            trades.save(new Trade(taker.getSymbol(), f.priceMills(), f.quantity(), buy.getId(), sell.getId(), buyer.getId(), seller.getId()));
            instrument.traded(f.priceMills());
            events.publishEvent(new TradeExecuted(taker.getSymbol(), f.priceMills(), f.quantity()));
            audit.record("FILL", taker.getSymbol() + " " + f.quantity() + " @ " + f.priceMills() + " buy#" + buy.getId() + " sell#" + sell.getId());
        }
        if (match.cancelledQty() > 0) {   // self-trade prevention removed the remainder
            release(taker, locked.get(taker.getAccountId()), match.cancelledQty(), RejectReason.SELF_TRADE_PREVENTED);
        }
    }

    @Transactional
    public void cancelOrder(long orderId) {
        BrokerOrder o = orders.findById(orderId).orElseThrow();
        Account a = accounts.lockById(o.getAccountId()).orElseThrow();
        release(o, a, o.remaining(), null);
        audit.record("CANCEL", "order#" + orderId);
    }

    /** Last resort when settlement threw: reject the order and hand back its reservation. Runs in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failOrder(long orderId) {
        BrokerOrder o = orders.findById(orderId).orElseThrow();
        if (!o.getStatus().isOpen()) return;
        Account a = accounts.lockById(o.getAccountId()).orElseThrow();
        release(o, a, o.remaining(), RejectReason.SYSTEM_ERROR);
        audit.record("SYSTEM_ERROR", "order#" + orderId + " rejected after settlement failure");
    }

    private void release(BrokerOrder o, Account account, long remainingQty, RejectReason reason) {
        long buyReserve = o.cancel(reason);
        if (o.getSide() == Side.BUY) account.release(buyReserve);
        else holding(account.getId(), o.getSymbol()).release(remainingQty);
    }

    private Holding holding(long accountId, String symbol) {
        return holdings.findByAccountIdAndSymbol(accountId, symbol).orElseGet(() -> holdings.save(new Holding(accountId, symbol, 0)));
    }
}
