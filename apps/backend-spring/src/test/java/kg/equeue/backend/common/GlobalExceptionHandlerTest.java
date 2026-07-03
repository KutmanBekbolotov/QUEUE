package kg.equeue.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void methodNotSupportedReturnsMethodNotAllowedError() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/user-id/status");

        var response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST", List.of("PATCH")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().details()).containsEntry("method", "POST");
    }

    @Test
    void asyncRequestTimeoutReturnsNoContentForStreams() {
        var response = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unexpectedExceptionDoesNotWriteJsonIntoEventStreamResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tv/department-id/stream");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setContentType("text/event-stream");

        var response = handler.handleUnexpected(new RuntimeException("client disconnected"), request, servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}
