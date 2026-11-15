package valhalla.core.stock.app.modules.estoque.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.estoque.entity.SituacaoValidadeLote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Saldo de um lote. Quantidades na unidade-base do estoque (ML para produto fracionado).")
public record LoteResponseDto(
        Long id,
        UUID produtoId,
        String produtoNome,
        String produtoSku,
        String unidadeEstoque,
        String codigo,
        LocalDate validade,
        @Schema(description = "Dias até a validade (negativo: vencido há N dias). Nulo quando o lote não tem validade.")
        Long diasParaVencer,
        SituacaoValidadeLote situacao,
        BigDecimal quantidadeAtual,
        @Schema(description = "Custo por unidade-base: preço de custo ÷ volume da embalagem para fracionado.")
        BigDecimal custoUnitario,
        @Schema(description = "quantidadeAtual × custoUnitario.")
        BigDecimal valorCusto,
        LocalDateTime entradaEm
) {
}
