package kg.equeue.backend.integrationclients;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import kg.equeue.backend.auth.IntegrationAuthenticationFilter;
import kg.equeue.backend.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationIdempotencyService {

    private final IntegrationRequestRepository integrationRequestRepository;
    private final ObjectMapper objectMapper;

    public IntegrationIdempotencyService(IntegrationRequestRepository integrationRequestRepository, ObjectMapper objectMapper) {
        this.integrationRequestRepository = integrationRequestRepository;
        this.objectMapper = objectMapper;
    }

    public String clientCode(HttpServletRequest request, String fallback) {
        String header = request.getHeader(IntegrationAuthenticationFilter.INTEGRATION_CLIENT_HEADER);
        return header == null || header.isBlank() ? fallback : header;
    }

    public String idempotencyKey(HttpServletRequest request, String bodyValue) {
        String header = request.getHeader("Idempotency-Key");
        return header == null || header.isBlank() ? bodyValue : header;
    }

    public String externalRequestId(HttpServletRequest request, String bodyValue) {
        return externalRequestId(request, bodyValue, null);
    }

    public String externalRequestId(HttpServletRequest request, String bodyValue, String idempotencyKey) {
        String header = request.getHeader("X-External-Request-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return idempotencyKey == null || idempotencyKey.isBlank() ? bodyValue : null;
    }

    @Transactional
    public BeginResult begin(String clientCode, String idempotencyKey, String externalRequestId, Object requestBody) {
        if ((idempotencyKey == null || idempotencyKey.isBlank()) && (externalRequestId == null || externalRequestId.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key or externalId is required");
        }
        String requestHash = hash(normalized(requestBody));
        return integrationRequestRepository.findExistingForUpdate(clientCode, blankToNull(idempotencyKey), blankToNull(externalRequestId))
                .map(existing -> evaluateExisting(existing, requestHash))
                .orElseGet(() -> create(clientCode, idempotencyKey, externalRequestId, requestHash));
    }

    @Transactional
    public void complete(IntegrationRequestEntity entity, Object responseBody, int responseStatus) {
        entity.setStatus(IntegrationRequestStatus.SUCCEEDED);
        entity.setResponseBody(normalized(responseBody));
        entity.setResponseStatus(responseStatus);
        integrationRequestRepository.save(entity);
    }

    @Transactional
    public void fail(IntegrationRequestEntity entity, String errorCode, int responseStatus) {
        entity.setStatus(IntegrationRequestStatus.FAILED);
        entity.setErrorCode(errorCode);
        entity.setResponseStatus(responseStatus);
        integrationRequestRepository.save(entity);
    }

    private BeginResult evaluateExisting(IntegrationRequestEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "Idempotency key was already used with a different request");
        }
        if (existing.getStatus() == IntegrationRequestStatus.SUCCEEDED && existing.getResponseBody() != null) {
            return BeginResult.replay(existing.getResponseBody());
        }
        if (existing.getStatus() == IntegrationRequestStatus.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_PROCESSING", "Request is already processing");
        }
        existing.setStatus(IntegrationRequestStatus.PROCESSING);
        existing.setErrorCode(null);
        existing.setResponseStatus(null);
        integrationRequestRepository.save(existing);
        return BeginResult.process(existing);
    }

    private BeginResult create(String clientCode, String idempotencyKey, String externalRequestId, String requestHash) {
        IntegrationRequestEntity entity = new IntegrationRequestEntity();
        entity.setClientCode(clientCode);
        entity.setIdempotencyKey(blankToNull(idempotencyKey));
        entity.setExternalRequestId(blankToNull(externalRequestId));
        entity.setRequestHash(requestHash);
        try {
            return BeginResult.process(integrationRequestRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            return integrationRequestRepository.findExistingForUpdate(clientCode, blankToNull(idempotencyKey), blankToNull(externalRequestId))
                    .map(existing -> evaluateExisting(existing, requestHash))
                    .orElseThrow(() -> ex);
        }
    }

    private String normalized(Object requestBody) {
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (Exception ex) {
            return String.valueOf(requestBody);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash idempotency request", ex);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record BeginResult(boolean replay, String responseBody, IntegrationRequestEntity entity) {
        static BeginResult replay(String responseBody) {
            return new BeginResult(true, responseBody, null);
        }

        static BeginResult process(IntegrationRequestEntity entity) {
            return new BeginResult(false, null, entity);
        }
    }
}
