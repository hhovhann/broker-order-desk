package dev.brokerdesk.service;

import dev.brokerdesk.config.BrokerProperties;
import dev.brokerdesk.domain.*;
import dev.brokerdesk.repo.*;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Pre-trade risk: runs inside the caller's transaction, rejects before the order can reach the book, reserves what the order needs. */
@Service
public class RiskService {
    public sealed interface Decision permits Decision.Accepted, Decision.Rejected {
        record Accepted(long reservedMills) implements Decision {}
        record Rejected(RejectReason reason) implements Decision {}
    }

    private final AccountRepository accounts;
    private final HoldingRepository holdings;
    private final InstrumentRepository instruments;
    private final BrokerProperties props;

    public RiskService(AccountRepository accounts, HoldingRepository holdings, InstrumentRepository instruments, BrokerProperties props) {
        this.accounts = accounts; this.holdings = holdings; this.instruments = instruments; this.props = props;
    }

    public Decision checkAndReserve(long accountId, String symbol, Side side, long quantity, long priceMills) {
        Optional<Instrument> inst = instruments.findById(symbol);
        if (inst.isEmpty()) return new Decision.Rejected(RejectReason.UNKNOWN_SYMBOL);
        long ref = inst.get().getReferencePriceMills();
        long low = ref * (100 - props.bandPercent()) / 100, high = ref * (100 + props.bandPercent()) / 100;
        if (priceMills < low || priceMills > high) return new Decision.Rejected(RejectReason.PRICE_OUTSIDE_BAND);
        long notional = Math.multiplyExact(priceMills, quantity);
        if (notional > props.maxOrderValueMills()) return new Decision.Rejected(RejectReason.MAX_ORDER_VALUE);

        Account account = accounts.lockById(accountId).orElseThrow();
        return switch (side) {
            case BUY -> {
                if (notional > account.buyingPower()) yield new Decision.Rejected(RejectReason.INSUFFICIENT_BUYING_POWER);
                account.reserve(notional);
                yield new Decision.Accepted(notional);
            }
            case SELL -> {
                Holding h = holdings.findByAccountIdAndSymbol(accountId, symbol).orElse(null);
                if (h == null || quantity > h.available()) yield new Decision.Rejected(RejectReason.INSUFFICIENT_SHARES);
                h.reserve(quantity);
                yield new Decision.Accepted(0);
            }
        };
    }
}
