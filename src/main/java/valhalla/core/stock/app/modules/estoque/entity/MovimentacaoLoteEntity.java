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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Quanto de cada lote uma movimentação criou (entrada) ou consumiu (saída). */
@Entity
@Table(name = "movimentacao_estoque_lote")
@Getter
@Setter
@NoArgsConstructor
public class MovimentacaoLoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimentacao_id", nullable = false)
    private MovimentacaoEstoqueEntity movimentacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteEstoqueEntity lote;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;
}
