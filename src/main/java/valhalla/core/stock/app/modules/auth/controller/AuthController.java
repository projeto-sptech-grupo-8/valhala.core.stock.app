package valhalla.core.stock.app.modules.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.auth.config.JwtProperties;
import valhalla.core.stock.app.modules.auth.dto.LoginRequestDto;
import valhalla.core.stock.app.modules.auth.dto.TokenResponseDto;
import valhalla.core.stock.app.modules.auth.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final boolean secureCookies;

    public AuthController(
            AuthService authService,
            JwtProperties jwtProperties,
            @Value("${app.cookie.secure:false}") boolean secureCookies
    ) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletResponse response
    ) {

        TokenResponseDto tokenResponseDto = authService.login(request);
        addAuthenticationCookies(response, tokenResponseDto);

        return authenticationResponse(
                "Autenticação realizada com sucesso",
                tokenResponseDto.username()
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        TokenResponseDto tokenResponseDto = authService.refresh(refreshToken);
        addAuthenticationCookies(response, tokenResponseDto);

        return authenticationResponse(
                "Autenticação renovada com sucesso",
                tokenResponseDto.username()
        );
    }

    private void addAuthenticationCookies(
            HttpServletResponse response,
            TokenResponseDto tokenResponse
    ) {
        ResponseCookie accessCookie = ResponseCookie
                .from("accessToken", tokenResponse.accessToken())
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(jwtProperties.expiration())
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie
                .from("refreshToken", tokenResponse.refreshToken())
                .httpOnly(true)
                .secure(secureCookies)
                .path("/auth/refresh")
                .maxAge(jwtProperties.refreshExpiration())
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private ResponseEntity<Map<String, String>> authenticationResponse(
            String message,
            String username
    ) {
        return ResponseEntity.ok(Map.of(
                "message", message,
                "user", username
        ));
    }
}
