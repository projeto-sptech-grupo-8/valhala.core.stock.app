package valhalla.core.stock.app.modules.caixa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import valhalla.core.stock.app.modules.caixa.entity.StatusSessaoCaixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SessaoCaixaDetalheResponseDto(
        @Schema(description = "Identificador da sessão") UUID id,
        @Schema(description = "Caixa da sessão") UUID caixaId,
        @Schema(description = "Nome do caixa da sessão") String caixaNome,
        @Schema(description = "Operador que abriu a sessão") UUID operadorUsuarioId,
        @Schema(description = "Nome do operador") String operadorNome,
        @Schema(description = "Situação da sessão") StatusSessaoCaixa status,
        @Schema(description = "Fundo de troco da abertura") BigDecimal fundoTroco,
        @Schema(description = "Dinheiro esperado; preenchido no fechamento", nullable = true)
        BigDecimal dinheiroEsperado,
        @Schema(description = "Dinheiro contado; preenchido no fechamento", nullable = true)
        BigDecimal dinheiroContado,
        @Schema(description = "Diferença entre contado e esperado", nullable = true) BigDecimal diferenca,
        @Schema(description = "Existe divergência aguardando aprovação") boolean possuiDivergenciaPendente,
        @Schema(description = "Observação da abertura", nullable = true) String observacaoAbertura,
        @Schema(description = "Observação do fechamento", nullable = true) String observacaoFechamento,
        @Schema(description = "Justificativa da divergência", nullable = true) String justificativaDivergencia,
        @Schema(description = "Data e hora de abertura") LocalDateTime abertaEm,
        @Schema(description = "Data e hora do encerramento do turno", nullable = true) LocalDateTime fechadaEm,
        @Schema(description = "Usuário que encerrou o turno", nullable = true) UUID fechadaPorUsuarioId,
        @Schema(description = "Nome de quem encerrou o turno", nullable = true) String fechadaPorNome,
        @Schema(description = "Usuário que aprovou a divergência", nullable = true)
        UUID divergenciaResolvidaPorUsuarioId,
        @Schema(description = "Nome de quem aprovou a divergência", nullable = true)
        String divergenciaResolvidaPorNome,
        @Schema(description = "Data e hora da aprovação da divergência", nullable = true)
        LocalDateTime divergenciaResolvidaEm,
        @Schema(description = "Última atualização") LocalDateTime atualizadoEm,
        @Schema(description = "Resumo calculado em tempo real enquanto a sessão está aberta")
        ResumoFechamentoDto resumoAtual,
        @Schema(description = "Resumo congelado no fechamento", nullable = true)
        ResumoFechamentoDto resumoFechamento,
        @Schema(description = "Sangrias e reforços da sessão") List<MovimentacaoCaixaResponseDto> movimentacoes
) {
}
