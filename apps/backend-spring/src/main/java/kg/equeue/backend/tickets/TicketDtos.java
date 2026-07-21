package kg.equeue.backend.tickets;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record CreateTicketRequest(
            @NotNull UUID departmentId,
            @NotNull UUID serviceId,
            UUID bookingId,
            String citizenFullName,
            String citizenPin,
            String citizenPhone,
            @NotNull TicketSource source,
            String comment,
            String externalId,
            String idempotencyKey
    ) {
    }

    public record CallTicketRequest(@NotNull UUID windowId) {
    }

    public record CallNextTicketRequest(
            @NotNull UUID departmentId,
            @NotNull UUID windowId,
            @NotEmpty List<UUID> serviceIds
    ) {
    }

    public record PauseTicketRequest(UUID pauseReasonId, String comment) {
    }

    public record CancelTicketRequest(UUID cancellationReasonId, String comment) {
    }

    public record TransferTicketRequest(
            @NotNull UUID targetDepartmentId,
            @NotNull UUID targetServiceId,
            UUID targetWindowId,
            String comment
    ) {
    }

    public record LocalizedName(String ru, String ky) {
    }

    public record TicketResponse(
            UUID id,
            String ticketNumber,
            String ticketPrefix,
            Integer sequenceNumber,
            LocalDate workDate,
            UUID regionId,
            UUID departmentId,
            UUID officeRoomId,
            UUID hallId,
            UUID windowId,
            UUID categoryId,
            UUID serviceId,
            String citizenFullName,
            String citizenPin,
            String citizenPhone,
            TicketSource source,
            TicketStatus status,
            Instant createdAt,
            Instant calledAt,
            Instant recalledAt,
            int recallCount,
            Instant serviceStartedAt,
            Instant servicePausedAt,
            Instant serviceCompletedAt,
            Instant cancelledAt,
            UUID cancellationReasonId,
            UUID pauseReasonId,
            UUID servedByUserId,
            String comment,
            long version,
            UUID operatorId,
            UUID serviceWindowId,
            String windowNumber,
            LocalizedName serviceName
    ) {
    }

    public record TvSnapshotResponse(UUID departmentId, List<TicketResponse> tickets, Instant generatedAt) {
    }
}
