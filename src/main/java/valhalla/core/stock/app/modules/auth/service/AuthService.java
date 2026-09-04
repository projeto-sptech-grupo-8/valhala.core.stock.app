package valhalla.core.stock.app.modules.auth.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.auth.dto.LoginRequestDto;
import valhalla.core.stock.app.modules.auth.dto.TokenResponseDto;
import valhalla.core.stock.app.modules.auth.security.CustomUserDetailsService;
import valhalla.core.stock.app.modules.auth.security.JwtService;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.error.InvalidRefreshTokenException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JwtDecoder refreshTokenDecoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenDecoder = refreshTokenDecoder;
    }

    public TokenResponseDto login(LoginRequestDto request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        normalizedEmail,
                        request.password()
                )
        );

        UserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuário autenticado não encontrado"
                ));

        String accessToken = jwtService.generateToken(authentication, user.getId());
        String refreshToken = jwtService.generateRefreshToken(authentication, user.getId());

        return new TokenResponseDto(accessToken, refreshToken, user.getName());
    }

    public TokenResponseDto refresh(String refreshToken) {
        Jwt jwt = decodeRefreshToken(refreshToken);
        UUID userId = extractUserId(jwt);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException(
                        "Refresh token inválido"
                ));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new InvalidRefreshTokenException("Refresh token inválido");
        }

        String role = "ROLE_" + CustomUserDetailsService.normalizeRole(
                user.getProfile().getName()
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority(role))
        );

        String newAccessToken = jwtService.generateToken(authentication, user.getId());
        String newRefreshToken = jwtService.generateRefreshToken(
                authentication,
                user.getId()
        );

        return new TokenResponseDto(
                newAccessToken,
                newRefreshToken,
                user.getName()
        );
    }

    private Jwt decodeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token não informado");
        }

        try {
            return refreshTokenDecoder.decode(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException("Refresh token inválido", exception);
        }
    }

    private UUID extractUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRefreshTokenException("Refresh token inválido", exception);
        }
    }
}
