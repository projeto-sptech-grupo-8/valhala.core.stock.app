package valhalla.core.stock.app.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.auth.entity.UserSessionEntity;
import valhalla.core.stock.app.modules.auth.repository.UserSessionRepository;
import valhalla.core.stock.app.modules.auth.security.CsrfTokenStore;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.error.InvalidRefreshTokenException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenStateService {

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final CsrfTokenStore csrfTokenStore;

    public Optional<UUID> replace(
            UUID userId,
            UUID accessJti,
            UUID refreshJti,
            Instant expiresAt,
            UserEntity user
    ) {
        userRepository.findByIdForUpdate(userId);
        LocalDateTime expiration = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        Optional<UserSessionEntity> existing = userSessionRepository.findById(userId);
        Optional<UUID> previousRefreshJti = existing.map(UserSessionEntity::getRefreshJti);
        existing.ifPresentOrElse(session -> {
            session.setAccessJti(accessJti);
            session.setRefreshJti(refreshJti);
            session.setExpiresAt(expiration);
        }, () -> userSessionRepository.save(UserSessionEntity.builder()
                .user(user).accessJti(accessJti).refreshJti(refreshJti)
                .expiresAt(expiration).build()));
        return previousRefreshJti;
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

    public boolean isRefreshActive(UUID userId, UUID refreshJti) {
        return userSessionRepository.existsByUserIdAndRefreshJtiAndExpiresAtAfter(
                userId, refreshJti, LocalDateTime.now(ZoneOffset.UTC));
    }

    public Optional<Instant> findActiveExpiration(UUID userId, UUID refreshJti) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return userSessionRepository.findById(userId)
                .filter(session -> refreshJti.equals(session.getRefreshJti()))
                .filter(session -> session.getExpiresAt().isAfter(now))
                .map(session -> session.getExpiresAt().toInstant(ZoneOffset.UTC));
    }

    public void revoke(
            UUID userId,
            UUID accessJti,
            UUID refreshJti
    ) {
        userSessionRepository.revoke(userId, accessJti, refreshJti);
    }

    public Optional<UUID> revokeAll(UUID userId) {
        Optional<UUID> previousRefreshJti = userSessionRepository.findById(userId)
                .map(UserSessionEntity::getRefreshJti);
        userSessionRepository.deleteById(userId);
        previousRefreshJti.ifPresent(csrfTokenStore::remover);
        return previousRefreshJti;
    }
}
