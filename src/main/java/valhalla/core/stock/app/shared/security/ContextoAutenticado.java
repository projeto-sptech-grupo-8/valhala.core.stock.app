package valhalla.core.stock.app.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/** Identificadores da sessão autenticada, lidos das claims do access token. */
public final class ContextoAutenticado {

    private ContextoAutenticado() {
    }

    public static UUID idEstabelecimento() {
        return claimUuid("establishmentId");
    }

    public static UUID idUsuario() {
        return claimUuid("userId");
    }

    private static UUID claimUuid(String nome) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            String valor = token.getToken().getClaimAsString(nome);
            if (valor == null || valor.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }
            return UUID.fromString(valor);
        } catch (IllegalArgumentException excecao) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
