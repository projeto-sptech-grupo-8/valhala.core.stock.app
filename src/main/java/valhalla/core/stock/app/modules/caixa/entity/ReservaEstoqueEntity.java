package valhalla.core.stock.app.modules.caixa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reserva_estoque")
@Getter
@Setter
@NoArgsConstructor
public class ReservaEstoqueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "item_pedido_id", nullable = false)
    private Long itemPedidoId;

    @Column(name = "pagamento_pedido_id")
    private UUID pagamentoPedidoId;

    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @Column(name = "estoque_id", nullable = false)
    private Integer estoqueId;

    @Column(name = "origem_drink_id")
    private UUID origemDrinkId;

    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReservaEstoque status = StatusReservaEstoque.ATIVA;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "efetivada_em")
    private LocalDateTime efetivadaEm;

    @Column(name = "liberada_em")
    private LocalDateTime liberadaEm;

    @Column(name = "motivo_liberacao", length = 200)
    private String motivoLiberacao;

    @Column(name = "movimentacao_estoque_id")
    private Long movimentacaoEstoqueId;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
        if (status == null) {
            status = StatusReservaEstoque.ATIVA;
        }
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
