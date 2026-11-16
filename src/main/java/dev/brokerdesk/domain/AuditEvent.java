package dev.brokerdesk.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "audit_event")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private LocalDateTime at = LocalDateTime.now(ZoneOffset.UTC);
    private String actor;
    private String action;
    private String detail;
    @Column(name = "correlation_id") private String correlationId;

    protected AuditEvent() {}
    public AuditEvent(String actor, String action, String detail, String correlationId) {
        this.actor = actor; this.action = action; this.detail = detail; this.correlationId = correlationId;
    }
    public LocalDateTime getAt() { return at; }
    public String getCorrelationId() { return correlationId; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
}
