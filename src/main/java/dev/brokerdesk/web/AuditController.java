package dev.brokerdesk.web;

import dev.brokerdesk.repo.AuditRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Append-only audit trail of orders, fills, cancels and failures. Who did what, when, under which correlation id. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    public record AuditLine(String at, String actor, String action, String detail, String correlationId) {}
    private final AuditRepository audit;
    public AuditController(AuditRepository audit) { this.audit = audit; }

    @GetMapping("/audit")
    List<AuditLine> audit() {
        return audit.findTop200ByOrderByIdDesc().stream().map(a -> new AuditLine(a.getAt().toString(), a.getActor(), a.getAction(), a.getDetail(), a.getCorrelationId())).toList();
    }
}
