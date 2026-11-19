package valhalla.core.stock.app.modules.caixa.fiscal.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "item_nota_fiscal")
@Getter
@Setter
@NoArgsConstructor
public class ItemNotaFiscalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nota_fiscal_id", nullable = false)
    private UUID notaFiscalId;

    @Column(name = "produto_id")
    private UUID produtoId;

    @Column(name = "item_pedido_id")
    private Long itemPedidoId;

    @Column(name = "descricao_produto", nullable = false, length = 150)
    private String descricaoProduto;

    @Column(name = "unidade_medida", nullable = false, length = 10)
    private String unidadeMedida = "UN";

    @Column(name = "cfop", length = 4)
    private String cfop;

    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "valor_desconto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_tributarios")
    private JsonNode dadosTributarios;

    @PrePersist
    private void prePersist() {
        if (unidadeMedida == null || unidadeMedida.isBlank()) {
            unidadeMedida = "UN";
        }
        if (valorDesconto == null) {
            valorDesconto = BigDecimal.ZERO;
        }
    }
}
