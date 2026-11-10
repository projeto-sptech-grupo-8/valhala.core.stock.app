package valhalla.core.stock.app.modules.caixa.mapper;

import valhalla.core.stock.app.modules.caixa.dto.CaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.CaixaEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CaixaMapper {

    private CaixaMapper() {
    }

    public static CaixaResponseDto paraResposta(
            CaixaEntity caixa,
            boolean emUso,
            List<TerminalCaixaResponseDto> terminais
    ) {
        return new CaixaResponseDto(
                caixa.getId(),
                caixa.getNome(),
                caixa.isAtivo(),
                emUso,
                terminais,
                caixa.getCriadoEm(),
                caixa.getAtualizadoEm()
        );
    }

    public static Map<String, Object> snapshot(CaixaEntity caixa) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("nome", caixa.getNome());
        dados.put("ativo", caixa.isAtivo());
        return dados;
    }
}
