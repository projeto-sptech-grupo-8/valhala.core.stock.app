package valhalla.core.stock.app.modules.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.auth.entity.PasswordResetTokenEntity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    boolean existsByUser_IdAndCreatedAtAfter(UUID userId, LocalDateTime instante);

    /** Marca o token como utilizado somente se ainda estiver pendente e válido (protege contra uso concorrente). */
    @Modifying
    @Query("""
            update PasswordResetTokenEntity t set t.usedAt = :agora
            where t.id = :id and t.usedAt is null and t.expiresAt > :agora
            """)
    int consumir(@Param("id") UUID id, @Param("agora") LocalDateTime agora);

    /** Invalida todos os tokens ainda pendentes do usuário. */
    @Modifying
    @Query("""
            update PasswordResetTokenEntity t set t.usedAt = :agora
            where t.user.id = :userId and t.usedAt is null
            """)
    int invalidarPendentes(@Param("userId") UUID userId, @Param("agora") LocalDateTime agora);
}
