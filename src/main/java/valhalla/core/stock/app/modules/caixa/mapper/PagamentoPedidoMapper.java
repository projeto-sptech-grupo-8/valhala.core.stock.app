package valhalla.core.stock.app.modules.caixa.mapper;

import valhalla.core.stock.app.modules.caixa.dto.PagamentoPedidoResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;

public final class PagamentoPedidoMapper {

    private PagamentoPedidoMapper() {
    }

    public static PagamentoPedidoResponseDto paraResposta(PagamentoPedidoEntity pagamento) {
        return new PagamentoPedidoResponseDto(
                pagamento.getId(),
                pagamento.getPedidoId(),
                pagamento.getSessaoCaixaId(),
                pagamento.getForma(),
                pagamento.getStatus(),
                pagamento.getProvedor(),
                pagamento.getValor(),
                pagamento.getValorCobrado(),
                pagamento.getValorRecebido(),
                pagamento.getValorTroco(),
                pagamento.getValorEstornado(),
                pagamento.getParcelas(),
                pagamento.getPoliticaJuros(),
                pagamento.getTaxaPercentualAplicada(),
                pagamento.getTaxaFixaAplicada(),
                pagamento.getValorTaxaEstimada(),
                pagamento.getValorLiquidoEstimado(),
                pagamento.getValorJuros(),
                pagamento.getChaveIdempotencia(),
                pagamento.getComprovanteReferencia(),
                pagamento.getMotivoCancelamento(),
                pagamento.getMotivoEstorno(),
                pagamento.getSolicitadoEm(),
                pagamento.getPagoEm(),
                pagamento.getCanceladoEm(),
                pagamento.getEstornadoEm(),
                pagamento.getCriadoEm()
        );
    }
}
