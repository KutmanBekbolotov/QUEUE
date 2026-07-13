package kg.equeue.backend.devices;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record CreateTerminalRequest(
            @NotNull UUID departmentId,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 255) String name
    ) {
    }

    public record UpdateTerminalRequest(
            UUID departmentId,
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 80) String code,
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String name,
            Boolean active
    ) {
    }

    public record CreateTvDisplayRequest(
            @NotNull UUID departmentId,
            UUID hallId,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 255) String name
    ) {
    }

    public record UpdateTvDisplayRequest(
            UUID departmentId,
            UUID hallId,
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 80) String code,
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String name,
            Boolean active
    ) {
    }

    public record DeviceResponse(
            UUID id,
            String type,
            UUID departmentId,
            UUID hallId,
            String code,
            String name,
            boolean active,
            Instant lastSeenAt
    ) {
    }

    public record ProvisionedDeviceResponse(
            DeviceResponse device,
            String deviceToken
    ) {
    }
}
