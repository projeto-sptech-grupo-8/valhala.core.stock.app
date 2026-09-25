package valhalla.core.stock.app.modules.users.entity;

import jakarta.persistence.*;
import lombok.*;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.UserFunctionalityOverrideEntity;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "telefone", length = 13)
    private String phone;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private EstablishmentEntity establishment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private ProfileEntity profile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<UserFunctionalityOverrideEntity> functionalityOverrides = new HashSet<>();

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "tentativas_login", nullable = false)
    private Integer loginAttempts;

    @Column(name = "bloqueado_ate")
    private LocalDateTime lockedUntil;

    @Column(name = "ultimo_login_em")
    private LocalDateTime lastLoginAt;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();

        updatedAt = createdAt;
        if (status == null) status = UserStatus.ATIVO;
        if (loginAttempts == null) loginAttempts = 0;
    }

    @PreUpdate
    private void preUpdate() { updatedAt = LocalDateTime.now(); }

    public boolean isActive() { return status == UserStatus.ATIVO; }

    /** Compatibilidade temporária para consumidores que ainda usam o contrato booleano. */
    public Boolean getActive() { return isActive(); }
}
