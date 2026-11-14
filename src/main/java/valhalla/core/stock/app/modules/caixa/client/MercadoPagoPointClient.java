package valhalla.core.stock.app.modules.caixa.client;

import valhalla.core.stock.app.modules.caixa.client.dto.CriarOrderPointCommand;
import valhalla.core.stock.app.modules.caixa.client.dto.ModoOperacaoTerminalPoint;
import valhalla.core.stock.app.modules.caixa.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.client.dto.TerminalMercadoPagoDto;

import java.util.List;

public interface MercadoPagoPointClient {

    OrderPointResultado criarOrderPoint(CriarOrderPointCommand command);

    OrderPointResultado consultarOrder(String orderId);

    OrderPointResultado cancelarOrder(String orderId, String chaveIdempotencia);

    List<TerminalMercadoPagoDto> listarTerminais();

    TerminalMercadoPagoDto atualizarModoOperacaoTerminal(
            String terminalId,
            ModoOperacaoTerminalPoint modoOperacao
    );
}
