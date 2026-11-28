package valhalla.core.stock.app.modules.caixa.pedido.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TaxaPagamentoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import valhalla.core.stock.app.modules.caixa.pedido.service.CalculoPagamentoEletronicoHelper;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

@Component
public class ProcessadorPagamentoManual implements ProcessadorPagamento {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CalculoPagamentoEletronicoHelper calculoHelper;

    @Autowired
    public ProcessadorPagamentoManual(CalculoPagamentoEletronicoHelper calculoHelper) {
        this.calculoHelper = calculoHelper;
    }

    public ProcessadorPagamentoManual(TaxaPagamentoRepository taxaPagamentoRepository) {
        this(new CalculoPagamentoEletronicoHelper(taxaPagamentoRepository));
    }

    @Override
    public boolean suporta(FormaPagamentoCaixa forma, ConfiguracaoCaixaEntity configuracao) {
        return forma == FormaPagamentoCaixa.DEBITO
                || forma == FormaPagamentoCaixa.CREDITO
                || forma == FormaPagamentoCaixa.PIX;
    }

    @Override
    public PagamentoPedidoEntity processar(
            PedidoEntity pedido,
            SessaoCaixaEntity sessaoCaixa,
            ConfiguracaoCaixaEntity configuracao,
            RegistrarPagamentoPedidoRequestDto dto
    ) {
        calculoHelper.validarFormaHabilitada(dto.forma(), configuracao);

        if (!configuracao.isPermitirPagamentoManual()) {
            throw new IllegalArgumentException(
                    "O registro manual para " + dto.forma() + " está desabilitado na configuração do caixa"
            );
        }
        if (dto.valorRecebido() != null) {
            throw new IllegalArgumentException("O valor recebido só deve ser informado para pagamentos em dinheiro");
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

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setPedidoId(pedido.getId());
        pagamento.setSessaoCaixaId(sessaoCaixa.getId());
        pagamento.setForma(dto.forma());
        pagamento.setStatus(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
        pagamento.setProvedor("MANUAL");
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
        pagamento.setChaveIdempotencia(normalizar(dto.chaveIdempotencia()));
        pagamento.setComprovanteReferencia(normalizar(dto.comprovanteReferencia()));
        pagamento.setSolicitadoEm(FusoHorarioOperacao.agora());

        return pagamento;
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
