package kg.equeue.backend.common;

import org.slf4j.MDC;

public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private RequestContext() {
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }

    public static String correlationId() {
        return MDC.get(CORRELATION_ID_MDC_KEY);
    }
}

