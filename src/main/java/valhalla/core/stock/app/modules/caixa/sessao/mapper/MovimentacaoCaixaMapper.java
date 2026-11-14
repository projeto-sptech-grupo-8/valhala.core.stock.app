package valhalla.core.stock.app.modules.caixa.sessao.mapper;

import valhalla.core.stock.app.modules.caixa.sessao.dto.MovimentacaoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.entity.MovimentacaoCaixaEntity;

public final class MovimentacaoCaixaMapper {

    private MovimentacaoCaixaMapper() {
    }

    public static MovimentacaoCaixaResponseDto paraResposta(
            MovimentacaoCaixaEntity movimentacao,
            String usuarioNome
    ) {
        return new MovimentacaoCaixaResponseDto(
                movimentacao.getId(),
                movimentacao.getTipo(),
                movimentacao.getValor(),
                movimentacao.getMotivo(),
                movimentacao.getUsuarioId(),
                usuarioNome,
                movimentacao.getOcorridoEm()
        );
    }
}
