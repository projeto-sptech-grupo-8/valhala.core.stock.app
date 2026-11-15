package valhalla.core.stock.app.modules.caixa.pedido.service;

import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TaxaPagamentoEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TaxaPagamentoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Component
public class CalculoPagamentoEletronicoHelper {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CEM = new BigDecimal("100");

    private final TaxaPagamentoRepository taxaPagamentoRepository;

    public CalculoPagamentoEletronicoHelper(TaxaPagamentoRepository taxaPagamentoRepository) {
        this.taxaPagamentoRepository = taxaPagamentoRepository;
    }

    public void validarFormaHabilitada(FormaPagamentoCaixa forma, ConfiguracaoCaixaEntity configuracao) {
        boolean habilitada = switch (forma) {
            case DEBITO -> configuracao.isDebitoHabilitado();
            case CREDITO -> configuracao.isCreditoHabilitado();
            case PIX -> configuracao.isPixHabilitado();
            case DINHEIRO -> configuracao.isDinheiroHabilitado();
        };
        if (!habilitada) {
            throw new IllegalArgumentException(
                    "A forma de pagamento " + forma + " está desabilitada na configuração do caixa"
            );
        }
    }

    public void validarParcelamento(
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

    public ResultadoTaxas calcularTaxas(
            UUID estabelecimentoId,
            FormaPagamentoCaixa forma,
            int parcelas,
            BigDecimal valor,
            ConfiguracaoCaixaEntity configuracao
    ) {
        TaxaPagamentoEntity taxa = taxaPagamentoRepository
                .findByEstabelecimentoIdAndFormaAndParcelasAndAtivoTrue(estabelecimentoId, forma, parcelas)
                .orElse(null);

        BigDecimal taxaPercentual = taxa != null && taxa.getTaxaPercentual() != null
                ? taxa.getTaxaPercentual().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal taxaFixa = taxa != null && taxa.getTaxaFixa() != null
                ? taxa.getTaxaFixa().setScale(2, RoundingMode.HALF_UP)
                : ZERO;

        boolean repassarAoCliente = taxa != null && taxa.getRepassarAoCliente() != null
                ? taxa.getRepassarAoCliente()
                : configuracao.getPoliticaJuros() == PoliticaJurosCaixa.REPASSADA_AO_CLIENTE;

        PoliticaJurosCaixa politicaAplicada = repassarAoCliente
                ? PoliticaJurosCaixa.REPASSADA_AO_CLIENTE
                : PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA;

        BigDecimal fatorTaxa = taxaPercentual.divide(CEM, 6, RoundingMode.HALF_UP);
        boolean possuiTaxas = taxaPercentual.compareTo(BigDecimal.ZERO) > 0
                || taxaFixa.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal valorCobrado;
        BigDecimal valorJuros;
        BigDecimal valorTaxaEstimada;
        BigDecimal valorLiquidoEstimado;

        if (repassarAoCliente && possuiTaxas) {
            BigDecimal divisor = BigDecimal.ONE.subtract(fatorTaxa);
            if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(
                        "Taxa percentual inválida para repasse ao cliente (igual ou superior a 100%)"
                );
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

        return new ResultadoTaxas(
                politicaAplicada,
                taxaPercentual,
                taxaFixa,
                valorCobrado,
                valorJuros,
                valorTaxaEstimada,
                valorLiquidoEstimado
        );
    }

    public record ResultadoTaxas(
            PoliticaJurosCaixa politicaAplicada,
            BigDecimal taxaPercentualAplicada,
            BigDecimal taxaFixaAplicada,
            BigDecimal valorCobrado,
            BigDecimal valorJuros,
            BigDecimal valorTaxaEstimada,
            BigDecimal valorLiquidoEstimado
    ) {
    }
}
