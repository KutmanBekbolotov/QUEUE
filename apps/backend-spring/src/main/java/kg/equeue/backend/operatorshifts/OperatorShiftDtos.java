package kg.equeue.backend.operatorshifts;

import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.directories.DirectoryDtos.WindowResponse;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;

public final class OperatorShiftDtos {

    private OperatorShiftDtos() {
    }

    public record OpenShiftRequest(UUID departmentId, UUID windowId) {
    }

    public record ShiftResponse(
            UUID id,
            UUID operatorId,
            UUID departmentId,
            UUID windowId,
            OperatorShiftStatus status,
            Instant openedAt,
            Instant closedAt
    ) {
    }

    public record OperatorDashboardResponse(
            UUID operatorId,
            ShiftResponse shift,
            WindowResponse window,
            TicketResponse activeTicket,
            Instant generatedAt
    ) {
    }
}
