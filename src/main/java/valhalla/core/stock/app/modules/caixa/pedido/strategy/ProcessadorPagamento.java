package valhalla.core.stock.app.modules.caixa.pedido.strategy;

import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;

public interface ProcessadorPagamento {

    boolean suporta(FormaPagamentoCaixa forma, ConfiguracaoCaixaEntity configuracao);

    PagamentoPedidoEntity processar(
            PedidoEntity pedido,
            SessaoCaixaEntity sessaoCaixa,
            ConfiguracaoCaixaEntity configuracao,
            RegistrarPagamentoPedidoRequestDto dto
    );
}
