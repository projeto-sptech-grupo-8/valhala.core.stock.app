package valhalla.core.stock.app.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/** Token de uso único para redefinição de senha. Datas em UTC. */
@Entity
@Table(name = "token_recuperacao_senha")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordResetTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    /** Hash SHA-256 (hexadecimal) do token enviado por e-mail. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "utilizado_em")
    private LocalDateTime usedAt;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime createdAt;
}
