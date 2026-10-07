package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;

import java.math.BigDecimal;
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
        String lote,

        @Size(max = 50, message = "O número da nota fiscal deve ter no máximo 50 caracteres")
        String numeroNotaFiscal
) {
}
