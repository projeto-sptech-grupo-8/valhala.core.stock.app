package valhalla.core.stock.app.modules.financeiro.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.financeiro.entity.CategoriaConta;
import valhalla.core.stock.app.modules.financeiro.entity.CentroCusto;
import valhalla.core.stock.app.modules.financeiro.entity.StatusConta;
import valhalla.core.stock.app.modules.financeiro.entity.TipoConta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RespostaContaDto(
        UUID id,
        TipoConta tipo,
        String descricao,
        BigDecimal valor,
        LocalDate vencimento,
        CentroCusto centroCusto,
        CategoriaConta categoria,
        StatusConta status,
        LocalDate pagoEm,
        BigDecimal valorPago,
        String observacao,
        @Schema(description = "Dias até o vencimento (negativo: vencida). Nulo quando a conta não está em aberto.")
        Integer diasParaVencer,
        LocalDateTime criadoEm
) {
}
