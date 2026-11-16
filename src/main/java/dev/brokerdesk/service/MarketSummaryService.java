package dev.brokerdesk.service;

import dev.brokerdesk.repo.InstrumentRepository;
import dev.brokerdesk.repo.TradeRepository;
import org.springframework.stereotype.Service;

import java.time.*;

/** Header numbers: synthetic equal-weight index, breadth, session totals and the trading-session state (Qatar time, Sunday to Thursday). */
@Service
public class MarketSummaryService {
    public record Summary(String session, boolean simulatedOpen, long indexPoints, long indexChangeBps, long trades, long shares, long valueMills, int advancers, int decliners, int unchanged, String asOf) {}
    private static final ZoneId DOHA = ZoneId.of("Asia/Qatar");
    private static final LocalTime OPEN = LocalTime.of(9, 30), CLOSE = LocalTime.of(13, 0);

    private final InstrumentRepository instruments;
    private final TradeRepository trades;
    private final Clock clock;

    public MarketSummaryService(InstrumentRepository instruments, TradeRepository trades, Clock clock) { this.instruments = instruments; this.trades = trades; this.clock = clock; }

    /** Real session state by the clock. The demo UI labels itself "simulated session" because order entry is not blocked outside hours. */
    public String sessionState(ZonedDateTime t) {
        DayOfWeek d = t.getDayOfWeek();
        boolean tradingDay = d != DayOfWeek.FRIDAY && d != DayOfWeek.SATURDAY;
        LocalTime now = t.toLocalTime();
        return tradingDay && !now.isBefore(OPEN) && now.isBefore(CLOSE) ? "OPEN" : "CLOSED";
    }

    public Summary summary() {
        var all = instruments.findAll();
        long sum = 0; int up = 0, down = 0, flat = 0;
        for (var i : all) {
            sum += i.getLastPriceMills() * MarketHistoryService.INDEX_BASE / i.getReferencePriceMills();
            if (i.getLastPriceMills() > i.getReferencePriceMills()) up++; else if (i.getLastPriceMills() < i.getReferencePriceMills()) down++; else flat++;
        }
        long points = all.isEmpty() ? MarketHistoryService.INDEX_BASE : sum / all.size();
        long shares = 0, value = 0, count = 0;
        for (var s : trades.statsBySymbol()) { shares += s.getShares(); value += s.getValueMills(); count += s.getTrades(); }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(DOHA));
        return new Summary(sessionState(now), true, points, (points - MarketHistoryService.INDEX_BASE) * 10_000 / MarketHistoryService.INDEX_BASE, count, shares, value, up, down, flat, now.toString());
    }
}
