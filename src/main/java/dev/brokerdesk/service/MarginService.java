package dev.brokerdesk.service;

import dev.brokerdesk.config.BrokerProperties;
import dev.brokerdesk.domain.*;
import dev.brokerdesk.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Margin position of an account: positions marked at the last traded price. A margin call is raised when equity falls under the maintenance level. */
@Service
@Transactional(readOnly = true)
public class MarginService {
    public record Position(String symbol, long quantity, long lastPriceMills, long valueMills) {}
    public record Report(long accountId, String owner, long cashMills, long loanMills, long marketValueMills, long equityMills,
                         long equityBps, long maintenanceBps, long buyingPowerMills, long reservedMills, boolean marginCall, List<Position> positions) {}

    private final AccountRepository accounts;
    private final HoldingRepository holdings;
    private final InstrumentRepository instruments;
    private final BrokerProperties props;

    public MarginService(AccountRepository accounts, HoldingRepository holdings, InstrumentRepository instruments, BrokerProperties props) {
        this.accounts = accounts; this.holdings = holdings; this.instruments = instruments; this.props = props;
    }

    public Report report(long accountId) {
        Account a = accounts.findById(accountId).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "account not found"));
        Map<String, Instrument> prices = instruments.findAll().stream().collect(Collectors.toMap(Instrument::getSymbol, i -> i));
        List<Position> positions = holdings.findByAccountId(accountId).stream().filter(h -> h.getQuantity() > 0)
                .map(h -> { long px = prices.get(h.getSymbol()).getLastPriceMills(); return new Position(h.getSymbol(), h.getQuantity(), px, Math.multiplyExact(px, h.getQuantity())); }).toList();
        long marketValue = positions.stream().mapToLong(Position::valueMills).sum();
        long equity = a.getCashMills() + marketValue;
        long equityBps = marketValue == 0 ? 10_000 : equity * 10_000 / marketValue;
        long maintenanceBps = props.maintenanceMarginPercent() * 100L;
        boolean call = a.loanMills() > 0 && equityBps < maintenanceBps;
        return new Report(accountId, a.getOwner(), a.getCashMills(), a.loanMills(), marketValue, equity, equityBps, maintenanceBps, a.buyingPower(), a.getReservedMills(), call, positions);
    }

    public List<Report> marginCalls() {
        return accounts.findAll().stream().map(a -> report(a.getId())).filter(Report::marginCall).toList();
    }
}
