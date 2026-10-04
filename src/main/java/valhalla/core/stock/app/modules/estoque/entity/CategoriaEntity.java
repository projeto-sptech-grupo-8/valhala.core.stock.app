package valhalla.core.stock.app.modules.estoque.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CategoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "estabelecimento_id")
    private UUID estabelecimentoId;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    private String descricao;

    private boolean ativo;

    @CreationTimestamp
    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
