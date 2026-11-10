package valhalla.core.stock.app.modules.caixa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "item_pedido")
@Getter
@Setter
@NoArgsConstructor
public class ItemPedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @Column(name = "item_orcamento_id")
    private Long itemOrcamentoId;

    @Column(name = "descricao_produto", nullable = false, length = 150)
    private String descricaoProduto;

    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "desconto", nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "custo_unitario", precision = 12, scale = 2)
    private BigDecimal custoUnitario;

    @Column(name = "categoria_id")
    private Integer categoriaId;

    @Column(name = "categoria_nome", length = 100)
    private String categoriaNome;

    @Column(name = "quantidade_enviada_bar", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeEnviadaBar = BigDecimal.ZERO;

    @Column(name = "enviado_bar_em")
    private LocalDateTime enviadoBarEm;

    @PrePersist
    private void prePersist() {
        if (desconto == null) {
            desconto = BigDecimal.ZERO;
        }
        if (quantidadeEnviadaBar == null) {
            quantidadeEnviadaBar = BigDecimal.ZERO;
        }
    }
}
