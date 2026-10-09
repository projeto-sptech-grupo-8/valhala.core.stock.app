package valhalla.core.stock.app.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Adds a safe request correlation id and records one summary line per HTTP request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        long startedAt = System.nanoTime();
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (response.getStatus() >= HttpServletResponse.SC_BAD_REQUEST) {
                var event = LOGGER.atWarn()
                        .addKeyValue("event", "http.request.completed")
                        .addKeyValue("outcome", "FAILURE")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("path", request.getRequestURI())
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue("durationMs", durationMs);
                RequestFailureContext.FailureDetails failure = RequestFailureContext.getFailure(request);
                if (failure != null) {
                    event.addKeyValue("error.type", failure.errorType())
                            .addKeyValue("error.message", failure.message());
                } else {
                    event.addKeyValue("error.type", "HTTP_" + response.getStatus())
                            .addKeyValue("error.message", "Requisição rejeitada sem detalhe da aplicação");
                }
                event.log("HTTP request failed");
            } else {
                LOGGER.atInfo()
                        .addKeyValue("event", "http.request.completed")
                        .addKeyValue("outcome", "SUCCESS")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("path", request.getRequestURI())
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue("durationMs", durationMs)
                        .log("HTTP request completed successfully");
            }
            MDC.remove(REQUEST_ID);
        }
    }

    private String requestId(String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        try {
            return UUID.fromString(requestedId.trim()).toString();
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID().toString();
        }
    }
}
