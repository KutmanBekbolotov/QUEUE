package kg.equeue.backend.employeewindowassignments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_window_assignments")
public class EmployeeWindowAssignmentEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "service_window_id", nullable = false)
    private UUID serviceWindowId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getServiceWindowId() {
        return serviceWindowId;
    }

    public void setServiceWindowId(UUID serviceWindowId) {
        this.serviceWindowId = serviceWindowId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}

