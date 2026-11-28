package valhalla.core.stock.app.modules.caixa.pedido.repository;

import java.util.UUID;

public interface SequenciaDocumentoRepositoryCustom {

    Long proximoNumero(UUID estabelecimentoId, String tipo);
}
