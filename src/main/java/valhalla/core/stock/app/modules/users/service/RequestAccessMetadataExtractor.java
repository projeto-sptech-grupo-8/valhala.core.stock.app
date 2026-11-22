package valhalla.core.stock.app.modules.users.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Component
public class RequestAccessMetadataExtractor {

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_DEVICE_LENGTH = 120;
    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final String UNKNOWN = "Desconhecido";

    public AccessMetadata extractCurrent() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return new AccessMetadata(null, UNKNOWN, null);
        }
        return extractFrom(servletAttributes.getRequest());
    }

    public AccessMetadata extractFrom(HttpServletRequest request) {
        if (request == null) {
            return new AccessMetadata(null, UNKNOWN, null);
        }
        String sourceIp = extractClientIp(request);
        String userAgent = extractUserAgent(request);
        String device = summarizeDevice(userAgent);
        return new AccessMetadata(sourceIp, device, userAgent);
    }

    String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String candidate = null;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            candidate = forwardedFor.split(",")[0].trim();
        }
        if (candidate == null || candidate.isBlank()) {
            String remoteAddr = request.getRemoteAddr();
            candidate = remoteAddr == null ? null : remoteAddr.trim();
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if ("0:0:0:0:0:0:0:1".equals(candidate) || "::1".equals(candidate)) {
            return "127.0.0.1";
        }
        return truncate(candidate, MAX_IP_LENGTH);
    }

    String extractUserAgent(HttpServletRequest request) {
        String raw = request.getHeader("User-Agent");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return truncate(raw.trim(), MAX_USER_AGENT_LENGTH);
    }

    String summarizeDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String os = detectOs(userAgent);
        String browser = detectBrowser(userAgent);

        if (UNKNOWN.equals(os) && UNKNOWN.equals(browser)) {
            return UNKNOWN;
        }
        if (UNKNOWN.equals(browser)) {
            return truncate(os, MAX_DEVICE_LENGTH);
        }
        if (UNKNOWN.equals(os)) {
            return truncate(browser, MAX_DEVICE_LENGTH);
        }
        return truncate(os + " · " + browser, MAX_DEVICE_LENGTH);
    }

    private String detectOs(String userAgent) {
        if (userAgent.contains("iPhone")) {
            return "iPhone";
        }
        if (userAgent.contains("iPad")) {
            return "iPad";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            return "macOS";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        return UNKNOWN;
    }

    private String detectBrowser(String userAgent) {
        if (userAgent.contains("Edg/") || userAgent.contains("EdgA/")
                || userAgent.contains("EdgiOS/") || userAgent.contains("Edge/")) {
            return "Edge";
        }
        if (userAgent.contains("OPR/") || userAgent.contains("Opera")) {
            return "Opera";
        }
        if (userAgent.contains("Chrome/") || userAgent.contains("CriOS/")) {
            return "Chrome";
        }
        if (userAgent.contains("Firefox/") || userAgent.contains("FxiOS/")) {
            return "Firefox";
        }
        if (userAgent.contains("Safari/")) {
            return "Safari";
        }
        return UNKNOWN;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record AccessMetadata(String sourceIp, String device, String userAgent) {
    }
}
