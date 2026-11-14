package valhalla.core.stock.app.modules.caixa.point.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;
import valhalla.core.stock.app.modules.caixa.point.enums.StatusProcessamentoWebhook;

@Entity
@Table(name = "evento_webhook_pagamento")
@Getter
@Setter
@NoArgsConstructor
public class EventoWebhookPagamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provedor", nullable = false, length = 30)
    private String provedor = "MERCADO_PAGO";

    @Column(name = "evento_externo_id", nullable = false, length = 100)
    private String eventoExternoId;

    @Column(name = "tipo_evento", length = 40)
    private String tipoEvento;

    @Column(name = "acao", length = 60)
    private String acao;

    @Column(name = "recurso_tipo", length = 30)
    private String recursoTipo;

    @Column(name = "recurso_externo_id", length = 100)
    private String recursoExternoId;

    @Column(name = "pagamento_pedido_id")
    private UUID pagamentoPedidoId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "assinatura", length = 255)
    private String assinatura;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_processamento", nullable = false, length = 20)
    private StatusProcessamentoWebhook statusProcessamento = StatusProcessamentoWebhook.RECEBIDO;

    @Column(name = "tentativas", nullable = false)
    private Integer tentativas = 0;

    @Column(name = "recebido_em", nullable = false)
    private LocalDateTime recebidoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "processado_em")
    private LocalDateTime processadoEm;

    @Column(name = "erro", columnDefinition = "TEXT")
    private String erro;

    @PrePersist
    protected void onCreate() {
        LocalDateTime agora = LocalDateTime.now();
        if (this.provedor == null || this.provedor.isBlank()) {
            this.provedor = "MERCADO_PAGO";
        }
        if (this.statusProcessamento == null) {
            this.statusProcessamento = StatusProcessamentoWebhook.RECEBIDO;
        }
        if (this.tentativas == null) {
            this.tentativas = 0;
        }
        if (this.recebidoEm == null) {
            this.recebidoEm = agora;
        }
        this.atualizadoEm = agora;
    }

    @PreUpdate
    protected void onUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
