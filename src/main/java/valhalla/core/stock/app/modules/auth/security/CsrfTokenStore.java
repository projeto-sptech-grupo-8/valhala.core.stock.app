package valhalla.core.stock.app.modules.auth.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Armazena o token CSRF associado à sessão autenticada.
 *
 * A implementação atual é local à instância da API. Em produção com múltiplas
 * instâncias, esta interface deve receber uma implementação respaldada por Redis.
 */
public interface CsrfTokenStore {

    String criar(UUID idSessao, Instant expiraEm);

    String obter(UUID idSessao);

    boolean ehValido(UUID idSessao, String token);

    void remover(UUID idSessao);
}
