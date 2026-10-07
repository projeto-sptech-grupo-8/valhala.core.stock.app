package valhalla.core.stock.app.modules.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class CsrfTokenValidationFilter extends OncePerRequestFilter {

    public static final String CABECALHO_CSRF = "X-XSRF-TOKEN";
    private static final Set<String> METODOS_PROTEGIDOS = Set.of(
            HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name()
    );

    private final CsrfTokenStore csrfTokenStore;
    private final JwtDecoder refreshTokenDecoder;
    private final ObjectMapper objectMapper;

    public CsrfTokenValidationFilter(
            CsrfTokenStore csrfTokenStore,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
            ObjectMapper objectMapper
    ) {
        this.csrfTokenStore = csrfTokenStore;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS_PROTEGIDOS.contains(request.getMethod()) || "/auth/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        UUID idSessao = obterIdSessaoAutenticada(request);
        // Sem uma sessão válida não há token CSRF a validar. A cadeia de segurança/autenticação
        // preserva a resposta 401 apropriada para token ausente, revogado ou malformado.
        if (idSessao == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // Sessões removidas no logout/refresh não possuem mais um token CSRF. Nesse caso,
        // a autenticação ou o endpoint deve responder 401 em vez de mascarar a sessão inválida como 403.
        if (csrfTokenStore.obter(idSessao) == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (csrfTokenStore.ehValido(idSessao, request.getHeader(CABECALHO_CSRF))) {
            filterChain.doFilter(request, response);
            return;
        }

        escreverErroCsrf(response);
    }

    private UUID obterIdSessaoAutenticada(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return converterUuid(token.getToken().getClaimAsString("sessionJti"));
        }

        String refreshToken = obterCookie(request, "refreshToken");
        if (refreshToken == null) {
            return null;
        }
        try {
            Jwt jwt = refreshTokenDecoder.decode(refreshToken);
            return converterUuid(jwt.getId());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private UUID converterUuid(String valor) {
        try {
            return valor == null ? null : UUID.fromString(valor);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String obterCookie(HttpServletRequest request, String nome) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (nome.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void escreverErroCsrf(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Token CSRF ausente ou inválido",
                Map.of()
        ));
    }
}
