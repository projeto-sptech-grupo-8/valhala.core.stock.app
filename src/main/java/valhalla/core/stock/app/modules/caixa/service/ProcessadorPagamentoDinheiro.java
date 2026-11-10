package valhalla.core.stock.app.modules.caixa.service;

import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusPagamentoPedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class ProcessadorPagamentoDinheiro implements ProcessadorPagamento {

    @Override
    public boolean suporta(FormaPagamentoCaixa forma, ConfiguracaoCaixaEntity configuracao) {
        return forma == FormaPagamentoCaixa.DINHEIRO;
    }

    @Override
    public PagamentoPedidoEntity processar(
            PedidoEntity pedido,
            SessaoCaixaEntity sessaoCaixa,
            ConfiguracaoCaixaEntity configuracao,
            RegistrarPagamentoPedidoRequestDto dto
    ) {
        if (!configuracao.isDinheiroHabilitado()) {
            throw new IllegalArgumentException("O pagamento em dinheiro está desabilitado na configuração do caixa");
        }
        int parcelas = dto.parcelas() == null ? 1 : dto.parcelas();
        if (parcelas != 1) {
            throw new IllegalArgumentException("Pagamento em dinheiro não permite parcelamento");
        }
        if (dto.valorRecebido() == null) {
            throw new IllegalArgumentException("Informe o valor recebido para pagamento em dinheiro");
        }

        BigDecimal valor = dto.valor().setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorRecebido = dto.valorRecebido().setScale(2, RoundingMode.HALF_UP);
        if (valorRecebido.compareTo(valor) < 0) {
            throw new IllegalArgumentException("O valor recebido em dinheiro não pode ser menor que o valor da parcela");
        }

        BigDecimal troco = valorRecebido.subtract(valor).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime agora = LocalDateTime.now();

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setPedidoId(pedido.getId());
        pagamento.setSessaoCaixaId(sessaoCaixa.getId());
        pagamento.setForma(FormaPagamentoCaixa.DINHEIRO);
        pagamento.setStatus(StatusPagamentoPedido.APROVADO);
        pagamento.setProvedor("INTERNO");
        pagamento.setValor(valor);
        pagamento.setValorCobrado(null);
        pagamento.setValorRecebido(valorRecebido);
        pagamento.setValorTroco(troco);
        pagamento.setValorEstornado(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        pagamento.setParcelas(1);
        pagamento.setChaveIdempotencia(normalizar(dto.chaveIdempotencia()));
        pagamento.setComprovanteReferencia(normalizar(dto.comprovanteReferencia()));
        pagamento.setSolicitadoEm(agora);
        pagamento.setPagoEm(agora);
        return pagamento;
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
