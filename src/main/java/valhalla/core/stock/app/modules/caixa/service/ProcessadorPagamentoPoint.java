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
import java.util.UUID;

@Component
public class ProcessadorPagamentoPoint {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CalculoPagamentoEletronicoHelper calculoHelper;

    public ProcessadorPagamentoPoint(CalculoPagamentoEletronicoHelper calculoHelper) {
        this.calculoHelper = calculoHelper;
    }

    public PagamentoPedidoEntity prepararPagamentoPendente(
            PedidoEntity pedido,
            SessaoCaixaEntity sessaoCaixa,
            ConfiguracaoCaixaEntity configuracao,
            RegistrarPagamentoPedidoRequestDto dto,
            String terminalId,
            String chaveIdempotencia
    ) {
        calculoHelper.validarFormaHabilitada(dto.forma(), configuracao);

        if (dto.valorRecebido() != null) {
            throw new IllegalArgumentException(
                    "O valor recebido só deve ser informado para pagamentos em dinheiro"
            );
        }

        int parcelas = dto.parcelas() == null ? 1 : dto.parcelas();
        BigDecimal valor = dto.valor().setScale(2, RoundingMode.HALF_UP);
        calculoHelper.validarParcelamento(dto.forma(), parcelas, valor, configuracao);

        CalculoPagamentoEletronicoHelper.ResultadoTaxas taxas = calculoHelper.calcularTaxas(
                pedido.getEstabelecimentoId(),
                dto.forma(),
                parcelas,
                valor,
                configuracao
        );

        UUID pagamentoId = UUID.randomUUID();
        String referenciaExterna = gerarReferenciaExterna(pedido.getNumero(), pagamentoId);
        LocalDateTime agora = LocalDateTime.now();

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(pagamentoId);
        pagamento.setPedidoId(pedido.getId());
        pagamento.setSessaoCaixaId(sessaoCaixa.getId());
        pagamento.setTerminalId(terminalId);
        pagamento.setForma(dto.forma());
        pagamento.setMetodoPagamento(mapearMetodoMercadoPago(dto.forma()));
        pagamento.setStatus(StatusPagamentoPedido.PENDENTE);
        pagamento.setProvedor("MERCADO_PAGO");
        pagamento.setReferenciaExterna(referenciaExterna);
        pagamento.setValor(valor);
        pagamento.setValorCobrado(taxas.valorCobrado());
        pagamento.setValorRecebido(null);
        pagamento.setValorTroco(ZERO);
        pagamento.setValorEstornado(ZERO);
        pagamento.setParcelas(parcelas);
        pagamento.setPoliticaJuros(taxas.politicaAplicada());
        pagamento.setTaxaPercentualAplicada(taxas.taxaPercentualAplicada());
        pagamento.setTaxaFixaAplicada(taxas.taxaFixaAplicada());
        pagamento.setValorTaxaEstimada(taxas.valorTaxaEstimada());
        pagamento.setValorLiquidoEstimado(taxas.valorLiquidoEstimado());
        pagamento.setValorJuros(taxas.valorJuros());
        pagamento.setChaveIdempotencia(chaveIdempotencia);
        pagamento.setSolicitadoEm(agora);

        return pagamento;
    }

    public String mapearMetodoMercadoPago(FormaPagamentoCaixa forma) {
        return switch (forma) {
            case CREDITO -> "credit_card";
            case DEBITO -> "debit_card";
            case PIX -> "qr";
            case DINHEIRO -> throw new IllegalArgumentException(
                    "Pagamento em dinheiro não utiliza terminal Point"
            );
        };
    }

    private String gerarReferenciaExterna(Long numeroPedido, UUID pagamentoId) {
        String sufixo = pagamentoId.toString().replace("-", "").substring(0, 12);
        return "PED-" + (numeroPedido == null ? "0" : numeroPedido) + "-" + sufixo;
    }
}
