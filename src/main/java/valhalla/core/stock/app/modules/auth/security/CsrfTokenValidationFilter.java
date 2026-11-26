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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.logging.RequestFailureContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class CsrfTokenValidationFilter extends OncePerRequestFilter {

    public static final String CABECALHO_CSRF = "X-XSRF-TOKEN";
    public static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final Set<String> METODOS_PROTEGIDOS = Set.of(
            HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name()
    );
    /** Endpoints públicos que não dependem de uma sessão e, portanto, não exigem token CSRF. */
    private static final Set<String> ENDPOINTS_PUBLICOS = Set.of(
            "/auth/login", "/auth/recuperar-senha", "/auth/redefinir-senha"
    );
    private static final Logger LOGGER = LoggerFactory.getLogger(CsrfTokenValidationFilter.class);

    private final CsrfTokenStore csrfTokenStore;
    private final JwtDecoder refreshTokenDecoder;
    private final ObjectMapper objectMapper;
    private final TokenStateService tokenStateService;

    public CsrfTokenValidationFilter(
            CsrfTokenStore csrfTokenStore,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
            ObjectMapper objectMapper,
            TokenStateService tokenStateService
    ) {
        this.csrfTokenStore = csrfTokenStore;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.objectMapper = objectMapper;
        this.tokenStateService = tokenStateService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS_PROTEGIDOS.contains(request.getMethod()) || ENDPOINTS_PUBLICOS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        SessaoRequisicao sessao = obterSessaoAutenticada(request);
        // Sem uma sessão válida e ativa no servidor não há token CSRF a validar. A cadeia
        // de segurança/autenticação preserva a resposta 401 (ou 204 no logout idempotente).
        if (sessao == null || sessao.idSessao() == null || !sessao.ativaConfirmada()) {
            filterChain.doFilter(request, response);
            return;
        }
        String tokenNoCabecalho = request.getHeader(CABECALHO_CSRF);
        if (tokenNoCabecalho != null && !tokenNoCabecalho.isBlank()
                && csrfTokenStore.ehValido(sessao.idSessao(), tokenNoCabecalho)) {
            filterChain.doFilter(request, response);
            return;
        }

        LOGGER.atWarn().addKeyValue("event", "security.csrf.rejected")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .log("CSRF validation rejected");
        RequestFailureContext.markFailure(request, "CSRF_TOKEN_INVALID", "Token CSRF ausente ou inválido");
        escreverErroCsrf(response);
    }

    private SessaoRequisicao obterSessaoAutenticada(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return new SessaoRequisicao(converterUuid(token.getToken().getClaimAsString("sessionJti")), true);
        }

        String refreshToken = obterCookie(request, "refreshToken");
        if (refreshToken == null) {
            return null;
        }
        try {
            Jwt jwt = refreshTokenDecoder.decode(refreshToken);
            UUID refreshJti = converterUuid(jwt.getId());
            UUID userId = converterUuid(jwt.getClaimAsString("userId"));
            boolean ativa = userId != null && refreshJti != null
                    && tokenStateService.isRefreshActive(userId, refreshJti);
            return new SessaoRequisicao(refreshJti, ativa);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private record SessaoRequisicao(UUID idSessao, boolean ativaConfirmada) {
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
