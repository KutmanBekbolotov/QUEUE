package kg.equeue.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(error(ex.getCode(), ex.getMessage(), ex.getDetails(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed", details, request));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorResponse> handleBindValidation(BindException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed", details, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableRequestBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormat) {
            String field = jsonPath(invalidFormat);
            String expectedType = invalidFormat.getTargetType() == null ? "expected type" : invalidFormat.getTargetType().getSimpleName();
            details.put(field.isBlank() ? "body" : field, "Invalid value for " + expectedType);
        } else {
            details.put("body", "Malformed JSON request");
        }
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed", details, request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> requiredType = ex.getRequiredType();
        String expectedType = requiredType == null ? "expected type" : requiredType.getSimpleName();
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed",
                        Map.of(ex.getName(), "Invalid value for " + expectedType), request));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("UNAUTHENTICATED", "Authentication is required", Map.of(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("FORBIDDEN", "Access denied", Map.of(), request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(error("METHOD_NOT_ALLOWED", "Request method is not supported",
                        Map.of("method", String.valueOf(ex.getMethod())), request));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    ResponseEntity<Void> handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> handleUnexpected(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        if (response.isCommitted() || MediaType.TEXT_EVENT_STREAM_VALUE.equals(response.getContentType())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "Unexpected server error", Map.of(), request));
    }

    private ErrorResponse error(String code, String message, Map<String, Object> details, HttpServletRequest request) {
        String requestId = RequestContext.requestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(RequestContext.REQUEST_ID_HEADER);
        }
        return new ErrorResponse(Instant.now(), requestId, code, message, details == null ? Map.of() : details);
    }

    private String jsonPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(reference -> reference.getFieldName() == null ? "[" + reference.getIndex() + "]" : reference.getFieldName())
                .collect(Collectors.joining("."));
    }
}
