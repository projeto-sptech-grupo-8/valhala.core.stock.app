package valhalla.core.stock.app.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sessao_usuario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSessionEntity {
    @Id
    @Column(name = "usuario_id")
    private UUID userId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "usuario_id")
    private UserEntity user;
    @Column(name = "access_jti", nullable = false, unique = true)
    private UUID accessJti;
    @Column(name = "refresh_jti", nullable = false, unique = true)
    private UUID refreshJti;
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiresAt;
}
