package kg.equeue.backend.bookings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.ticketevents.TicketActorType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "booking_events")
public class BookingEventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")
    private BookingStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    private TicketActorType actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "service_id")
    private UUID serviceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setFromStatus(BookingStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public void setToStatus(BookingStatus toStatus) {
        this.toStatus = toStatus;
    }

    public void setActorType(TicketActorType actorType) {
        this.actorType = actorType;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}

