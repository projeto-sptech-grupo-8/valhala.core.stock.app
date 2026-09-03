package valhalla.core.stock.app.shared.utils;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatorUtils {

    public UUID getUserId() {
        String userId = getAuthentication()
                .getToken()
                .getClaimAsString("userId");

        if (userId == null || userId.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "O token não possui o identificador do usuário"
            );
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "O identificador do usuário no token é inválido",
                    exception
            );
        }
    }

    public String getEmail() {
        return getAuthentication().getName();
    }

    public boolean hasRole(String role) {
        String authority = role.startsWith("ROLE_")
                ? role
                : "ROLE_" + role;

        return getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private JwtAuthenticationToken getAuthentication() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Não existe usuário autenticado nesta requisição"
            );
        }

        return jwt;
    }
}