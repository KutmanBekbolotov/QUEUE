package kg.equeue.backend.integrationclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kg.equeue.backend.common.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class IntegrationIdempotencyTest {

    private final IntegrationRequestRepository repository = org.mockito.Mockito.mock(IntegrationRequestRepository.class);
    private final IntegrationIdempotencyService service = new IntegrationIdempotencyService(repository, new ObjectMapper());

    @Test
    void duplicateTundukExternalIdReturnsSameBookingResponse() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("externalId", "tunduk-booking-1");
        when(repository.findExistingForUpdate("TUNDUK", "idem-1", "tunduk-booking-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IntegrationRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IntegrationIdempotencyService.BeginResult first = service.begin("TUNDUK", "idem-1", "tunduk-booking-1", request);
        service.complete(first.entity(), Map.of("bookingId", "booking-1"), 200);

        reset(repository);
        when(repository.findExistingForUpdate("TUNDUK", "idem-1", "tunduk-booking-1")).thenReturn(Optional.of(first.entity()));

        IntegrationIdempotencyService.BeginResult replay = service.begin("TUNDUK", "idem-1", "tunduk-booking-1", request);

        assertThat(replay.replay()).isTrue();
        assertThat(replay.responseBody()).contains("booking-1");
    }

    @Test
    void sameIdempotencyKeyWithDifferentBodyReturnsConflict() {
        IntegrationRequestEntity existing = new IntegrationRequestEntity();
        existing.setClientCode("TUNDUK");
        existing.setIdempotencyKey("idem-1");
        existing.setRequestHash("different-request-hash");
        existing.setStatus(IntegrationRequestStatus.SUCCEEDED);
        when(repository.findExistingForUpdate("TUNDUK", "idem-1", null)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.begin("TUNDUK", "idem-1", null, Map.of("externalId", "changed")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void createsProcessingRequestWithHashedBody() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("externalId", "zenoss-1");
        request.put("departmentId", "department-1");
        when(repository.findExistingForUpdate("ZENOSS", "idem-2", "zenoss-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IntegrationRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IntegrationIdempotencyService.BeginResult result = service.begin("ZENOSS", "idem-2", "zenoss-1", request);

        ArgumentCaptor<IntegrationRequestEntity> captor = ArgumentCaptor.forClass(IntegrationRequestEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(result.replay()).isFalse();
        assertThat(captor.getValue().getClientCode()).isEqualTo("ZENOSS");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-2");
        assertThat(captor.getValue().getExternalRequestId()).isEqualTo("zenoss-1");
        assertThat(captor.getValue().getRequestHash()).isNotBlank();
        assertThat(captor.getValue().getRequestHash()).doesNotContain("zenoss-1", "department-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(IntegrationRequestStatus.PROCESSING);
    }

    @Test
    void requiresIdempotencyKeyOrExternalRequestId() {
        assertThatThrownBy(() -> service.begin("CABINET", null, null, Map.of()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
                });
        verify(repository, never()).findExistingForUpdate(any(), any(), any());
    }

    @Test
    void explicitIdempotencyKeyPreventsBusinessExternalIdFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(service.externalRequestId(request, "tunduk-booking-1", "cancel-1")).isNull();
    }

    @Test
    void externalRequestHeaderTakesPrecedenceOverFallbackRules() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-External-Request-Id", "request-1");

        assertThat(service.externalRequestId(request, "tunduk-booking-1", "cancel-1")).isEqualTo("request-1");
    }
}
