package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;

import java.math.BigDecimal;

@Schema(description = "Dados para criar um produto. O SKU é gerado pelo servidor.")
public record ProdutoRequestDto(
        @NotBlank(message = "O nome do produto é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        @Schema(example = "Vodka Absolut 1L")
        String nome,

        @NotNull(message = "A categoria do produto é obrigatória")
        @Schema(example = "1")
        Integer categoriaId,

        @NotBlank(message = "A unidade de medida é obrigatória")
        @Size(max = 10, message = "A unidade de medida deve ter no máximo 10 caracteres")
        @Schema(example = "garrafa")
        String unidadeMedida,

        @NotNull(message = "O preço de venda é obrigatório")
        @Positive(message = "O preço de venda deve ser maior que zero")
        @Schema(example = "129.90")
        BigDecimal precoVenda,

        @DecimalMin(value = "0.0", message = "O preço de custo não pode ser negativo")
        @Schema(description = "Quando omitido, assume zero", example = "89.90")
        BigDecimal precoCusto,

        @Schema(description = "PADRAO quando omitido. DRINK terá a receita cadastrada em etapa posterior.", example = "PADRAO")
        ProdutoTipo tipo,

        @Size(max = 45, message = "O código de barras deve ter no máximo 45 caracteres")
        @Schema(example = "7891234567890")
        String codigoBarras,

        @Schema(example = "Vodka importada de 1 litro")
        String descricao,

        @Schema(example = "false")
        Boolean fracionado,

        @Positive(message = "O volume da embalagem deve ser maior que zero")
        @Schema(description = "Volume total de uma unidade, em mililitros", example = "1000")
        BigDecimal volumeEmbalagemMl,

        @PositiveOrZero(message = "O estoque mínimo não pode ser negativo")
        @Schema(description = "Opcional. Usado no cálculo dos alertas de estoque.", example = "6")
        BigDecimal estoqueMinimo,

        @PositiveOrZero(message = "A quantidade inicial não pode ser negativa")
        @Schema(description = "Aplicável a produtos PADRAO. Quando omitida, inicia em zero.", example = "12")
        BigDecimal quantidadeInicial
) {
}
