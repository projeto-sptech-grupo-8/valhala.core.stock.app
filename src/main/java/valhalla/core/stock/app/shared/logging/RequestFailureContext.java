package valhalla.core.stock.app.shared.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Carries a safe, user-facing failure reason to the HTTP request summary log. */
public final class RequestFailureContext {

    private static final String ATTRIBUTE = RequestFailureContext.class.getName();
    private static final int MAX_MESSAGE_LENGTH = 300;

    private RequestFailureContext() {
    }

    public static void markFailure(HttpServletRequest request, String errorType, String message) {
        request.setAttribute(ATTRIBUTE, new FailureDetails(errorType, sanitize(message)));
    }

    public static void markFailure(String errorType, String message) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            markFailure(attributes.getRequest(), errorType, message);
        }
    }

    public static FailureDetails getFailure(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof FailureDetails details ? details : null;
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Erro não especificado";
        }
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= MAX_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }

    public record FailureDetails(String errorType, String message) {
    }
}
