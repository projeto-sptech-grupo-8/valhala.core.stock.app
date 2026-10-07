package valhalla.core.stock.app.modules.estoque.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
public class ProdutoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaEntity categoria;

    @Column(nullable = false, length = 45)
    private String sku;

    @Column(name = "codigo_barras", length = 45)
    private String codigoBarras;

    @Column(nullable = false, length = 150)
    private String nome;

    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ProdutoTipo tipo;

    @Column(name = "unidade_medida", nullable = false, length = 10)
    private String unidadeMedida;

    @Column(name = "preco_custo", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVenda;

    @Column(nullable = false)
    private boolean fracionado;

    @Column(name = "volume_embalagem_ml", precision = 12, scale = 3)
    private BigDecimal volumeEmbalagemMl;

    @Column(nullable = false)
    private boolean ativo;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
