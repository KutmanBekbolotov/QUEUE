package kg.equeue.backend.qr;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class QrDtos {

    private QrDtos() {
    }

    public record QrConfigResponse(
            UUID departmentId,
            String departmentCode,
            String departmentName,
            List<QrConfigServiceResponse> services,
            List<QrConfigCategoryResponse> categories
    ) {
    }

    public record LocalizedName(String ru, String ky) {
    }

    public record QrConfigServiceResponse(
            UUID id,
            String code,
            LocalizedName name,
            UUID categoryId,
            String categoryCode
    ) {
    }

    public record QrConfigCategoryResponse(
            UUID id,
            String code,
            LocalizedName name
    ) {
    }

    public record QrCreateTicketRequest(
            @NotNull UUID departmentId,
            @NotNull UUID serviceId,
            String citizenFullName,
            String citizenPin,
            String citizenPhone,
            String comment
    ) {
    }
}
