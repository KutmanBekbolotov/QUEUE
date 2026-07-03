package kg.equeue.backend.terminals;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class TerminalDtos {

    private TerminalDtos() {
    }

    public record TerminalConfigResponse(
            UUID terminalId,
            UUID departmentId,
            String code,
            String name,
            List<UUID> serviceIds
    ) {
    }

    public record TerminalCreateTicketRequest(
            @NotNull UUID departmentId,
            @NotNull UUID serviceId,
            String citizenFullName,
            String citizenPin,
            String citizenPhone,
            String comment
    ) {
    }
}
