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
            List<UUID> serviceIds,
            List<TerminalConfigServiceResponse> services,
            List<TerminalConfigCategoryResponse> categories
    ) {
    }

    public record LocalizedName(String ru, String ky) {
    }

    public record TerminalConfigServiceResponse(
            UUID id,
            String code,
            LocalizedName name,
            UUID categoryId,
            String type
    ) {
    }

    public record TerminalConfigCategoryResponse(
            UUID id,
            String type,
            LocalizedName name
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
