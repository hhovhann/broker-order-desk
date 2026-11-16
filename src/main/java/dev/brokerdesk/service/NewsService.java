package dev.brokerdesk.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsService {
    public record Item(long id, String kind, String symbol, String date, String title, String body) {}
    private final JdbcClient jdbc;
    public NewsService(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** kind NEWS or DISCLOSURE; symbol null or "MARKET" means market-wide items (no symbol). */
    public List<Item> list(String kind, String symbol) {
        boolean market = symbol == null || symbol.isBlank() || symbol.equals("MARKET");
        return jdbc.sql("select id, kind, symbol, published_on, title, body from news_item where kind = :k and (" + (market ? "1 = 1" : "symbol = :s") + ") order by published_on desc, id desc limit 40")
                .param("k", kind).params(market ? java.util.Map.<String, Object>of() : java.util.Map.<String, Object>of("s", symbol))
                .query((rs, i) -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDate(4).toString(), rs.getString(5), rs.getString(6))).list();
    }
}
