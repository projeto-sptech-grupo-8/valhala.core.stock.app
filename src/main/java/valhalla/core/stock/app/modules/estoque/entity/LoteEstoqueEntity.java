package valhalla.core.stock.app.modules.estoque.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Saldo de um lote dentro do estoque de um produto. A soma dos lotes é igual a
 * {@link EstoqueEntity#getQuantidadeAtual()} e usa a mesma unidade-base (ML para fracionado).
 */
@Entity
@Table(name = "lote_estoque", uniqueConstraints = @UniqueConstraint(
        name = "uq_lote_estoque_codigo", columnNames = {"estoque_id", "codigo"}))
@Getter
@Setter
@NoArgsConstructor
public class LoteEstoqueEntity {

    public static final String SEM_LOTE = "SEM-LOTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estoque_id", nullable = false)
    private EstoqueEntity estoque;

    @Column(nullable = false, length = 100)
    private String codigo;

    private LocalDate validade;

    @Column(name = "quantidade_atual", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeAtual;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    public boolean vencidoEm(LocalDate data) {
        return validade != null && validade.isBefore(data);
    }
}
