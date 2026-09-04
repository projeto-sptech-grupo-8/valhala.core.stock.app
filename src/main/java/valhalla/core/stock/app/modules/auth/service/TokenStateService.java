package valhalla.core.stock.app.modules.auth.service;

import org.springframework.stereotype.Service;
import valhalla.core.stock.app.shared.error.InvalidRefreshTokenException;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TokenStateService {

    private final ConcurrentMap<UUID, TokenState> activeTokens =
            new ConcurrentHashMap<>();

    public void replace(
            UUID userId,
            UUID accessJti,
            UUID refreshJti
    ) {
        activeTokens.put(userId, new TokenState(accessJti, refreshJti));
    }

    public void rotate(
            UUID userId,
            UUID expectedRefreshJti,
            UUID newAccessJti,
            UUID newRefreshJti
    ) {
        AtomicBoolean rotated = new AtomicBoolean(false);
        activeTokens.computeIfPresent(userId, (id, current) -> {
            if (!Objects.equals(current.refreshJti(), expectedRefreshJti)) {
                return current;
            }

            rotated.set(true);
            return new TokenState(newAccessJti, newRefreshJti);
        });

        if (!rotated.get()) {
            throw new InvalidRefreshTokenException("Refresh token inválido");
        }
    }

    public boolean isActive(
            UUID userId,
            UUID accessJti,
            UUID refreshJti
    ) {
        TokenState current = activeTokens.get(userId);
        return current != null
                && Objects.equals(current.accessJti(), accessJti)
                && Objects.equals(current.refreshJti(), refreshJti);
    }

    public void revoke(
            UUID userId,
            UUID accessJti,
            UUID refreshJti
    ) {
        activeTokens.computeIfPresent(userId, (id, current) -> {
            boolean refreshMatches = Objects.equals(
                    current.refreshJti(),
                    refreshJti
            );
            boolean accessMatches = accessJti == null
                    || Objects.equals(current.accessJti(), accessJti);

            return refreshMatches && accessMatches ? null : current;
        });
    }

    public void revokeAll(UUID userId) {
        activeTokens.remove(userId);
    }

    private record TokenState(UUID accessJti, UUID refreshJti) {
    }
}
