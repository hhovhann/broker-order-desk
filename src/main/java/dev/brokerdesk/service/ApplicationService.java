package dev.brokerdesk.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** "Open account" requests from the public site. Small, validated, rate limited per client; reviewed by an administrator. */
@Service
public class ApplicationService {
    public static final Set<String> TYPES = Set.of("INSIDE_QATAR", "OUTSIDE_QATAR", "INSTITUTION", "AGENT");
    public record Application(long id, String fullName, String email, String type, String note, String status, String createdAt) {}
    private final JdbcClient jdbc;
    private final Map<String, Deque<Long>> recent = new ConcurrentHashMap<>();

    public ApplicationService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public long submit(String client, String fullName, String email, String type, String note) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> q = recent.computeIfAbsent(client, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= 5) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too many requests, try again in a minute");
            q.addLast(now);
        }
        if (!TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "unknown applicant type");
        jdbc.sql("insert into account_application (full_name, email, applicant_type, note, status, created_at) values (:n, :e, :t, :o, 'PENDING', current_timestamp)")
                .param("n", fullName).param("e", email).param("t", type).param("o", note).update();
        return jdbc.sql("select max(id) from account_application").query(Long.class).single();
    }

    public List<Application> list() {
        return jdbc.sql("select id, full_name, email, applicant_type, note, status, created_at from account_application order by id desc limit 100")
                .query((rs, i) -> new Application(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toString())).list();
    }
}
