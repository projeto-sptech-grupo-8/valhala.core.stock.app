package valhalla.core.stock.app.modules.caixa.service;

import valhalla.core.stock.app.modules.caixa.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.SessaoCaixaEntity;

public interface ProcessadorPagamento {

    boolean suporta(FormaPagamentoCaixa forma, ConfiguracaoCaixaEntity configuracao);

    PagamentoPedidoEntity processar(
            PedidoEntity pedido,
            SessaoCaixaEntity sessaoCaixa,
            ConfiguracaoCaixaEntity configuracao,
            RegistrarPagamentoPedidoRequestDto dto
    );
}
