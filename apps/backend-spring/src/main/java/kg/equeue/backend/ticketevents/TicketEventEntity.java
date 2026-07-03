package kg.equeue.backend.ticketevents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.tickets.TicketStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ticket_events")
public class TicketEventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    private TicketStatus toStatus;

    @Column(name = "old_status")
    private String oldStatus;

    @Column(name = "new_status")
    private String newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    private TicketActorType actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "window_id")
    private UUID windowId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public TicketStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(TicketStatus fromStatus) {
        this.fromStatus = fromStatus;
        this.oldStatus = fromStatus == null ? null : fromStatus.name();
    }

    public TicketStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(TicketStatus toStatus) {
        this.toStatus = toStatus;
        this.newStatus = toStatus == null ? null : toStatus.name();
    }

    public TicketActorType getActorType() {
        return actorType;
    }

    public void setActorType(TicketActorType actorType) {
        this.actorType = actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getWindowId() {
        return windowId;
    }

    public void setWindowId(UUID windowId) {
        this.windowId = windowId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

