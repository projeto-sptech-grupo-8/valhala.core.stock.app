package valhalla.core.stock.app.modules.caixa.service;

import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.entity.TaxaPagamentoEntity;
import valhalla.core.stock.app.modules.caixa.repository.TaxaPagamentoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class ProcessadorPagamentoManual implements ProcessadorPagamento {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CEM = new BigDecimal("100");

    private final TaxaPagamentoRepository taxaPagamentoRepository;

    public ProcessadorPagamentoManual(TaxaPagamentoRepository taxaPagamentoRepository) {
        this.taxaPagamentoRepository = taxaPagamentoRepository;
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
        validarFormaHabilitada(dto.forma(), configuracao);

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
        validarParcelamento(dto.forma(), parcelas, valor, configuracao);

        TaxaPagamentoEntity taxa = taxaPagamentoRepository
                .findByEstabelecimentoIdAndFormaAndParcelasAndAtivoTrue(
                        pedido.getEstabelecimentoId(),
                        dto.forma(),
                        parcelas
                )
                .orElse(null);

        BigDecimal taxaPercentual = taxa != null && taxa.getTaxaPercentual() != null
                ? taxa.getTaxaPercentual().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal taxaFixa = taxa != null && taxa.getTaxaFixa() != null
                ? taxa.getTaxaFixa().setScale(2, RoundingMode.HALF_UP)
                : ZERO;

        boolean repassarAoCliente = resolverRepasse(taxa, configuracao);
        PoliticaJurosCaixa politicaAplicada = repassarAoCliente
                ? PoliticaJurosCaixa.REPASSADA_AO_CLIENTE
                : PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA;

        BigDecimal valorCobrado;
        BigDecimal valorJuros;
        BigDecimal valorTaxaEstimada;
        BigDecimal valorLiquidoEstimado;

        BigDecimal fatorTaxa = taxaPercentual.divide(CEM, 6, RoundingMode.HALF_UP);
        boolean possuiTaxas = taxaPercentual.compareTo(BigDecimal.ZERO) > 0 || taxaFixa.compareTo(BigDecimal.ZERO) > 0;

        if (repassarAoCliente && possuiTaxas) {
            BigDecimal divisor = BigDecimal.ONE.subtract(fatorTaxa);
            if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Taxa percentual inválida para repasse ao cliente (igual ou superior a 100%)");
            }

            valorCobrado = valor.add(taxaFixa).divide(divisor, 2, RoundingMode.HALF_UP);

            if (valorCobrado.compareTo(valor) < 0) {
                valorCobrado = valor;
            }

            valorJuros = valorCobrado.subtract(valor);

            valorTaxaEstimada = valorJuros;
            valorLiquidoEstimado = valor;
        } else {
            valorCobrado = valor;
            valorJuros = ZERO;
            valorTaxaEstimada = valor.multiply(fatorTaxa).add(taxaFixa).setScale(2, RoundingMode.HALF_UP);
            valorLiquidoEstimado = valor.subtract(valorTaxaEstimada);
        }

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setPedidoId(pedido.getId());
        pagamento.setSessaoCaixaId(sessaoCaixa.getId());
        pagamento.setForma(dto.forma());
        pagamento.setStatus(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
        pagamento.setProvedor("MANUAL");
        pagamento.setValor(valor);
        pagamento.setValorCobrado(valorCobrado);
        pagamento.setValorRecebido(null);
        pagamento.setValorTroco(ZERO);
        pagamento.setValorEstornado(ZERO);
        pagamento.setParcelas(parcelas);
        pagamento.setPoliticaJuros(politicaAplicada);
        pagamento.setTaxaPercentualAplicada(taxaPercentual);
        pagamento.setTaxaFixaAplicada(taxaFixa);
        pagamento.setValorTaxaEstimada(valorTaxaEstimada);
        pagamento.setValorLiquidoEstimado(valorLiquidoEstimado);
        pagamento.setValorJuros(valorJuros);
        pagamento.setChaveIdempotencia(normalizar(dto.chaveIdempotencia()));
        pagamento.setComprovanteReferencia(normalizar(dto.comprovanteReferencia()));
        pagamento.setSolicitadoEm(LocalDateTime.now());

        return pagamento;
    }


    private void validarFormaHabilitada(FormaPagamentoCaixa forma, ConfiguracaoCaixaEntity configuracao) {
        boolean habilitada = switch (forma) {
            case DEBITO -> configuracao.isDebitoHabilitado();
            case CREDITO -> configuracao.isCreditoHabilitado();
            case PIX -> configuracao.isPixHabilitado();
            case DINHEIRO -> configuracao.isDinheiroHabilitado();
        };
        if (!habilitada) {
            throw new IllegalArgumentException("A forma de pagamento " + forma + " está desabilitada na configuração do caixa");
        }
    }

    private void validarParcelamento(
            FormaPagamentoCaixa forma,
            int parcelas,
            BigDecimal valor,
            ConfiguracaoCaixaEntity configuracao
    ) {
        if (forma != FormaPagamentoCaixa.CREDITO) {
            if (parcelas != 1) {
                throw new IllegalArgumentException("Parcelamento é permitido apenas para cartão de crédito");
            }
            return;
        }
        if (parcelas == 1) {
            return;
        }
        if (!configuracao.isParcelamentoHabilitado()) {
            throw new IllegalArgumentException("O parcelamento no cartão de crédito está desabilitado");
        }
        if (parcelas < configuracao.getParcelasMinimas() || parcelas > configuracao.getParcelasMaximas()) {
            throw new IllegalArgumentException(
                    "A quantidade de parcelas deve estar entre "
                            + configuracao.getParcelasMinimas()
                            + " e "
                            + configuracao.getParcelasMaximas()
            );
        }
        if (configuracao.getValorMinimoParcela() != null
                && configuracao.getValorMinimoParcela().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal valorPorParcela = valor.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
            if (valorPorParcela.compareTo(configuracao.getValorMinimoParcela()) < 0) {
                throw new IllegalArgumentException(
                        "O valor de cada parcela não pode ser inferior ao mínimo configurado de R$ "
                                + configuracao.getValorMinimoParcela()
                );
            }
        }
    }

    private boolean resolverRepasse(TaxaPagamentoEntity taxa, ConfiguracaoCaixaEntity configuracao) {
        if (taxa != null && taxa.getRepassarAoCliente() != null) {
            return taxa.getRepassarAoCliente();
        }
        return configuracao.getPoliticaJuros() == PoliticaJurosCaixa.REPASSADA_AO_CLIENTE;
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
