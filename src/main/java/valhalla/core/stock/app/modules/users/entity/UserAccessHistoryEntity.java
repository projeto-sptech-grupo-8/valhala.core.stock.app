package valhalla.core.stock.app.modules.users.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "historico_acesso_usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccessHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private EstablishmentEntity establishment;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 30)
    private TipoEventoAcessoUsuario eventType;

    @Column(name = "ip_origem", length = 45)
    private String sourceIp;

    @Column(name = "dispositivo", length = 120)
    private String device;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    private void prePersist() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
