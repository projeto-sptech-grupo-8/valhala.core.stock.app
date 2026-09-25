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

    public boolean canView(UUID userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (hasPermission(authentication, "USUARIOS_VISUALIZAR")) {
            UUID establishmentId = extractEstablishmentId(authentication);
            if (establishmentId == null) {
                return false;
            }
            // Permite que o serviço devolva 404 para um UUID inexistente, mas
            // bloqueia explicitamente usuários existentes de outro estabelecimento.
            return !userRepository.existsById(userId) || userRepository
                    .existsByIdAndEstablishment_Id(userId, establishmentId);
        }

        UUID authenticatedUserId = extractUserId(authentication);
        if (authenticatedUserId != null) {
            return userId.equals(authenticatedUserId);
        }

        return false;
    }

    public boolean canUpdate(UUID userId, Authentication authentication) {
        return isCurrentUser(userId, authentication)
                || canManageInSameEstablishment(userId, authentication, "USUARIOS_EDITAR");
    }

    public boolean canDelete(UUID userId, Authentication authentication) {
        return isCurrentUser(userId, authentication)
                || canManageInSameEstablishment(userId, authentication, "USUARIOS_EXCLUIR");
    }

    public boolean isManager(Authentication authentication) {
        if (!isManagerRole(authentication)) {
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

    public boolean hasPermission(Authentication authentication, String permissionCode) {
        return isManagerRole(authentication) || authentication != null
                && authentication.getAuthorities().stream().anyMatch(authority ->
                permissionCode.equals(authority.getAuthority()));
    }

    private boolean isCurrentUser(UUID userId, Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && userId.equals(extractUserId(authentication));
    }

    private boolean canManageInSameEstablishment(UUID userId, Authentication authentication,
                                                  String permissionCode) {
        if (!hasPermission(authentication, permissionCode)) return false;
        UUID establishmentId = extractEstablishmentId(authentication);
        return establishmentId != null && ( !userRepository.existsById(userId)
                || userRepository.existsByIdAndEstablishment_Id(userId, establishmentId));
    }

    public static boolean isManagerRole(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> MANAGER_AUTHORITY.equals(authority.getAuthority()));
    }

    private UUID extractEstablishmentId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) return null;
        try {
            return UUID.fromString(jwtAuthentication.getToken()
                    .getClaimAsString("establishmentId"));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }
}
