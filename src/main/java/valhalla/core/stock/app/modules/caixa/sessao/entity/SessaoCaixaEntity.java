package valhalla.core.stock.app.modules.caixa.sessao.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.StatusSessaoCaixa;

@Entity
@Table(name = "sessao_caixa")
@Getter
@Setter
@NoArgsConstructor
public class SessaoCaixaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @ManyToOne
    @JoinColumn(name = "caixa_id", nullable = false)
    private CaixaEntity caixa;

    @Column(name = "operador_usuario_id", nullable = false)
    private UUID operadorUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusSessaoCaixa status = StatusSessaoCaixa.ABERTA;

    @Column(name = "fundo_troco", nullable = false, precision = 12, scale = 2)
    private BigDecimal fundoTroco = BigDecimal.ZERO;

    @Column(name = "dinheiro_esperado", precision = 12, scale = 2)
    private BigDecimal dinheiroEsperado;

    @Column(name = "dinheiro_contado", precision = 12, scale = 2)
    private BigDecimal dinheiroContado;

    @Column(name = "diferenca", precision = 12, scale = 2)
    private BigDecimal diferenca;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resumo_fechamento")
    private JsonNode resumoFechamento;

    @Column(name = "observacao_abertura", columnDefinition = "TEXT")
    private String observacaoAbertura;

    @Column(name = "observacao_fechamento", columnDefinition = "TEXT")
    private String observacaoFechamento;

    @Column(name = "justificativa_divergencia", columnDefinition = "TEXT")
    private String justificativaDivergencia;

    @Column(name = "aberta_em", nullable = false)
    private LocalDateTime abertaEm;

    @Column(name = "fechada_em")
    private LocalDateTime fechadaEm;

    @Column(name = "fechada_por_usuario_id")
    private UUID fechadaPorUsuarioId;

    @Column(name = "divergencia_resolvida_por_usuario_id")
    private UUID divergenciaResolvidaPorUsuarioId;

    @Column(name = "divergencia_resolvida_em")
    private LocalDateTime divergenciaResolvidaEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (abertaEm == null) {
            abertaEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
        if (status == null) {
            status = StatusSessaoCaixa.ABERTA;
        }
        if (fundoTroco == null) {
            fundoTroco = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public boolean isAberta() {
        return status == StatusSessaoCaixa.ABERTA;
    }

    public boolean isEmFechamento() {
        return status == StatusSessaoCaixa.EM_FECHAMENTO;
    }

    public boolean possuiDivergenciaPendente() {
        return isEmFechamento()
                && diferenca != null
                && diferenca.compareTo(BigDecimal.ZERO) != 0
                && divergenciaResolvidaEm == null;
    }
}
