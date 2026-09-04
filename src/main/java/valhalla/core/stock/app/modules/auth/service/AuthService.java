package valhalla.core.stock.app.modules.auth.service;

import jakarta.transaction.Transactional;
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

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenStateService tokenStateService;
    private final JwtDecoder accessTokenDecoder;
    private final JwtDecoder refreshTokenDecoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            TokenStateService tokenStateService,
            @Qualifier("jwtDecoder") JwtDecoder accessTokenDecoder,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tokenStateService = tokenStateService;
        this.accessTokenDecoder = accessTokenDecoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
    }

    @Transactional
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

        return issueTokens(authentication, user);
    }

    @Transactional
    public TokenResponseDto refresh(String refreshToken) {
        Jwt jwt = decodeRefreshToken(refreshToken);
        UUID userId = extractUserId(jwt);
        UUID refreshJti = extractRefreshJti(jwt);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException(
                        "Refresh token inválido"
                ));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new InvalidRefreshTokenException("Refresh token inválido");
        }

        Authentication authentication = authenticationFor(user);
        return issueTokens(authentication, user, refreshJti);
    }

    @Transactional
    public void logout(String refreshToken, String accessToken) {
        revokeByRefreshToken(refreshToken);
        revokeByAccessToken(accessToken);
    }

    private void revokeByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Jwt jwt = refreshTokenDecoder.decode(refreshToken);
            tokenStateService.revoke(
                    extractUserId(jwt),
                    null,
                    extractRefreshJti(jwt)
            );
        } catch (JwtException | InvalidRefreshTokenException ignored) {
            // Logout é idempotente: cookies inválidos também são descartados.
        }
    }

    private void revokeByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        try {
            Jwt jwt = accessTokenDecoder.decode(accessToken);
            tokenStateService.revoke(
                    extractUserId(jwt),
                    parseUuid(jwt.getId()),
                    parseUuid(jwt.getClaimAsString("sessionJti"))
            );
        } catch (JwtException | InvalidRefreshTokenException ignored) {
            // Logout é idempotente: cookies inválidos também são descartados.
        }
    }

    private TokenResponseDto issueTokens(
            Authentication authentication,
            UserEntity user
    ) {
        return issueTokens(authentication, user, null);
    }

    private TokenResponseDto issueTokens(
            Authentication authentication,
            UserEntity user,
            UUID expectedRefreshJti
    ) {
        UUID accessJti = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        Instant refreshExpiresAt = jwtService.refreshTokenExpiresAt();

        String accessToken = jwtService.generateToken(
                authentication,
                user.getId(),
                accessJti,
                refreshJti
        );
        String refreshToken = jwtService.generateRefreshToken(
                authentication,
                user.getId(),
                refreshJti,
                refreshExpiresAt
        );

        if (expectedRefreshJti == null) {
            tokenStateService.replace(user.getId(), accessJti, refreshJti);
        } else {
            tokenStateService.rotate(
                    user.getId(),
                    expectedRefreshJti,
                    accessJti,
                    refreshJti
            );
        }

        return new TokenResponseDto(accessToken, refreshToken, user.getName());
    }

    private Authentication authenticationFor(UserEntity user) {
        String role = "ROLE_" + CustomUserDetailsService.normalizeRole(
                user.getProfile().getName()
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority(role))
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
        return parseUuid(jwt.getClaimAsString("userId"));
    }

    private UUID extractRefreshJti(Jwt jwt) {
        return parseUuid(jwt.getId());
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRefreshTokenException("Refresh token inválido", exception);
        }
    }

}
