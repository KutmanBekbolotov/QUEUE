package kg.equeue.backend.directories;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import kg.equeue.backend.servicewindows.WindowStatus;

public final class DirectoryDtos {

    private DirectoryDtos() {
    }

    public record ActiveStatusRequest(@NotNull Boolean active) {
    }

    public record DepartmentStatusRequest(@NotNull Boolean active, Boolean closed) {
    }

    public record RegionRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 255) String name) {
    }

    public record RegionResponse(UUID id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record DepartmentRequest(
            @NotNull UUID regionId,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String address,
            @Size(max = 80) String timezone
    ) {
    }

    public record DepartmentResponse(
            UUID id,
            UUID regionId,
            String code,
            String name,
            String address,
            String timezone,
            boolean active,
            boolean closed,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record OfficeRoomRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 255) String name, @Size(max = 80) String floor) {
    }

    public record OfficeRoomResponse(UUID id, UUID departmentId, String code, String name, String floor, boolean active) {
    }

    public record HallRequest(UUID officeRoomId, @NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 255) String name) {
    }

    public record HallResponse(UUID id, UUID departmentId, UUID officeRoomId, String code, String name, boolean active) {
    }

    public record WindowRequest(UUID hallId, @NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 255) String displayName) {
    }

    public record WindowStatusRequest(@NotNull WindowStatus status) {
    }

    public record AssignEmployeeToWindowRequest(@NotNull UUID employeeId) {
    }

    public record WindowResponse(
            UUID id,
            UUID departmentId,
            UUID hallId,
            UUID employeeId,
            String code,
            String displayName,
            boolean active,
            boolean open,
            WindowStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ServiceCategoryRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 20) String ticketPrefix
    ) {
    }

    public record ServiceCategoryResponse(
            UUID id,
            String code,
            String name,
            String ticketPrefix,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ServiceRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 255) String name,
            String description,
            @Min(1) Integer defaultDurationMinutes,
            @Min(1) Integer dailyLimit
    ) {
    }

    public record ServiceResponse(
            UUID id,
            UUID categoryId,
            String code,
            String name,
            String description,
            int defaultDurationMinutes,
            Integer dailyLimit,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DepartmentServiceRequest(
            Boolean onlineBookingEnabled,
            Boolean terminalEnabled,
            Boolean qrEnabled,
            @Min(1) Integer dailyLimit
    ) {
    }

    public record DepartmentServiceResponse(
            UUID id,
            UUID departmentId,
            UUID serviceId,
            boolean active,
            boolean onlineBookingEnabled,
            boolean terminalEnabled,
            boolean qrEnabled,
            Integer dailyLimit
    ) {
    }

    public record AssignEmployeeServiceRequest(@NotNull UUID departmentId) {
    }

    public record EmployeeServiceAssignmentResponse(
            UUID id,
            UUID userId,
            UUID departmentId,
            UUID serviceId,
            boolean active
    ) {
    }
}
