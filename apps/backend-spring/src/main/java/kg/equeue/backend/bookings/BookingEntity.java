package kg.equeue.backend.bookings;

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
@Table(name = "bookings")
public class BookingEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_number", nullable = false)
    private String bookingNumber;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "external_id")
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_source")
    private BookingSource externalSource;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "booking_slot_id")
    private UUID bookingSlotId;

    @Column(name = "citizen_full_name")
    private String citizenFullName;

    @Column(name = "citizen_pin")
    private String citizenPin;

    @Column(name = "citizen_phone")
    private String citizenPhone;

    @Column(name = "vehicle_number")
    private String vehicleNumber;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "booking_start", nullable = false)
    private LocalTime bookingStart;

    @Column(name = "booking_end", nullable = false)
    private LocalTime bookingEnd;

    @Column(name = "booked_date", nullable = false)
    private LocalDate bookedDate;

    @Column(name = "starts_at", nullable = false)
    private LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_client_code")
    private String externalClientCode;

    @Column(name = "external_booking_id")
    private String externalBookingId;

    @Column(name = "qr_token", nullable = false)
    private String qrToken;

    @Column(name = "cancellation_reason_id")
    private UUID cancellationReasonId;

    @Column(name = "cancel_comment")
    private String cancelComment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

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

    private void syncLegacyColumns() {
        bookingSlotId = slotId;
        bookedDate = bookingDate;
        startsAt = bookingStart;
        endsAt = bookingEnd;
        source = externalSource == null ? BookingSource.ADMIN_CREATED.name() : externalSource.name();
        externalClientCode = externalSource == null ? null : externalSource.name();
        externalBookingId = externalId;
    }

    public UUID getId() {
        return id;
    }

    public String getBookingNumber() {
        return bookingNumber;
    }

    public void setBookingNumber(String bookingNumber) {
        this.bookingNumber = bookingNumber;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public BookingSource getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(BookingSource externalSource) {
        this.externalSource = externalSource;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public UUID getSlotId() {
        return slotId;
    }

    public void setSlotId(UUID slotId) {
        this.slotId = slotId;
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

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getBookingStart() {
        return bookingStart;
    }

    public void setBookingStart(LocalTime bookingStart) {
        this.bookingStart = bookingStart;
    }

    public LocalTime getBookingEnd() {
        return bookingEnd;
    }

    public void setBookingEnd(LocalTime bookingEnd) {
        this.bookingEnd = bookingEnd;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public UUID getCancellationReasonId() {
        return cancellationReasonId;
    }

    public void setCancellationReasonId(UUID cancellationReasonId) {
        this.cancellationReasonId = cancellationReasonId;
    }

    public String getCancelComment() {
        return cancelComment;
    }

    public void setCancelComment(String cancelComment) {
        this.cancelComment = cancelComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(Instant checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(Instant expiredAt) {
        this.expiredAt = expiredAt;
    }
}

