package dev.brokerdesk.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Daily candles per symbol, a synthetic equal-weight INDEX built from them, and a normalised compare series. */
@Service
public class MarketHistoryService {
    public static final String INDEX = "INDEX";
    public static final long INDEX_BASE = 10_000;   // index points when every symbol sits at its previous close
    public record Candle(String date, long open, long high, long low, long close, long volume) {}
    public record Series(String symbol, List<long[]> points) {}   // [epochDay, valueInBps-of-start]

    private static final Map<String, Integer> DAYS = Map.of("1M", 30, "2M", 61, "3M", 91, "1Y", 365, "2Y", 730, "3Y", 1095, "ALL", Integer.MAX_VALUE);
    private final JdbcClient jdbc;

    public MarketHistoryService(JdbcClient jdbc) { this.jdbc = jdbc; }

    private LocalDate from(String range) {
        Integer d = DAYS.get(range.toUpperCase());
        if (d == null) throw new ApiException(HttpStatus.BAD_REQUEST, "range must be one of " + new TreeSet<>(DAYS.keySet()));
        return d == Integer.MAX_VALUE ? LocalDate.of(1970, 1, 1) : LocalDate.now().minusDays(d);
    }

    public List<Candle> candles(String symbol, String range) {
        LocalDate from = from(range);
        if (symbol.equals(INDEX)) return indexCandles(from);
        if (jdbc.sql("select count(*) from instrument where symbol = :s").param("s", symbol).query(Long.class).single() == 0) throw new ApiException(HttpStatus.NOT_FOUND, "unknown symbol");
        return jdbc.sql("select trade_date, open_mills, high_mills, low_mills, close_mills, volume from price_history where symbol = :s and trade_date >= :f order by trade_date")
                .param("s", symbol).param("f", java.sql.Date.valueOf(from))
                .query((rs, i) -> new Candle(rs.getDate(1).toString(), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6))).list();
    }

    /** Index points = 10,000 x average of (price / previous close) over all symbols, for each day. */
    private List<Candle> indexCandles(LocalDate from) {
        var rows = jdbc.sql("select h.trade_date, h.open_mills, h.high_mills, h.low_mills, h.close_mills, h.volume, i.reference_price_mills from price_history h join instrument i on i.symbol = h.symbol where h.trade_date >= :f order by h.trade_date")
                .param("f", java.sql.Date.valueOf(from)).query((rs, i) -> new long[]{rs.getDate(1).toLocalDate().toEpochDay(), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)}).list();
        Map<Long, List<long[]>> byDay = new TreeMap<>(rows.stream().collect(Collectors.groupingBy(r -> r[0])));
        List<Candle> out = new ArrayList<>();
        byDay.forEach((day, list) -> {
            long o = 0, h = 0, l = 0, c = 0, v = 0;
            for (long[] r : list) { o += r[1] * INDEX_BASE / r[6]; h += r[2] * INDEX_BASE / r[6]; l += r[3] * INDEX_BASE / r[6]; c += r[4] * INDEX_BASE / r[6]; v += r[5]; }
            int n = list.size();
            out.add(new Candle(LocalDate.ofEpochDay(day).toString(), o / n, h / n, l / n, c / n, v));
        });
        return out;
    }

    /** Percentage change since the start of the range, in basis points, so symbols of any price compare on one axis. */
    public List<Series> compare(List<String> symbols, String range) {
        if (symbols.isEmpty() || symbols.size() > 6) throw new ApiException(HttpStatus.BAD_REQUEST, "choose 1 to 6 symbols");
        List<Series> out = new ArrayList<>();
        for (String s : symbols) {
            var c = candles(s, range);
            if (c.isEmpty()) { out.add(new Series(s, List.of())); continue; }
            long base = c.get(0).close();
            out.add(new Series(s, c.stream().map(x -> new long[]{LocalDate.parse(x.date()).toEpochDay(), (x.close() - base) * 10_000 / base}).toList()));
        }
        return out;
    }
}
