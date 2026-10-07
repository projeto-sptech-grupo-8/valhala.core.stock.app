package valhalla.core.stock.app.modules.auth.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryCsrfTokenStore implements CsrfTokenStore {

    private static final int TAMANHO_TOKEN_EM_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<UUID, TokenArmazenado> tokens = new ConcurrentHashMap<>();

    @Override
    public String criar(UUID idSessao, Instant expiraEm) {
        removerExpirados();
        byte[] bytes = new byte[TAMANHO_TOKEN_EM_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(idSessao, new TokenArmazenado(token, expiraEm));
        return token;
    }

    @Override
    public boolean ehValido(UUID idSessao, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        TokenArmazenado armazenado = tokens.get(idSessao);
        if (armazenado == null || !armazenado.expiraEm().isAfter(Instant.now())) {
            tokens.remove(idSessao, armazenado);
            return false;
        }
        return MessageDigest.isEqual(
                armazenado.valor().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String obter(UUID idSessao) {
        TokenArmazenado armazenado = tokens.get(idSessao);
        if (armazenado == null || !armazenado.expiraEm().isAfter(Instant.now())) {
            tokens.remove(idSessao, armazenado);
            return null;
        }
        return armazenado.valor();
    }

    @Override
    public void remover(UUID idSessao) {
        tokens.remove(idSessao);
    }

    private void removerExpirados() {
        Instant agora = Instant.now();
        tokens.entrySet().removeIf(entrada -> !entrada.getValue().expiraEm().isAfter(agora));
    }

    private record TokenArmazenado(String valor, Instant expiraEm) {
    }
}
