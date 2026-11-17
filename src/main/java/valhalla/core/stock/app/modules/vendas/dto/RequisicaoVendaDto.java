package valhalla.core.stock.app.modules.vendas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.vendas.entity.CanalVenda;
import valhalla.core.stock.app.modules.vendas.entity.FormaPagamento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Venda finalizada no caixa. Os preços vêm do cadastro do produto; o estoque é baixado na mesma transação.")
public record RequisicaoVendaDto(

        @NotNull(message = "O canal é obrigatório")
        @Schema(example = "BALCAO")
        CanalVenda canal,

        @NotEmpty(message = "Informe ao menos um item")
        @Size(max = 200, message = "A venda pode ter no máximo 200 itens")
        List<@Valid @NotNull Item> itens,

        @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O desconto deve ter no máximo 2 casas decimais")
        @Schema(description = "Desconto sobre o total da venda. Omitido: 0.", example = "5.00")
        BigDecimal desconto,

        @NotEmpty(message = "Informe ao menos um pagamento")
        @Size(max = 10, message = "A venda pode ter no máximo 10 pagamentos")
        @Schema(description = "Valores recebidos. A soma deve cobrir o total; a sobra é troco e exige pagamento em dinheiro.")
        List<@Valid @NotNull Pagamento> pagamentos,

        @Size(max = 150, message = "O nome do cliente deve ter no máximo 150 caracteres")
        String nomeCliente,

        @Size(max = 20, message = "O documento do cliente deve ter no máximo 20 caracteres")
        String documentoCliente,

        @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres")
        @Schema(description = "Identificação livre da venda, como a mesa ou a comanda.", example = "Mesa 4")
        String observacao
) {

    public record Item(
            @NotNull(message = "O produto é obrigatório")
            UUID produtoId,

            @NotNull(message = "A quantidade é obrigatória")
            @Positive(message = "A quantidade deve ser maior que zero")
            @Digits(integer = 9, fraction = 3, message = "A quantidade deve ter no máximo 3 casas decimais")
            @Schema(description = "Unidades vendidas (garrafas, copos ou drinks).", example = "2")
            BigDecimal quantidade
    ) {
    }

    public record Pagamento(
            @NotNull(message = "A forma de pagamento é obrigatória")
            @Schema(example = "DINHEIRO")
            FormaPagamento forma,

            @NotNull(message = "O valor do pagamento é obrigatório")
            @Positive(message = "O valor do pagamento deve ser maior que zero")
            @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 2 casas decimais")
            @Schema(description = "Valor recebido nesta forma (em dinheiro, inclui o troco).", example = "50.00")
            BigDecimal valor
    ) {
    }
}
