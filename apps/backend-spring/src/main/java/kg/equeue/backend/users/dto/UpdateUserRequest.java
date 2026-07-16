package kg.equeue.backend.users.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UpdateUserRequest(
        @Size(max = 120) String username,
        @Size(min = 8, max = 120) String password,
        @Size(max = 255) String fullName,
        @Size(max = 255) String email,
        @Size(max = 64) String phone,
        UUID departmentId,
        Set<String> roleCodes,
        @JsonAlias({"assignedWindow", "assignedWindowId"})
        String windowId,
        @JsonAlias({"services", "serviceCodes", "employeeService", "employeeServices", "employeeServiceIds", "employeeServiceCodes"})
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        @JsonDeserialize(contentUsing = AssignmentIdentifierDeserializer.class)
        Set<String> serviceIds
) {
}
