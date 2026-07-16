package kg.equeue.backend.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import kg.equeue.backend.auth.dto.MeResponse;
import kg.equeue.backend.users.dto.CreateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserRequest;
import kg.equeue.backend.users.dto.UserResponse;
import org.junit.jupiter.api.Test;

class UserRequestJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createRequestAcceptsFrontendServicesAlias() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        String json = """
                {
                  "username": "operator1",
                  "password": "password123",
                  "departmentId": "%s",
                  "windowId": "%s",
                  "roleCodes": ["OPERATOR"],
                  "services": ["VS", "TS"]
                }
                """.formatted(departmentId, windowId);

        CreateUserRequest request = objectMapper.readValue(json, CreateUserRequest.class);

        assertThat(request.departmentId()).isEqualTo(departmentId);
        assertThat(request.windowId()).isEqualTo(windowId.toString());
        assertThat(request.serviceIds()).containsExactlyInAnyOrder("VS", "TS");
    }

    @Test
    void createRequestAcceptsAssignedWindowAndEmployeeServiceAliases() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        String serviceId = UUID.randomUUID().toString();
        String json = """
                {
                  "username": "operator1",
                  "password": "password123",
                  "departmentId": "%s",
                  "assignedWindowId": "%s",
                  "roleCodes": ["OPERATOR"],
                  "employeeService": "%s"
                }
                """.formatted(departmentId, windowId, serviceId);

        CreateUserRequest request = objectMapper.readValue(json, CreateUserRequest.class);

        assertThat(request.departmentId()).isEqualTo(departmentId);
        assertThat(request.windowId()).isEqualTo(windowId.toString());
        assertThat(request.serviceIds()).containsExactly(serviceId);
    }

    @Test
    void updateRequestKeepsExplicitEmptyAssignments() throws Exception {
        UpdateUserRequest request = objectMapper.readValue("""
                {
                  "windowId": "",
                  "serviceCodes": []
                }
                """, UpdateUserRequest.class);

        assertThat(request.windowId()).isEmpty();
        assertThat(request.serviceIds()).isEmpty();
    }

    @Test
    void updateRequestAcceptsEmployeeServiceIdsAlias() throws Exception {
        String serviceId = UUID.randomUUID().toString();

        UpdateUserRequest request = objectMapper.readValue("""
                {
                  "assignedWindow": "W-01",
                  "employeeServiceIds": ["%s"]
                }
                """.formatted(serviceId), UpdateUserRequest.class);

        assertThat(request.windowId()).isEqualTo("W-01");
        assertThat(request.serviceIds()).containsExactly(serviceId);
    }

    @Test
    void updateRequestAcceptsSelectOptionObjectsForEmployeeServices() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        UpdateUserRequest request = objectMapper.readValue("""
                {
                  "employeeServices": [
                    { "value": "%s:%s", "label": "Vehicle service" },
                    { "service": { "code": "TS" } }
                  ]
                }
                """.formatted(departmentId, serviceId), UpdateUserRequest.class);

        assertThat(request.serviceIds()).containsExactly(
                departmentId + ":" + serviceId,
                "TS"
        );
    }

    @Test
    void userAssignmentsAreAlsoSerializedWithFrontendServicesName() {
        MeResponse response = new MeResponse(
                UUID.randomUUID(),
                "operator1",
                "Operator",
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                java.util.List.of(UUID.randomUUID()),
                java.util.List.of("VS", "TS"),
                UserStatus.ACTIVE,
                java.util.List.of("OPERATOR"),
                java.util.List.of("TICKET_START")
        );

        var json = objectMapper.valueToTree(response);

        assertThat(json.get("serviceCodes").get(0).asText()).isEqualTo("VS");
        assertThat(json.get("services").get(0).asText()).isEqualTo("VS");
    }

    @Test
    void userResponseSerializesAssignmentAliasesForFrontendForms() {
        UUID windowId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UserResponse response = new UserResponse(
                UUID.randomUUID(),
                "operator1",
                "Operator",
                null,
                null,
                UUID.randomUUID(),
                windowId,
                java.util.List.of(serviceId),
                java.util.List.of("VS"),
                UserStatus.ACTIVE,
                1,
                java.util.List.of("OPERATOR"),
                null,
                null
        );

        var json = objectMapper.valueToTree(response);

        assertThat(json.get("windowId").asText()).isEqualTo(windowId.toString());
        assertThat(json.get("assignedWindow").asText()).isEqualTo(windowId.toString());
        assertThat(json.get("assignedWindowId").asText()).isEqualTo(windowId.toString());
        assertThat(json.get("serviceIds").get(0).asText()).isEqualTo(serviceId.toString());
        assertThat(json.get("employeeServiceIds").get(0).asText()).isEqualTo(serviceId.toString());
        assertThat(json.get("services").get(0).asText()).isEqualTo("VS");
        assertThat(json.get("employeeService").get(0).asText()).isEqualTo("VS");
        assertThat(json.get("employeeServices").get(0).asText()).isEqualTo("VS");
        assertThat(json.get("employeeServiceCodes").get(0).asText()).isEqualTo("VS");
    }
}
