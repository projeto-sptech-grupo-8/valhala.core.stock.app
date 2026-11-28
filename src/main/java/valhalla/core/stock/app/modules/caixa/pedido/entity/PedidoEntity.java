package valhalla.core.stock.app.modules.caixa.pedido.entity;

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
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.TipoDescontoPedido;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

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

    @Column(name = "orcamento_id")
    private UUID orcamentoId;

    @Column(name = "numero", nullable = false)
    private Long numero;

    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo = "VENDA_PDV";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusPedidoCaixa status = StatusPedidoCaixa.EM_ATENDIMENTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 30)
    private CanalPedidoCaixa canal = CanalPedidoCaixa.BALCAO;

    @Column(name = "identificador_atendimento", length = 60)
    private String identificadorAtendimento;

    @Column(name = "sessao_caixa_id")
    private UUID sessaoCaixaId;

    @Column(name = "nome_cliente", length = 150)
    private String nomeCliente;

    @Column(name = "documento_cliente", length = 20)
    private String documentoCliente;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "desconto", nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_desconto", length = 20)
    private TipoDescontoPedido tipoDesconto;

    @Column(name = "valor_desconto_informado", precision = 12, scale = 2)
    private BigDecimal valorDescontoInformado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_desconto", nullable = false, length = 30)
    private StatusDescontoPedido statusDesconto = StatusDescontoPedido.NENHUM;

    @Column(name = "desconto_solicitado", precision = 12, scale = 2)
    private BigDecimal descontoSolicitado;

    @Column(name = "motivo_desconto", columnDefinition = "TEXT")
    private String motivoDesconto;

    @Column(name = "desconto_decidido_por_usuario_id")
    private UUID descontoDecididoPorUsuarioId;

    @Column(name = "desconto_decidido_em")
    private LocalDateTime descontoDecididoEm;

    @Column(name = "observacao_decisao_desconto", columnDefinition = "TEXT")
    private String observacaoDecisaoDesconto;

    @Column(name = "observacao", columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    @Column(name = "cancelado_por_usuario_id")
    private UUID canceladoPorUsuarioId;

    @Column(name = "motivo_estorno", columnDefinition = "TEXT")
    private String motivoEstorno;

    @Column(name = "estornado_por_usuario_id")
    private UUID estornadoPorUsuarioId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "concluido_em")
    private LocalDateTime concluidoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @Column(name = "estornado_em")
    private LocalDateTime estornadoEm;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = FusoHorarioOperacao.agora();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
        if (tipo == null || tipo.isBlank()) {
            tipo = "VENDA_PDV";
        }
        if (status == null) {
            status = StatusPedidoCaixa.EM_ATENDIMENTO;
        }
        if (canal == null) {
            canal = CanalPedidoCaixa.BALCAO;
        }
        if (statusDesconto == null) {
            statusDesconto = StatusDescontoPedido.NENHUM;
        }
        if (subtotal == null) {
            subtotal = BigDecimal.ZERO;
        }
        if (desconto == null) {
            desconto = BigDecimal.ZERO;
        }
        if (total == null) {
            total = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = FusoHorarioOperacao.agora();
    }
}
