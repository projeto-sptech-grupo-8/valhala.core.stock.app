package valhalla.core.stock.app.modules.accesscontrol.entity;

import jakarta.persistence.*;
import lombok.*;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

@Entity
@Table(name = "usuario_funcionalidade")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserFunctionalityOverrideEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "funcionalidade_id", nullable = false)
    private FuncionalidadeEntity functionality;

    @Enumerated(EnumType.STRING)
    @Column(name = "efeito", nullable = false, length = 10)
    private PermissionEffect effect;
}
