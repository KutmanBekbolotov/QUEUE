package kg.equeue.backend.bookingslots;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "booking_slots")
public class BookingSlotEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_start", nullable = false)
    private LocalTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalTime slotEnd;

    @Column(nullable = false)
    private int capacity = 1;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingSlotStatus status = BookingSlotStatus.ACTIVE;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "reserved_count", nullable = false)
    private int reservedCount;

    @Column(name = "starts_at", nullable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalTime endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        syncLegacyColumns();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        syncLegacyColumns();
    }

    public boolean hasCapacity() {
        return status == BookingSlotStatus.ACTIVE && bookedCount < capacity;
    }

    public void incrementBookedCount() {
        if (!hasCapacity()) {
            throw new IllegalStateException("Booking slot has no remaining capacity");
        }
        bookedCount++;
        reservedCount = bookedCount;
        if (bookedCount >= capacity) {
            status = BookingSlotStatus.FULL;
            active = true;
        }
    }

    public void decrementBookedCount() {
        if (bookedCount > 0) {
            bookedCount--;
        }
        reservedCount = bookedCount;
        if (status == BookingSlotStatus.FULL && bookedCount < capacity) {
            status = BookingSlotStatus.ACTIVE;
        }
    }

    private void syncLegacyColumns() {
        startsAt = slotStart;
        endsAt = slotEnd;
        reservedCount = bookedCount;
        active = status == BookingSlotStatus.ACTIVE || status == BookingSlotStatus.FULL;
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

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalTime getSlotStart() {
        return slotStart;
    }

    public void setSlotStart(LocalTime slotStart) {
        this.slotStart = slotStart;
    }

    public LocalTime getSlotEnd() {
        return slotEnd;
    }

    public void setSlotEnd(LocalTime slotEnd) {
        this.slotEnd = slotEnd;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(int bookedCount) {
        this.bookedCount = bookedCount;
    }

    public BookingSlotStatus getStatus() {
        return status;
    }

    public void setStatus(BookingSlotStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
