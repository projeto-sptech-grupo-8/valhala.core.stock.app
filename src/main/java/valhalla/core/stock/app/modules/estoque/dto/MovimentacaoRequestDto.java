package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Movimentação direta de um produto padrão. A quantidade usa a unidade-base do estoque.")
public record MovimentacaoRequestDto(
        @NotNull(message = "O produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "O tipo de movimentação é obrigatório")
        @Schema(allowableValues = {"ENTRADA", "SAIDA", "AJUSTE_POSITIVO", "AJUSTE_NEGATIVO", "PERDA"})
        TipoMovimentacaoEstoque tipo,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        BigDecimal quantidade,

        String motivo,

        @Size(max = 100, message = "O lote deve ter no máximo 100 caracteres")
        @Schema(description = "Entradas: código do lote que recebe a quantidade (omitido: SEM-LOTE, ou VAL-<validade> se a validade for informada). "
                + "Saídas: código do lote a consumir (omitido: FEFO).", example = "L2611A")
        String lote,

        @Size(max = 50, message = "O número da nota fiscal deve ter no máximo 50 caracteres")
        String numeroNotaFiscal,

        @Schema(description = "Validade do lote, em yyyy-MM-dd. Só em ENTRADA e AJUSTE_POSITIVO.", example = "2027-03-31")
        LocalDate validade,

        @Positive(message = "O lote de origem deve ser um identificador válido")
        @Schema(description = "Lote específico a consumir em SAIDA, AJUSTE_NEGATIVO ou PERDA (ex.: baixa de lote vencido). Omitido: FEFO.", example = "42")
        Long loteId
) {
    public MovimentacaoRequestDto(
            UUID produtoId,
            TipoMovimentacaoEstoque tipo,
            BigDecimal quantidade,
            String motivo,
            String lote,
            String numeroNotaFiscal
    ) {
        this(produtoId, tipo, quantidade, motivo, lote, numeroNotaFiscal, null, null);
    }
}
