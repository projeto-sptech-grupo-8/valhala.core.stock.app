package valhalla.core.stock.app.modules.financeiro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.financeiro.entity.CategoriaConta;
import valhalla.core.stock.app.modules.financeiro.entity.CentroCusto;
import valhalla.core.stock.app.modules.financeiro.entity.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Conta a pagar ou a receber. Na edição, o tipo não pode mudar.")
public record RequisicaoContaDto(
        @NotNull(message = "O tipo é obrigatório")
        @Schema(example = "PAGAR")
        TipoConta tipo,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 150, message = "A descrição deve ter no máximo 150 caracteres")
        @Schema(description = "Fornecedor, cliente ou natureza da conta.", example = "Aluguel · Adega")
        String descricao,

        @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 2 casas decimais")
        @Schema(example = "3800.00")
        BigDecimal valor,

        @NotNull(message = "O vencimento é obrigatório")
        @Schema(example = "2026-12-05")
        LocalDate vencimento,

        @NotNull(message = "O centro de custo é obrigatório")
        @Schema(example = "OPERACIONAL")
        CentroCusto centroCusto,

        @Schema(description = "Obrigatória em contas a pagar e proibida em contas a receber. MERCADORIA (compra de "
                + "estoque) entra só no fluxo de caixa; DESPESA_FIXA e DESPESA_VARIAVEL entram no resultado.",
                example = "DESPESA_FIXA")
        CategoriaConta categoria,

        @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres")
        String observacao
) {
}
