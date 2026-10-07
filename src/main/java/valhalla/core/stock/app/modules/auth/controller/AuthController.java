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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import valhalla.core.stock.app.modules.auth.config.JwtProperties;
import valhalla.core.stock.app.modules.auth.dto.RequisicaoLoginDto;
import valhalla.core.stock.app.modules.auth.dto.RespostaAutenticacaoDto;
import valhalla.core.stock.app.modules.auth.dto.RespostaCsrfTokenDto;
import valhalla.core.stock.app.modules.auth.dto.RespostaTokenDto;
import valhalla.core.stock.app.modules.auth.service.AuthService;
import valhalla.core.stock.app.modules.auth.security.CsrfTokenStore;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Tag(
        name = "Autenticação",
        description = "Endpoints responsáveis pela autenticação e gerenciamento da sessão do usuário."
)
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final boolean secureCookies;
    private final CsrfTokenStore csrfTokenStore;

    public AuthController(
            AuthService authService,
            JwtProperties jwtProperties,
            CsrfTokenStore csrfTokenStore,
            @Value("${app.cookie.secure:false}") boolean secureCookies
    ) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.csrfTokenStore = csrfTokenStore;
        this.secureCookies = secureCookies;
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
    public ResponseEntity<RespostaCsrfTokenDto> obterTokenCsrf(@AuthenticationPrincipal Jwt jwt) {
        UUID idSessao = UUID.fromString(jwt.getClaimAsString("sessionJti"));
        String token = csrfTokenStore.obter(idSessao);
        if (token == null) {
            throw new AccessDeniedException("Sessão inválida");
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

    private void addAuthenticationCookies(
            HttpServletResponse response,
            RespostaTokenDto tokenResponse
    ) {
        ResponseCookie accessCookie = ResponseCookie
                .from("accessToken", tokenResponse.tokenAcesso())
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(jwtProperties.expiration())
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie
                .from("refreshToken", tokenResponse.tokenRenovacao())
                .httpOnly(true)
                .secure(secureCookies)
                .path("/auth")
                .maxAge(jwtProperties.refreshExpiration())
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthenticationCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = expiredCookie("accessToken", "/");
        ResponseCookie refreshCookie = expiredCookie("refreshToken", "/auth");

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookies)
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
