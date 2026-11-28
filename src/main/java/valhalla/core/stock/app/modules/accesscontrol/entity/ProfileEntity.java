package valhalla.core.stock.app.modules.accesscontrol.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

@Entity
@Table(name = "perfil")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estabelecimento_id", nullable = false)
    private EstablishmentEntity establishment;

    @Column(name = "nome", nullable = false, length = 100)
    private String name;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String description;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "perfil_funcionalidade",
            joinColumns = @JoinColumn(name = "perfil_id"),
            inverseJoinColumns = @JoinColumn(name = "funcionalidade_id")
    )
    @Builder.Default
    private Set<FuncionalidadeEntity> functionalities = new HashSet<>();

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = FusoHorarioOperacao.agora();
        createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = FusoHorarioOperacao.agora();
    }
}
