package valhalla.core.stock.app.modules.auth.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.auth.entity.UserSessionEntity;

import java.time.LocalDateTime;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {
    boolean existsByUserIdAndAccessJtiAndRefreshJtiAndExpiresAtAfter(
            UUID userId, UUID accessJti, UUID refreshJti, LocalDateTime now
    );

    @Modifying
    @Query("""
            update UserSessionEntity s set s.accessJti = :accessJti,
            s.refreshJti = :refreshJti, s.expiresAt = :expiresAt
            where s.userId = :userId and s.refreshJti = :expectedRefreshJti
            """)
    int rotate(@Param("userId") UUID userId,
               @Param("expectedRefreshJti") UUID expectedRefreshJti,
               @Param("accessJti") UUID accessJti,
               @Param("refreshJti") UUID refreshJti,
               @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Query("delete from UserSessionEntity s where s.userId = :userId and " +
            "(:accessJti is null or s.accessJti = :accessJti) and s.refreshJti = :refreshJti")
    int revoke(@Param("userId") UUID userId, @Param("accessJti") UUID accessJti,
               @Param("refreshJti") UUID refreshJti);
}
