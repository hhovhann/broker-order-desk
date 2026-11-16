package dev.brokerdesk.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteService {
    private final JdbcClient jdbc;
    public FavoriteService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<String> list(String owner) { return jdbc.sql("select symbol from favorite where owner = :o order by symbol").param("o", owner).query(String.class).list(); }

    public void add(String owner, String symbol) {
        if (jdbc.sql("select count(*) from instrument where symbol = :s").param("s", symbol).query(Long.class).single() == 0) throw new ApiException(HttpStatus.NOT_FOUND, "unknown symbol");
        try { jdbc.sql("insert into favorite (owner, symbol) values (:o, :s)").param("o", owner).param("s", symbol).update(); }
        catch (DuplicateKeyException already) { /* idempotent */ }
    }

    public void remove(String owner, String symbol) { jdbc.sql("delete from favorite where owner = :o and symbol = :s").param("o", owner).param("s", symbol).update(); }
}
