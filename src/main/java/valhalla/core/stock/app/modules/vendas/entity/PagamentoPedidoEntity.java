package valhalla.core.stock.app.modules.vendas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Valor efetivamente recebido na forma de pagamento (dinheiro já sem o troco). */
@Entity
@Table(name = "pagamento_pedido")
@Getter
@Setter
@NoArgsConstructor
public class PagamentoPedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoEntity pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FormaPagamento forma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPagamento status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
