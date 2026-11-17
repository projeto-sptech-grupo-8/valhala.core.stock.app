package valhalla.core.stock.app.modules.vendas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoEntity pedido;

    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    /** Nome do produto no momento da venda. */
    @Column(name = "descricao_produto", nullable = false, length = 150)
    private String descricaoProduto;

    /** Unidades vendidas (garrafas, copos, drinks), não a unidade-base do estoque. */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** Custo de uma unidade vendida no momento da venda (base do CMV). */
    @Column(name = "custo_unitario", nullable = false, precision = 12, scale = 4)
    private BigDecimal custoUnitario;
}
