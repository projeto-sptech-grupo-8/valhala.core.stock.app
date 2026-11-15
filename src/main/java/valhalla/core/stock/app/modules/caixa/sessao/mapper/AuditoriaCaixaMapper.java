package valhalla.core.stock.app.modules.caixa.sessao.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import tools.jackson.databind.util.RawValue;
import valhalla.core.stock.app.modules.caixa.sessao.dto.AuditoriaCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.entity.AuditoriaCaixaEntity;

public final class AuditoriaCaixaMapper {

    private AuditoriaCaixaMapper() {
    }

    public static AuditoriaCaixaResponseDto paraResposta(AuditoriaCaixaEntity auditoria) {
        return new AuditoriaCaixaResponseDto(
                auditoria.getId(),
                auditoria.getTipoEvento(),
                auditoria.getEntidade(),
                auditoria.getEntidadeId(),
                auditoria.getSessaoCaixaId(),
                auditoria.getUsuarioId(),
                auditoria.getUsuarioNome(),
                auditoria.getDescricao(),
                auditoria.getValor(),
                auditoria.getMotivo(),
                auditoria.getAutorizadoPorUsuarioId(),
                auditoria.getAutorizadoPorNome(),
                auditoria.getPedidoId(),
                auditoria.getPedidoIdentificador(),
                auditoria.getPagamentoPedidoId(),
                raw(auditoria.getDadosAnteriores()),
                raw(auditoria.getDadosNovos()),
                auditoria.getOcorridoEm()
        );
    }

    private static RawValue raw(JsonNode json) {
        return json == null ? null : new RawValue(json.toString());
    }
}
