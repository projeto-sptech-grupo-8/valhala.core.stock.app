package valhalla.core.stock.app.modules.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.auth.dto.LoginRequestDto;
import valhalla.core.stock.app.modules.auth.dto.TokenResponseDto;
import valhalla.core.stock.app.modules.auth.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletResponse response
    ) {

        TokenResponseDto tokenResponseDto = authService.login(request);

        ResponseCookie responseCookie = ResponseCookie.from("accessToken", tokenResponseDto.acessToken())
                .httpOnly(true)
                .secure(false) // alterar para true em prod
                .path("/")
                .maxAge(15 * 60)
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokenResponseDto.refreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/auth/refresh")
                .maxAge(7 * 24 * 60 * 60)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(Map.of(
                "message", "Autenticação realizada com sucesso",
                "user", tokenResponseDto.username()
        ));

    }
}
