package valhalla.core.stock.app.modules.financeiro.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Conta a pagar ou a receber. A baixa registra a data e o valor efetivamente pagos/recebidos. */
@Entity
@Table(name = "conta_financeira")
@Getter
@Setter
@NoArgsConstructor
public class ContaFinanceiraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoConta tipo;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "centro_custo", nullable = false, length = 20)
    private CentroCusto centroCusto;

    /** Só em contas a pagar: define se a conta entra no DRE e como. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CategoriaConta categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusConta status;

    @Column(name = "pago_em")
    private LocalDate pagoEm;

    @Column(name = "valor_pago", precision = 12, scale = 2)
    private BigDecimal valorPago;

    private String observacao;

    @Column(name = "criado_por_usuario_id", nullable = false)
    private UUID criadoPorUsuarioId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
