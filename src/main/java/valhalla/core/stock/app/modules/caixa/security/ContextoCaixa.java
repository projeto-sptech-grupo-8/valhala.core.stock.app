package valhalla.core.stock.app.modules.caixa.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;

import java.util.UUID;

@Component
public class ContextoCaixa {

    public UUID estabelecimentoId() {
        return UUID.fromString(claimObrigatoria("establishmentId"));
    }

    public UUID usuarioId() {
        return UUID.fromString(claimObrigatoria("userId"));
    }

    public boolean isGerente() {
        return UserAuthorizationService.isManagerRole(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    private String claimObrigatoria(String nomeClaim) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }

        String valor = token.getToken().getClaimAsString(nomeClaim);
        if (valor == null || valor.isBlank()) {
            throw new AccessDeniedException("Sessão inválida");
        }

        try {
            UUID.fromString(valor);
            return valor;
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
