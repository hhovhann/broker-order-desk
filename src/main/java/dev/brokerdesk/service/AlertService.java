package dev.brokerdesk.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Price alerts: ABOVE fires when a trade prints at or over the target, BELOW at or under. Each alert fires once. */
@Service
public class AlertService {
    public record Alert(long id, String symbol, String direction, long priceMills, String status, String createdAt, String triggeredAt, Long triggeredPriceMills) {}
    private static final int MAX_ACTIVE = 20;

    private final JdbcClient jdbc;
    private final StreamHub hub;

    public AlertService(JdbcClient jdbc, StreamHub hub) { this.jdbc = jdbc; this.hub = hub; }

    @Transactional
    public Alert create(String owner, String symbol, String direction, long priceMills) {
        if (!direction.equals("ABOVE") && !direction.equals("BELOW")) throw new ApiException(HttpStatus.BAD_REQUEST, "direction must be ABOVE or BELOW");
        if (priceMills <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "price must be positive");
        if (jdbc.sql("select count(*) from instrument where symbol = :s").param("s", symbol).query(Long.class).single() == 0) throw new ApiException(HttpStatus.NOT_FOUND, "unknown symbol");
        long active = jdbc.sql("select count(*) from price_alert where owner = :o and status = 'ACTIVE'").param("o", owner).query(Long.class).single();
        if (active >= MAX_ACTIVE) throw new ApiException(HttpStatus.CONFLICT, "at most " + MAX_ACTIVE + " active alerts");
        jdbc.sql("insert into price_alert (owner, symbol, direction, price_mills, status, created_at) values (:o, :s, :d, :p, 'ACTIVE', current_timestamp)")
                .param("o", owner).param("s", symbol).param("d", direction).param("p", priceMills).update();
        return list(owner).get(0);
    }

    public List<Alert> list(String owner) {
        return jdbc.sql("select id, symbol, direction, price_mills, status, created_at, triggered_at, triggered_price_mills from price_alert where owner = :o order by id desc limit 50")
                .param("o", owner).query((rs, i) -> new Alert(rs.getLong("id"), rs.getString("symbol"), rs.getString("direction"), rs.getLong("price_mills"), rs.getString("status"),
                        rs.getTimestamp("created_at").toString(), rs.getTimestamp("triggered_at") == null ? null : rs.getTimestamp("triggered_at").toString(),
                        rs.getObject("triggered_price_mills") == null ? null : rs.getLong("triggered_price_mills"))).list();
    }

    @Transactional
    public void delete(String owner, long id) {
        if (jdbc.sql("delete from price_alert where id = :i and owner = :o").param("i", id).param("o", owner).update() == 0) throw new ApiException(HttpStatus.NOT_FOUND, "alert not found");
    }

    /** Called after a trade commits, in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluate(String symbol, long priceMills) {
        var hits = jdbc.sql("select id, owner, direction, price_mills from price_alert where symbol = :s and status = 'ACTIVE' and ((direction = 'ABOVE' and :p >= price_mills) or (direction = 'BELOW' and :p <= price_mills))")
                .param("s", symbol).param("p", priceMills).query((rs, i) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)}).list();
        for (Object[] h : hits) {
            if (jdbc.sql("update price_alert set status = 'TRIGGERED', triggered_at = current_timestamp, triggered_price_mills = :p where id = :i and status = 'ACTIVE'").param("p", priceMills).param("i", h[0]).update() == 1)
                hub.sendTo((String) h[1], "alert", symbol + " " + (priceMills / 1000.0) + " crossed " + (((String) h[2]).equals("ABOVE") ? "above " : "below ") + ((Long) h[3] / 1000.0));
        }
    }
}
