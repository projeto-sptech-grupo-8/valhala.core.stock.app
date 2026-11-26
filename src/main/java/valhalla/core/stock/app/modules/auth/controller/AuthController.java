package valhalla.core.stock.app.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import valhalla.core.stock.app.modules.auth.config.JwtProperties;
import valhalla.core.stock.app.modules.auth.dto.RequisicaoLoginDto;
import valhalla.core.stock.app.modules.auth.dto.RequisicaoRecuperacaoSenhaDto;
import valhalla.core.stock.app.modules.auth.dto.RequisicaoRedefinicaoSenhaDto;
import valhalla.core.stock.app.modules.auth.dto.RespostaAutenticacaoDto;
import valhalla.core.stock.app.modules.auth.dto.RespostaCsrfTokenDto;
import valhalla.core.stock.app.modules.auth.dto.RespostaMensagemDto;
import valhalla.core.stock.app.modules.auth.dto.RespostaTokenDto;
import valhalla.core.stock.app.modules.auth.service.AuthService;
import valhalla.core.stock.app.modules.auth.service.RecuperacaoSenhaService;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.auth.security.CsrfTokenStore;
import valhalla.core.stock.app.shared.config.CookieProperties;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(
        name = "Autenticação",
        description = "Endpoints responsáveis pela autenticação e gerenciamento da sessão do usuário."
)
public class AuthController {

    private static final String COOKIE_CSRF = "XSRF-TOKEN";

    private final AuthService authService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;
    private final CsrfTokenStore csrfTokenStore;
    private final TokenStateService tokenStateService;

    public AuthController(
            AuthService authService,
            RecuperacaoSenhaService recuperacaoSenhaService,
            JwtProperties jwtProperties,
            CsrfTokenStore csrfTokenStore,
            TokenStateService tokenStateService,
            CookieProperties cookieProperties
    ) {
        this.authService = authService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.jwtProperties = jwtProperties;
        this.csrfTokenStore = csrfTokenStore;
        this.tokenStateService = tokenStateService;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Realizar login",
            description = "Autentica o usuário e cria os cookies de acesso e renovação da sessão."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticação realizada com sucesso.",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "Cookies HttpOnly accessToken e refreshToken são criados."),
                    content = @Content(schema = @Schema(implementation = RespostaAutenticacaoDto.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuário ou senha inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RespostaAutenticacaoDto> login(
            @Valid @RequestBody RequisicaoLoginDto request,
            HttpServletResponse response
    ) {

        RespostaTokenDto tokenResponseDto = authService.login(request);
        addAuthenticationCookies(response, tokenResponseDto);

        return authenticationResponse(
                "Autenticação realizada com sucesso",
                tokenResponseDto.nomeUsuario()
        );
    }

    @PostMapping("/refresh")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Renovar autenticação",
            description = "Utiliza o refresh token armazenado no cookie para gerar novos tokens."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticação renovada com sucesso.",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "Cookies HttpOnly accessToken e refreshToken são substituídos."),
                    content = @Content(schema = @Schema(implementation = RespostaAutenticacaoDto.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Refresh token ausente, inválido ou expirado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<RespostaAutenticacaoDto> refresh(
            @Parameter(name = "refreshToken", in = ParameterIn.COOKIE,
                    description = "Cookie HttpOnly com o refresh token.")
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {

        RespostaTokenDto tokenResponseDto = authService.refresh(refreshToken);
        addAuthenticationCookies(response, tokenResponseDto);

        return authenticationResponse(
                "Autenticação renovada com sucesso",
                tokenResponseDto.nomeUsuario()
        );
    }

    @GetMapping("/csrf")
    @SecurityRequirement(name = "accessTokenCookie")
    @Operation(summary = "Obter token CSRF da sessão autenticada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token CSRF retornado.",
                    content = @Content(schema = @Schema(implementation = RespostaCsrfTokenDto.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaCsrfTokenDto> obterTokenCsrf(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response
    ) {
        UUID idSessao = UUID.fromString(jwt.getClaimAsString("sessionJti"));
        UUID idUsuario = UUID.fromString(jwt.getClaimAsString("userId"));
        String token = csrfTokenStore.obter(idSessao);
        if (token == null) {
            Instant agora = Instant.now();
            Instant expiraEm = tokenStateService.findActiveExpiration(idUsuario, idSessao)
                    .orElseGet(() -> agora.plus(jwtProperties.refreshExpiration()));
            token = csrfTokenStore.criar(idSessao, expiraEm);
            Duration maxAge = Duration.between(agora, expiraEm);
            if (maxAge.isNegative()) {
                maxAge = Duration.ZERO;
            }
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie
                    .from(COOKIE_CSRF, token)
                    .httpOnly(false)
                    .secure(cookieProperties.secure())
                    .path("/")
                    .maxAge(maxAge)
                    .sameSite("Strict")
                    .build()
                    .toString());
        }
        return ResponseEntity.ok(new RespostaCsrfTokenDto(token));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Realizar logout",
            description = "Invalida os tokens atuais e remove os cookies de autenticação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout realizado e cookies removidos.",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "Cookies accessToken e refreshToken são expirados."))
    })
    public ResponseEntity<Void> logout(
            @Parameter(name = "refreshToken", in = ParameterIn.COOKIE,
                    description = "Cookie HttpOnly com o refresh token.")
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            @Parameter(name = "accessToken", in = ParameterIn.COOKIE,
                    description = "Cookie HttpOnly com o access token.")
            @CookieValue(name = "accessToken", required = false) String accessToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, accessToken);
        clearAuthenticationCookies(response);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recuperar-senha")
    @Operation(
            summary = "Solicitar recuperação de senha",
            description = "Envia para o e-mail informado um link de uso único para redefinir a senha. "
                    + "A resposta é sempre a mesma, independentemente de o e-mail estar cadastrado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solicitação recebida.",
                    content = @Content(schema = @Schema(implementation = RespostaMensagemDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaMensagemDto> solicitarRecuperacaoSenha(
            @Valid @RequestBody RequisicaoRecuperacaoSenhaDto request
    ) {
        recuperacaoSenhaService.solicitar(request.email());
        return ResponseEntity.accepted().body(new RespostaMensagemDto(
                "Se o e-mail estiver cadastrado, você receberá as instruções para redefinir a senha"
        ));
    }

    @PostMapping("/redefinir-senha")
    @Operation(
            summary = "Redefinir senha",
            description = "Define uma nova senha a partir do token recebido por e-mail. "
                    + "Todas as sessões ativas do usuário são encerradas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha redefinida e cookies de sessão removidos.",
                    headers = @Header(name = HttpHeaders.SET_COOKIE,
                            description = "Cookies accessToken, refreshToken e XSRF-TOKEN são expirados.")),
            @ApiResponse(responseCode = "400",
                    description = "Dados de entrada inválidos ou token inválido, expirado ou já utilizado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> redefinirSenha(
            @Valid @RequestBody RequisicaoRedefinicaoSenhaDto request,
            HttpServletResponse response
    ) {
        recuperacaoSenhaService.redefinir(request.token(), request.novaSenha());
        clearAuthenticationCookies(response);
        return ResponseEntity.noContent().build();
    }

    private void addAuthenticationCookies(
            HttpServletResponse response,
            RespostaTokenDto tokenResponse
    ) {
        ResponseCookie accessCookie = ResponseCookie
                .from("accessToken", tokenResponse.tokenAcesso())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(jwtProperties.expiration())
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie
                .from("refreshToken", tokenResponse.tokenRenovacao())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path("/auth")
                .maxAge(jwtProperties.refreshExpiration())
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie
                .from(COOKIE_CSRF, tokenResponse.tokenCsrf())
                .httpOnly(false)
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(jwtProperties.refreshExpiration())
                .sameSite("Strict")
                .build()
                .toString());
    }

    private void clearAuthenticationCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = expiredCookie("accessToken", "/", true);
        ResponseCookie refreshCookie = expiredCookie("refreshToken", "/auth", true);
        ResponseCookie csrfCookie = expiredCookie(COOKIE_CSRF, "/", false);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    private ResponseCookie expiredCookie(String name, String path, boolean httpOnly) {
        return ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(cookieProperties.secure())
                .path(path)
                .maxAge(Duration.ZERO)
                .sameSite("Strict")
                .build();
    }

    private ResponseEntity<RespostaAutenticacaoDto> authenticationResponse(
            String mensagem,
            String nomeUsuario
    ) {
        return ResponseEntity.ok(new RespostaAutenticacaoDto(mensagem, nomeUsuario));
    }
}
