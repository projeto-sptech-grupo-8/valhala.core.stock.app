package valhalla.core.stock.app.modules.vendas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Venda concluída no caixa. Valores monetários com 2 casas; datas no fuso da operação. */
@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
public class PedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(name = "operador_usuario_id", nullable = false)
    private UUID operadorUsuarioId;

    /** Sequencial por estabelecimento. */
    @Column(nullable = false)
    private Long numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private CanalVenda canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "nome_cliente", length = 150)
    private String nomeCliente;

    @Column(name = "documento_cliente", length = 20)
    private String documentoCliente;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "concluido_em")
    private LocalDateTime concluidoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "motivo_cancelamento", length = 255)
    private String motivoCancelamento;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ItemPedidoEntity> itens = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("criadoEm ASC")
    private List<PagamentoPedidoEntity> pagamentos = new ArrayList<>();

    public void adicionarItem(ItemPedidoEntity item) {
        item.setPedido(this);
        itens.add(item);
    }

    public void adicionarPagamento(PagamentoPedidoEntity pagamento) {
        pagamento.setPedido(this);
        pagamentos.add(pagamento);
    }
}
