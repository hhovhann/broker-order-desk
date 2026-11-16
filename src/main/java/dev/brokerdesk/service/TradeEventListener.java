package dev.brokerdesk.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Reacts only to trades that really committed. */
@Component
public class TradeEventListener {
    private final AlertService alerts;
    public TradeEventListener(AlertService alerts) { this.alerts = alerts; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTrade(TradeExecuted t) { alerts.evaluate(t.symbol(), t.priceMills()); }
}
