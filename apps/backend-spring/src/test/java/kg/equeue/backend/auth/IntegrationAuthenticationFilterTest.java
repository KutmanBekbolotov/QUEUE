package kg.equeue.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import kg.equeue.backend.config.SecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class IntegrationAuthenticationFilterTest {

    private final SecurityProperties properties = new SecurityProperties();
    private final IntegrationAuthenticationFilter filter = new IntegrationAuthenticationFilter(properties);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBackendIntegrationKeyAuthenticatesIntegrationClient() throws ServletException, IOException {
        properties.getIntegration().setBackendKey("backend-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IntegrationAuthenticationFilter.INTEGRATION_CLIENT_HEADER, "TUNDUK");
        request.addHeader(IntegrationAuthenticationFilter.BACKEND_KEY_HEADER, "backend-secret");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("integration:TUNDUK");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_INTEGRATION_SERVICE", "BOOKING_CREATE", "TICKET_CREATE");
    }

    @Test
    void invalidBackendIntegrationKeyDoesNotAuthenticate() throws ServletException, IOException {
        properties.getIntegration().setBackendKey("backend-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IntegrationAuthenticationFilter.INTEGRATION_CLIENT_HEADER, "TUNDUK");
        request.addHeader(IntegrationAuthenticationFilter.BACKEND_KEY_HEADER, "wrong-secret");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
