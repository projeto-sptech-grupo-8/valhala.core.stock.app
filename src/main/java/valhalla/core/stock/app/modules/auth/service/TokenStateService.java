package valhalla.core.stock.app.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.auth.entity.UserSessionEntity;
import valhalla.core.stock.app.modules.auth.repository.UserSessionRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.shared.error.InvalidRefreshTokenException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenStateService {

    private final UserSessionRepository userSessionRepository;

    public void replace(
            UUID userId,
            UUID accessJti,
            UUID refreshJti,
            Instant expiresAt,
            UserEntity user
    ) {
        LocalDateTime expiration = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        userSessionRepository.findById(userId).ifPresentOrElse(session -> {
            session.setAccessJti(accessJti);
            session.setRefreshJti(refreshJti);
            session.setExpiresAt(expiration);
        }, () -> userSessionRepository.save(UserSessionEntity.builder()
                .user(user).accessJti(accessJti).refreshJti(refreshJti)
                .expiresAt(expiration).build()));
    }

    public void rotate(
            UUID userId,
            UUID expectedRefreshJti,
            UUID newAccessJti,
            UUID newRefreshJti,
            Instant expiresAt
    ) {
        if (userSessionRepository.rotate(userId, expectedRefreshJti, newAccessJti,
                newRefreshJti, LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)) != 1) {
            throw new InvalidRefreshTokenException("Refresh token inválido");
        }
    }

    public boolean isActive(
            UUID userId,
            UUID accessJti,
            UUID refreshJti
    ) {
        return userSessionRepository.existsByUserIdAndAccessJtiAndRefreshJtiAndExpiresAtAfter(
                userId, accessJti, refreshJti, LocalDateTime.now(ZoneOffset.UTC));
    }

    public void revoke(
            UUID userId,
            UUID accessJti,
            UUID refreshJti
    ) {
        userSessionRepository.revoke(userId, accessJti, refreshJti);
    }

    public void revokeAll(UUID userId) {
        userSessionRepository.deleteById(userId);
    }
}
