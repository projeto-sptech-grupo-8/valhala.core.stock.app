package valhalla.core.stock.app.modules.users.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.UUID;

@Component("userAuthorizationService")
@RequiredArgsConstructor
public class UserAuthorizationService {

    private static final String MANAGER_AUTHORITY = "ROLE_GERENTE";

    private final UserRepository userRepository;

    public boolean canUpdate(UUID userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (isManager(authentication)) {
            return true;
        }

        UUID authenticatedUserId = extractUserId(authentication);
        if (authenticatedUserId != null) {
            return userId.equals(authenticatedUserId);
        }

        return userRepository.existsByIdAndEmailIgnoreCase(
                userId,
                authentication.getName()
        );
    }

    public boolean isManager(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> MANAGER_AUTHORITY.equals(authority.getAuthority()))) {
            return false;
        }

        UUID authenticatedUserId = extractUserId(authentication);
        if (authenticatedUserId != null) {
            return userRepository.existsByIdAndProfile_NameIgnoreCase(
                    authenticatedUserId,
                    "Gerente"
            );
        }

        return userRepository.existsByEmailIgnoreCaseAndProfile_NameIgnoreCase(
                authentication.getName(),
                "Gerente"
        );
    }

    private UUID extractUserId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }

        String userId = jwtAuthentication.getToken().getClaimAsString("userId");
        if (userId == null) {
            return null;
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
