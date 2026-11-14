package valhalla.core.stock.app.modules.caixa.configuracao.mapper;

import valhalla.core.stock.app.modules.caixa.configuracao.dto.TerminalCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TerminalCaixaEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TerminalCaixaMapper {

    private TerminalCaixaMapper() {
    }

    public static TerminalCaixaResponseDto paraResposta(TerminalCaixaEntity terminal) {
        return new TerminalCaixaResponseDto(
                terminal.getId(),
                terminal.getCaixa().getId(),
                terminal.getTerminalId(),
                terminal.getNome(),
                terminal.isAtivo(),
                terminal.getCriadoEm(),
                terminal.getAtualizadoEm()
        );
    }

    public static Map<String, Object> snapshot(TerminalCaixaEntity terminal) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("caixaId", terminal.getCaixa().getId().toString());
        dados.put("terminalId", terminal.getTerminalId());
        dados.put("nome", terminal.getNome());
        dados.put("ativo", terminal.isAtivo());
        return dados;
    }
}
