package kg.equeue.backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = headerOrNew(request, RequestContext.REQUEST_ID_HEADER);
        String correlationId = request.getHeader(RequestContext.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = requestId;
        }

        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, requestId);
        MDC.put(RequestContext.CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        response.setHeader(RequestContext.CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
            MDC.remove(RequestContext.CORRELATION_ID_MDC_KEY);
        }
    }

    private String headerOrNew(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }
}

