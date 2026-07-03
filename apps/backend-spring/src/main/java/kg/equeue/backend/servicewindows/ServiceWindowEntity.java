package kg.equeue.backend.servicewindows;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_windows")
public class ServiceWindowEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "hall_id")
    private UUID hallId;

    @Column(nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean open;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WindowStatus status = WindowStatus.CLOSED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getHallId() {
        return hallId;
    }

    public void setHallId(UUID hallId) {
        this.hallId = hallId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            this.open = false;
            this.status = WindowStatus.INACTIVE;
        } else if (this.status == WindowStatus.INACTIVE) {
            this.status = WindowStatus.CLOSED;
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        this.status = open ? WindowStatus.OPEN : WindowStatus.CLOSED;
    }

    public WindowStatus getStatus() {
        return status;
    }

    public void setStatus(WindowStatus status) {
        this.status = status;
        this.active = status != WindowStatus.INACTIVE;
        this.open = status == WindowStatus.OPEN;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

