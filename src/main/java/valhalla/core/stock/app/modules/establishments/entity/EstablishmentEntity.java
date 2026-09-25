package valhalla.core.stock.app.modules.establishments.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "estabelecimento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EstablishmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "razao_social", nullable = false, length = 150)
    private String corporateName;
    @Column(name = "nome_fantasia", nullable = false, length = 150)
    private String tradeName;
    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;
    @Column(name = "ativo", nullable = false)
    private Boolean active;
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); updatedAt = createdAt; if (active == null) active = true; }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
