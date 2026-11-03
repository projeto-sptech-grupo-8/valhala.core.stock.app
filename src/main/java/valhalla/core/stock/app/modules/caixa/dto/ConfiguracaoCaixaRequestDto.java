package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.caixa.entity.PoliticaJurosCaixa;

import java.math.BigDecimal;

public record ConfiguracaoCaixaRequestDto(

        @Size(max = 100, message = "O terminal deve ter no máximo 100 caracteres")
        @Schema(description = "Identificador do terminal Point. Obrigatório quando pointHabilitado=true.",
                example = "TERM-001", nullable = true)
        String terminalId,

        @Size(max = 100, message = "O nome do terminal deve ter no máximo 100 caracteres")
        @Schema(description = "Nome amigável do terminal", example = "Caixa principal", nullable = true)
        String terminalNome,

        @NotNull(message = "pointHabilitado é obrigatório")
        @Schema(description = "Habilita cobranças na Point", example = "false")
        Boolean pointHabilitado,

        @NotNull(message = "dinheiroHabilitado é obrigatório")
        @Schema(description = "Aceita pagamentos em dinheiro", example = "true")
        Boolean dinheiroHabilitado,

        @NotNull(message = "debitoHabilitado é obrigatório")
        @Schema(description = "Aceita pagamentos em débito", example = "true")
        Boolean debitoHabilitado,

        @NotNull(message = "creditoHabilitado é obrigatório")
        @Schema(description = "Aceita pagamentos em crédito", example = "true")
        Boolean creditoHabilitado,

        @NotNull(message = "pixHabilitado é obrigatório")
        @Schema(description = "Aceita pagamentos via Pix", example = "true")
        Boolean pixHabilitado,

        @NotNull(message = "parcelamentoHabilitado é obrigatório")
        @Schema(description = "Habilita parcelamento no crédito", example = "false")
        Boolean parcelamentoHabilitado,

        @NotNull(message = "parcelasMinimas é obrigatório")
        @Min(value = 1, message = "parcelasMinimas deve ser no mínimo 1")
        @Schema(description = "Número mínimo de parcelas", example = "1")
        Integer parcelasMinimas,

        @NotNull(message = "parcelasMaximas é obrigatório")
        @Min(value = 1, message = "parcelasMaximas deve ser no mínimo 1")
        @Max(value = 12, message = "parcelasMaximas deve ser no máximo 12")
        @Schema(description = "Número máximo de parcelas", example = "5", maximum = "12")
        Integer parcelasMaximas,

        @DecimalMin(value = "0.01", message = "O valor mínimo da parcela deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor mínimo da parcela deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor mínimo de cada parcela; nulo quando não houver restrição",
                example = "10.00", nullable = true)
        BigDecimal valorMinimoParcela,

        @NotNull(message = "politicaJuros é obrigatória")
        @Schema(description = "Quem absorve os juros do parcelamento", example = "ABSORVIDA_PELA_LOJA")
        PoliticaJurosCaixa politicaJuros,

        @NotNull(message = "validadeCobrancaSegundos é obrigatório")
        @Min(value = 60, message = "A validade da cobrança deve ser de no mínimo 60 segundos")
        @Max(value = 900, message = "A validade da cobrança deve ser de no máximo 900 segundos")
        @Schema(description = "Validade da cobrança na Point, em segundos", example = "300")
        Integer validadeCobrancaSegundos,

        @NotNull(message = "limiteDescontoPercentual é obrigatório")
        @DecimalMin(value = "0.00", message = "O limite percentual de desconto não pode ser negativo")
        @DecimalMax(value = "100.00", message = "O limite percentual de desconto não pode ser maior que 100")
        @Digits(integer = 3, fraction = 2, message = "O limite percentual deve ter no máximo 2 casas decimais")
        @Schema(description = "Percentual de desconto que dispensa autorização", example = "10.00")
        BigDecimal limiteDescontoPercentual,

        @NotNull(message = "limiteDescontoValor é obrigatório")
        @DecimalMin(value = "0.00", message = "O limite de desconto em valor não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O limite de desconto em valor deve ter no máximo 2 casas decimais")
        @Schema(description = "Valor de desconto que dispensa autorização", example = "20.00")
        BigDecimal limiteDescontoValor,

        @NotNull(message = "imprimirComprovante é obrigatório")
        @Schema(description = "Imprime comprovante na maquininha", example = "true")
        Boolean imprimirComprovante,

        @NotNull(message = "permitirPagamentoManual é obrigatório")
        @Schema(description = "Permite registrar pagamento manual sem integração com a Point", example = "false")
        Boolean permitirPagamentoManual
) {
}
