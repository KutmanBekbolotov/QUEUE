package kg.equeue.backend.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import kg.equeue.backend.auth.dto.MeResponse;
import kg.equeue.backend.users.dto.CreateUserRequest;
import kg.equeue.backend.users.dto.UpdateUserRequest;
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
}
