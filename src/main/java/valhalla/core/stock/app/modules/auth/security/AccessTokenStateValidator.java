package valhalla.core.stock.app.modules.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessTokenStateValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token",
            "Token revogado ou usuário sem acesso",
            null
    );

    private final UserRepository userRepository;
    private final TokenStateService tokenStateService;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            UUID userId = UUID.fromString(token.getClaimAsString("userId"));
            UUID accessJti = UUID.fromString(token.getId());
            UUID sessionJti = UUID.fromString(
                    token.getClaimAsString("sessionJti")
            );

            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null
                    || !user.isActive()
                    || !token.getClaimAsString("establishmentId").equals(
                            user.getEstablishment().getId().toString())
                    || !hasCurrentAuthorizations(token, user)
                    || !tokenStateService.isActive(
                            userId,
                            accessJti,
                            sessionJti
                    )) {
                return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
            }

            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
    }

    private boolean hasCurrentAuthorizations(Jwt token, UserEntity user) {
        List<String> roles = token.getClaimAsStringList("roles");
        Set<String> expectedRoles = CustomUserDetailsService.authoritiesFor(user).stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(java.util.stream.Collectors.toSet());
        return roles != null
                && expectedRoles.equals(Set.copyOf(roles));
    }
}
