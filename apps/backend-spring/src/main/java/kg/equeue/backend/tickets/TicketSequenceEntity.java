package kg.equeue.backend.tickets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ticket_sequences")
public class TicketSequenceEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "service_category_id", nullable = false)
    private UUID serviceCategoryId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "current_value", nullable = false)
    private int currentValue;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

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

    public UUID getServiceCategoryId() {
        return serviceCategoryId;
    }

    public void setServiceCategoryId(UUID serviceCategoryId) {
        this.serviceCategoryId = serviceCategoryId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public int getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(int currentValue) {
        this.currentValue = currentValue;
        this.lastNumber = currentValue;
    }
}

