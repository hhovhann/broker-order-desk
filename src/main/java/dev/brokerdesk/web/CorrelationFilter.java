package dev.brokerdesk.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/** Java 25 ScopedValue (final in 25): an immutable request context that cannot leak across virtual threads like a ThreadLocal can. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter implements Filter {
    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        String given = ((HttpServletRequest) req).getHeader("X-Correlation-Id");
        String cid = given != null && given.matches("[A-Za-z0-9-]{1,64}") ? given : UUID.randomUUID().toString();
        ((HttpServletResponse) res).setHeader("X-Correlation-Id", cid);
        try {
            ScopedValue.where(CORRELATION_ID, cid).call(() -> {
                MDC.put("cid", cid);
                try { chain.doFilter(req, res); return null; } finally { MDC.remove("cid"); }
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
