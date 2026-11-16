package dev.brokerdesk.service;

import dev.brokerdesk.domain.AuditEvent;
import dev.brokerdesk.repo.AuditRepository;
import dev.brokerdesk.web.CorrelationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRepository repo;
    public AuditService(AuditRepository repo) { this.repo = repo; }

    /** Actor and correlation id come from the request (SecurityContext, ScopedValue); on engine threads the actor is "engine". */
    public void record(String action, String detail) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String actor = a != null ? a.getName() : "engine";
        String cid = CorrelationFilter.CORRELATION_ID.isBound() ? CorrelationFilter.CORRELATION_ID.get() : null;
        repo.save(new AuditEvent(actor, action, detail.length() > 300 ? detail.substring(0, 300) : detail, cid));
    }
}
