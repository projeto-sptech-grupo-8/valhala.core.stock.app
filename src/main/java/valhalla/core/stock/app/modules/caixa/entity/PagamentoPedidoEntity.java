package valhalla.core.stock.app.modules.caixa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "pagamento_pedido")
@Getter
@Setter
@NoArgsConstructor
public class PagamentoPedidoEntity {

    @Id
    private UUID id;

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "sessao_caixa_id")
    private UUID sessaoCaixaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma", nullable = false, length = 30)
    private FormaPagamentoCaixa forma;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusPagamentoPedido status;

    @Column(name = "provedor", nullable = false, length = 30)
    private String provedor = "INTERNO";

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "valor_cobrado", precision = 12, scale = 2)
    private BigDecimal valorCobrado;

    @Column(name = "valor_recebido", precision = 12, scale = 2)
    private BigDecimal valorRecebido;

    @Column(name = "valor_troco", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTroco = BigDecimal.ZERO;

    @Column(name = "valor_estornado", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorEstornado = BigDecimal.ZERO;

    @Column(name = "parcelas", nullable = false)
    private Integer parcelas = 1;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
        if (valorTroco == null) {
            valorTroco = BigDecimal.ZERO;
        }
        if (valorEstornado == null) {
            valorEstornado = BigDecimal.ZERO;
        }
        if (parcelas == null) {
            parcelas = 1;
        }
        if (provedor == null || provedor.isBlank()) {
            provedor = "INTERNO";
        }
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public boolean estaAprovado() {
        return status == StatusPagamentoPedido.APROVADO;
    }
}
