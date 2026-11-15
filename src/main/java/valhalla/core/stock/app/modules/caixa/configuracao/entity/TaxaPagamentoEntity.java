package valhalla.core.stock.app.modules.caixa.configuracao.entity;

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
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;

@Entity
@Table(name = "taxa_pagamento")
@Getter
@Setter
@NoArgsConstructor
public class TaxaPagamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma", nullable = false, length = 30)
    private FormaPagamentoCaixa forma;

    @Column(name = "parcelas", nullable = false)
    private int parcelas = 1;

    @Column(name = "taxa_percentual", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxaPercentual = BigDecimal.ZERO;

    @Column(name = "taxa_fixa", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxaFixa = BigDecimal.ZERO;

    @Column(name = "repassar_ao_cliente")
    private Boolean repassarAoCliente;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

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
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
