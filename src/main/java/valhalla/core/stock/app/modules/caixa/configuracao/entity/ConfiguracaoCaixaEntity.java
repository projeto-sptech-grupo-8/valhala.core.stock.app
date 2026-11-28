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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

@Entity
@Table(
        name = "configuracao_caixa",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_configuracao_caixa_estabelecimento",
                columnNames = "estabelecimento_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoCaixaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(name = "point_habilitado", nullable = false)
    private boolean pointHabilitado;

    @Column(name = "dinheiro_habilitado", nullable = false)
    private boolean dinheiroHabilitado = true;

    @Column(name = "debito_habilitado", nullable = false)
    private boolean debitoHabilitado = true;

    @Column(name = "credito_habilitado", nullable = false)
    private boolean creditoHabilitado = true;

    @Column(name = "pix_habilitado", nullable = false)
    private boolean pixHabilitado = true;

    @Column(name = "parcelamento_habilitado", nullable = false)
    private boolean parcelamentoHabilitado;

    @Column(name = "parcelas_minimas", nullable = false)
    private int parcelasMinimas = 1;

    @Column(name = "parcelas_maximas", nullable = false)
    private int parcelasMaximas = 5;

    @Column(name = "valor_minimo_parcela", precision = 12, scale = 2)
    private BigDecimal valorMinimoParcela;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_juros", nullable = false, length = 30)
    private PoliticaJurosCaixa politicaJuros = PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA;

    @Column(name = "validade_cobranca_segundos", nullable = false)
    private int validadeCobrancaSegundos = 300;

    @Column(name = "limite_desconto_percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal limiteDescontoPercentual = BigDecimal.ZERO;

    @Column(name = "limite_desconto_valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal limiteDescontoValor = BigDecimal.ZERO;

    @Column(name = "imprimir_comprovante", nullable = false)
    private boolean imprimirComprovante = true;

    @Column(name = "permitir_pagamento_manual", nullable = false)
    private boolean permitirPagamentoManual;

    @Column(name = "atualizado_por_usuario_id")
    private UUID atualizadoPorUsuarioId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = FusoHorarioOperacao.agora();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = FusoHorarioOperacao.agora();
    }
}
