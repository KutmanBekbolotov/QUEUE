package kg.equeue.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import kg.equeue.backend.config.SecurityProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IntegrationAuthenticationFilter extends OncePerRequestFilter {

    public static final String INTEGRATION_CLIENT_HEADER = "X-Integration-Client";
    public static final String BACKEND_KEY_HEADER = "X-Backend-Integration-Key";

    private final SecurityProperties securityProperties;

    public IntegrationAuthenticationFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientCode = request.getHeader(INTEGRATION_CLIENT_HEADER);
        String backendKey = request.getHeader(BACKEND_KEY_HEADER);
        if (clientCode != null && !clientCode.isBlank()
                && backendKey != null
                && backendKey.equals(securityProperties.getIntegration().getBackendKey())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_INTEGRATION_SERVICE"),
                    new SimpleGrantedAuthority("REGION_READ"),
                    new SimpleGrantedAuthority("DEPARTMENT_READ"),
                    new SimpleGrantedAuthority("SERVICE_READ"),
                    new SimpleGrantedAuthority("BOOKING_READ"),
                    new SimpleGrantedAuthority("BOOKING_CREATE"),
                    new SimpleGrantedAuthority("BOOKING_CANCEL"),
                    new SimpleGrantedAuthority("BOOKING_CHECK_IN"),
                    new SimpleGrantedAuthority("BOOKING_SLOT_READ"),
                    new SimpleGrantedAuthority("TICKET_CREATE"),
                    new SimpleGrantedAuthority("TICKET_READ")
            );
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken("integration:" + clientCode, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
