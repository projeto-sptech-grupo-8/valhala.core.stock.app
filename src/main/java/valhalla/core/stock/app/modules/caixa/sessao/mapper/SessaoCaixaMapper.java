package valhalla.core.stock.app.modules.caixa.sessao.mapper;

import valhalla.core.stock.app.modules.caixa.sessao.dto.MovimentacaoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.ResumoFechamentoDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.SessaoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.SessaoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SessaoCaixaMapper {

    private SessaoCaixaMapper() {
    }

    public static SessaoCaixaResumoResponseDto paraResumo(
            SessaoCaixaEntity sessao,
            Map<UUID, String> nomesUsuarios
    ) {
        return new SessaoCaixaResumoResponseDto(
                sessao.getId(),
                sessao.getCaixa().getId(),
                sessao.getCaixa().getNome(),
                sessao.getOperadorUsuarioId(),
                nome(nomesUsuarios, sessao.getOperadorUsuarioId()),
                sessao.getStatus(),
                sessao.getFundoTroco(),
                sessao.getDinheiroEsperado(),
                sessao.getDinheiroContado(),
                sessao.getDiferenca(),
                sessao.possuiDivergenciaPendente(),
                sessao.getAbertaEm(),
                sessao.getFechadaEm(),
                sessao.getFechadaPorUsuarioId(),
                nome(nomesUsuarios, sessao.getFechadaPorUsuarioId()),
                sessao.getDivergenciaResolvidaPorUsuarioId(),
                nome(nomesUsuarios, sessao.getDivergenciaResolvidaPorUsuarioId()),
                sessao.getDivergenciaResolvidaEm(),
                sessao.getAtualizadoEm()
        );
    }

    public static SessaoCaixaDetalheResponseDto paraDetalhe(
            SessaoCaixaEntity sessao,
            Map<UUID, String> nomesUsuarios,
            ResumoFechamentoDto resumoAtual,
            ResumoFechamentoDto resumoFechamento,
            List<MovimentacaoCaixaResponseDto> movimentacoes
    ) {
        return new SessaoCaixaDetalheResponseDto(
                sessao.getId(),
                sessao.getCaixa().getId(),
                sessao.getCaixa().getNome(),
                sessao.getOperadorUsuarioId(),
                nome(nomesUsuarios, sessao.getOperadorUsuarioId()),
                sessao.getStatus(),
                sessao.getFundoTroco(),
                sessao.getDinheiroEsperado(),
                sessao.getDinheiroContado(),
                sessao.getDiferenca(),
                sessao.possuiDivergenciaPendente(),
                sessao.getObservacaoAbertura(),
                sessao.getObservacaoFechamento(),
                sessao.getJustificativaDivergencia(),
                sessao.getAbertaEm(),
                sessao.getFechadaEm(),
                sessao.getFechadaPorUsuarioId(),
                nome(nomesUsuarios, sessao.getFechadaPorUsuarioId()),
                sessao.getDivergenciaResolvidaPorUsuarioId(),
                nome(nomesUsuarios, sessao.getDivergenciaResolvidaPorUsuarioId()),
                sessao.getDivergenciaResolvidaEm(),
                sessao.getAtualizadoEm(),
                resumoAtual,
                resumoFechamento,
                movimentacoes
        );
    }

    private static String nome(Map<UUID, String> nomesUsuarios, UUID usuarioId) {
        return usuarioId == null ? null : nomesUsuarios.get(usuarioId);
    }
}
