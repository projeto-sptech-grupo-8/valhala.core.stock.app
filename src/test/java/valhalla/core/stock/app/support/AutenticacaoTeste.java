package valhalla.core.stock.app.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Utilitário compartilhado pelos testes unitários para simular a sessão JWT multi-tenant.
 * Sempre combine {@link #autenticar} com {@link #limpar()} em um {@code @AfterEach}.
 */
public final class AutenticacaoTeste {

    private AutenticacaoTeste() {
    }

    /** Cria um token JWT com as claims usadas pela aplicação, sem registrá-lo no contexto. */
    public static JwtAuthenticationToken token(UUID estabelecimentoId, UUID usuarioId, String... authorities) {
        Jwt.Builder jwt = Jwt.withTokenValue("token-teste").header("alg", "none").subject("teste");
        if (estabelecimentoId != null) {
            jwt.claim("establishmentId", estabelecimentoId.toString());
        }
        if (usuarioId != null) {
            jwt.claim("userId", usuarioId.toString());
        }
        List<SimpleGrantedAuthority> permissoes = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        return new JwtAuthenticationToken(jwt.build(), permissoes);
    }

    /** Registra no {@link SecurityContextHolder} um token JWT com as claims informadas. */
    public static JwtAuthenticationToken autenticar(UUID estabelecimentoId, UUID usuarioId, String... authorities) {
        JwtAuthenticationToken autenticacao = token(estabelecimentoId, usuarioId, authorities);
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
        return autenticacao;
    }

    public static void limpar() {
        SecurityContextHolder.clearContext();
    }
}
