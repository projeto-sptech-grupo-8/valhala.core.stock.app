package valhalla.core.stock.app.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.auth.dto.LoginRequestDto;
import valhalla.core.stock.app.modules.auth.dto.TokenResponseDto;
import valhalla.core.stock.app.modules.auth.security.JwtService;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

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
}
