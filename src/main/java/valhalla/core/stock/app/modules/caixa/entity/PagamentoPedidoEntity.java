package valhalla.core.stock.app.modules.caixa.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @ManyToOne
    @JoinColumn(name = "terminal_caixa_id")
    private TerminalCaixaEntity terminalCaixa;

    @Column(name = "terminal_id", length = 100)
    private String terminalId;

    @Column(name = "taxa_percentual_aplicada", precision = 7, scale = 4)
    private BigDecimal taxaPercentualAplicada;

    @Column(name = "taxa_fixa_aplicada", precision = 12, scale = 2)
    private BigDecimal taxaFixaAplicada;

    @Column(name = "valor_taxa_estimada", precision = 12, scale = 2)
    private BigDecimal valorTaxaEstimada;

    @Column(name = "valor_liquido_estimado", precision = 12, scale = 2)
    private BigDecimal valorLiquidoEstimado;

    @Column(name = "valor_juros", precision = 12, scale = 2)
    private BigDecimal valorJuros;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma", nullable = false, length = 30)
    private FormaPagamentoCaixa forma;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusPagamentoPedido status;

    @Column(name = "provedor", nullable = false, length = 30)
    private String provedor = "INTERNO";

    @Column(name = "metodo_pagamento", length = 40)
    private String metodoPagamento;

    @Column(name = "referencia_externa", length = 150)
    private String referenciaExterna;

    @Column(name = "order_externa_id", length = 100)
    private String orderExternaId;

    @Column(name = "payment_externo_id", length = 100)
    private String paymentExternoId;

    @Column(name = "status_detalhe", length = 100)
    private String statusDetalhe;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_juros", length = 30)
    private PoliticaJurosCaixa politicaJuros;

    @Column(name = "chave_idempotencia", length = 100)
    private String chaveIdempotencia;

    @Column(name = "comprovante_referencia", length = 255)
    private String comprovanteReferencia;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comprovante_dados")
    private JsonNode comprovanteDados;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    @Column(name = "motivo_estorno", columnDefinition = "TEXT")
    private String motivoEstorno;

    @Column(name = "solicitado_em")
    private LocalDateTime solicitadoEm;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "estornado_em")
    private LocalDateTime estornadoEm;

    @Column(name = "cancelado_por_usuario_id")
    private UUID canceladoPorUsuarioId;

    @Column(name = "estornado_por_usuario_id")
    private UUID estornadoPorUsuarioId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    private void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
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

    public boolean compoeSaldoEmAberto() {
        return status == StatusPagamentoPedido.APROVADO
                || status == StatusPagamentoPedido.PENDENTE
                || status == StatusPagamentoPedido.PENDENTE_NA_POINT
                || status == StatusPagamentoPedido.EM_TERMINAL
                || status == StatusPagamentoPedido.PENDENTE_CONCILIACAO;
    }
}
