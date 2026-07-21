package kg.equeue.backend.tickets;

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
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class TicketEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "ticket_number", nullable = false)
    private String ticketNumber;

    @Column(name = "ticket_prefix")
    private String ticketPrefix;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "office_room_id")
    private UUID officeRoomId;

    @Column(name = "hall_id")
    private UUID hallId;

    @Column(name = "current_window_id")
    private UUID windowId;

    @Column(name = "service_category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "citizen_full_name")
    private String citizenFullName;

    @Column(name = "citizen_pin")
    private String citizenPin;

    @Column(name = "citizen_phone")
    private String citizenPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.WAITING;

    @Column(nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "booking_time")
    private Instant bookingTime;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "recalled_at")
    private Instant recalledAt;

    @Column(name = "recall_count", nullable = false)
    private int recallCount;

    @Column(name = "service_started_at")
    private Instant serviceStartedAt;

    @Column(name = "service_paused_at")
    private Instant servicePausedAt;

    @Column(name = "service_completed_at")
    private Instant serviceCompletedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason_id")
    private UUID cancellationReasonId;

    @Column(name = "pause_reason_id")
    private UUID pauseReasonId;

    @Column(name = "qr_token")
    private String qrToken;

    @Column(name = "current_operator_id")
    private UUID servedByUserId;

    private String comment;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (workDate == null) {
            workDate = LocalDate.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public String getTicketPrefix() {
        return ticketPrefix;
    }

    public void setTicketPrefix(String ticketPrefix) {
        this.ticketPrefix = ticketPrefix;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public UUID getRegionId() {
        return regionId;
    }

    public void setRegionId(UUID regionId) {
        this.regionId = regionId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getOfficeRoomId() {
        return officeRoomId;
    }

    public void setOfficeRoomId(UUID officeRoomId) {
        this.officeRoomId = officeRoomId;
    }

    public UUID getHallId() {
        return hallId;
    }

    public void setHallId(UUID hallId) {
        this.hallId = hallId;
    }

    public UUID getWindowId() {
        return windowId;
    }

    public void setWindowId(UUID windowId) {
        this.windowId = windowId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getCitizenFullName() {
        return citizenFullName;
    }

    public void setCitizenFullName(String citizenFullName) {
        this.citizenFullName = citizenFullName;
    }

    public String getCitizenPin() {
        return citizenPin;
    }

    public void setCitizenPin(String citizenPin) {
        this.citizenPin = citizenPin;
    }

    public String getCitizenPhone() {
        return citizenPhone;
    }

    public void setCitizenPhone(String citizenPhone) {
        this.citizenPhone = citizenPhone;
    }

    public TicketSource getSource() {
        return source;
    }

    public void setSource(TicketSource source) {
        this.source = source;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getBookingTime() {
        return bookingTime;
    }

    public void setBookingTime(Instant bookingTime) {
        this.bookingTime = bookingTime;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(Instant calledAt) {
        this.calledAt = calledAt;
    }

    public Instant getRecalledAt() {
        return recalledAt;
    }

    public void setRecalledAt(Instant recalledAt) {
        this.recalledAt = recalledAt;
    }

    public int getRecallCount() {
        return recallCount;
    }

    public void setRecallCount(int recallCount) {
        this.recallCount = recallCount;
    }

    public Instant getServiceStartedAt() {
        return serviceStartedAt;
    }

    public void setServiceStartedAt(Instant serviceStartedAt) {
        this.serviceStartedAt = serviceStartedAt;
    }

    public Instant getServicePausedAt() {
        return servicePausedAt;
    }

    public void setServicePausedAt(Instant servicePausedAt) {
        this.servicePausedAt = servicePausedAt;
    }

    public Instant getServiceCompletedAt() {
        return serviceCompletedAt;
    }

    public void setServiceCompletedAt(Instant serviceCompletedAt) {
        this.serviceCompletedAt = serviceCompletedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public UUID getCancellationReasonId() {
        return cancellationReasonId;
    }

    public void setCancellationReasonId(UUID cancellationReasonId) {
        this.cancellationReasonId = cancellationReasonId;
    }

    public UUID getPauseReasonId() {
        return pauseReasonId;
    }

    public void setPauseReasonId(UUID pauseReasonId) {
        this.pauseReasonId = pauseReasonId;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public UUID getServedByUserId() {
        return servedByUserId;
    }

    public void setServedByUserId(UUID servedByUserId) {
        this.servedByUserId = servedByUserId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public long getVersion() {
        return version;
    }
}
