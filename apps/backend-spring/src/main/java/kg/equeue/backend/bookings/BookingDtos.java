package kg.equeue.backend.bookings;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import kg.equeue.backend.bookingslots.BookingSlotStatus;
import kg.equeue.backend.tickets.TicketStatus;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record AvailableDatesResponse(UUID departmentId, UUID serviceId, List<LocalDate> dates) {
    }

    public record SlotResponse(
            UUID id,
            UUID departmentId,
            UUID serviceId,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            int capacity,
            int bookedCount,
            int remaining,
            BookingSlotStatus status
    ) {
    }

    public record CreateBookingRequest(
            @NotNull UUID departmentId,
            @NotNull UUID serviceId,
            @NotNull UUID slotId,
            String citizenFullName,
            String citizenPin,
            String citizenPhone,
            String vehicleNumber,
            @NotNull BookingSource source,
            String externalId,
            String idempotencyKey
    ) {
    }

    public record CancelBookingRequest(UUID cancellationReasonId, String comment, String externalId, String idempotencyKey) {
    }

    public record GenerateSlotsRequest(
            @NotNull UUID departmentId,
            @NotNull UUID serviceId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @Min(1) int intervalMinutes,
            @Min(1) int capacity,
            boolean overwrite
    ) {
    }

    public record GenerateSlotsResponse(int created, int skipped, int disabled) {
    }

    public record BookingResponse(
            UUID id,
            String bookingNumber,
            BookingStatus status,
            UUID departmentId,
            UUID serviceId,
            UUID slotId,
            LocalDate bookingDate,
            LocalTime bookingStart,
            LocalTime bookingEnd,
            BookingSource source,
            String externalId,
            String qrToken,
            UUID ticketId,
            String ticketNumber,
            TicketStatus ticketStatus,
            Instant createdAt,
            Instant updatedAt,
            Instant cancelledAt,
            Instant checkedInAt,
            Instant expiredAt
    ) {
    }
}

