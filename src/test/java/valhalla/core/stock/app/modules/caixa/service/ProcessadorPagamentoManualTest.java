package valhalla.core.stock.app.modules.caixa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessadorPagamentoManualTest {

    @Mock
    private TaxaPagamentoRepository taxaPagamentoRepository;

    private ProcessadorPagamentoManual processador;
    private UUID estabelecimentoId;
    private PedidoEntity pedido;
    private SessaoCaixaEntity sessao;

    @BeforeEach
    void preparar() {
        processador = new ProcessadorPagamentoManual(taxaPagamentoRepository);
        estabelecimentoId = UUID.randomUUID();

        pedido = new PedidoEntity();
        pedido.setId(UUID.randomUUID());
        pedido.setEstabelecimentoId(estabelecimentoId);

        sessao = new SessaoCaixaEntity();
        sessao.setId(UUID.randomUUID());
    }

    @ParameterizedTest
    @EnumSource(value = FormaPagamentoCaixa.class, names = {"DEBITO", "CREDITO", "PIX"})
    void deveSuportarFormasManuais(FormaPagamentoCaixa forma) {
        assertThat(processador.suporta(forma, new ConfiguracaoCaixaEntity())).isTrue();
    }

    @Test
    void naoDeveSuportarDinheiroComoPagamentoManual() {
        assertThat(processador.suporta(FormaPagamentoCaixa.DINHEIRO, new ConfiguracaoCaixaEntity())).isFalse();
    }

    @Test
    void deveRejeitarQuandoFormaEstaDesabilitadaNaConfiguracao() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        config.setPixHabilitado(false);

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.PIX, new BigDecimal("50.00"), 1, null, null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desabilitada");
    }

    @Test
    void deveRejeitarQuandoPagamentoManualEstaDesabilitadoGeral() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        config.setPermitirPagamentoManual(false);

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.DEBITO, new BigDecimal("50.00"), 1, null, null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registro manual para DEBITO está desabilitado");
    }

    @Test
    void deveRejeitarQuandoValorRecebidoForInformadoParaManual() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.DEBITO, new BigDecimal("50.00"), 1, new BigDecimal("60.00"), null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor recebido só deve ser informado para pagamentos em dinheiro");
    }

    @Test
    void deveRejeitarParcelamentoParaDebitoOuPix() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.DEBITO, new BigDecimal("100.00"), 2, null, null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcelamento é permitido apenas para cartão de crédito");
    }

    @Test
    void deveRejeitarParcelamentoNoCreditoQuandoDesabilitado() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        config.setParcelamentoHabilitado(false);

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.CREDITO, new BigDecimal("100.00"), 3, null, null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parcelamento no cartão de crédito está desabilitado");
    }

    @Test
    void deveRejeitarParcelamentoForaDoIntervaloPermitido() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        config.setParcelamentoHabilitado(true);
        config.setParcelasMinimas(2);
        config.setParcelasMaximas(6);

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.CREDITO, new BigDecimal("100.00"), 8, null, null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deve estar entre 2 e 6");
    }

    @Test
    void deveRejeitarParcelamentoAbaixoDoValorMinimoPorParcela() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        config.setParcelamentoHabilitado(true);
        config.setParcelasMinimas(2);
        config.setParcelasMaximas(6);
        config.setValorMinimoParcela(new BigDecimal("50.00"));

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.CREDITO, new BigDecimal("80.00"), 2, null, null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inferior ao mínimo configurado de R$ 50.00");
    }

    @Test
    void deveProcessarPagamentoComTaxaAbsorvidaPelaLoja() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        config.setPoliticaJuros(PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA);

        TaxaPagamentoEntity taxa = new TaxaPagamentoEntity();
        taxa.setTaxaPercentual(new BigDecimal("2.50"));
        taxa.setTaxaFixa(new BigDecimal("0.50"));
        taxa.setRepassarAoCliente(false);

        when(taxaPagamentoRepository.findByEstabelecimentoIdAndFormaAndParcelasAndAtivoTrue(
                estabelecimentoId, FormaPagamentoCaixa.DEBITO, 1))
                .thenReturn(Optional.of(taxa));

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.DEBITO, new BigDecimal("100.00"), 1, null, "IDEMP-123", "AUTH-999");

        PagamentoPedidoEntity resultado = processador.processar(pedido, sessao, config, dto);

        assertThat(resultado.getStatus()).isEqualTo(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
        assertThat(resultado.getProvedor()).isEqualTo("MANUAL");
        assertThat(resultado.getValor()).isEqualByComparingTo("100.00");
        assertThat(resultado.getValorCobrado()).isEqualByComparingTo("100.00");
        assertThat(resultado.getValorJuros()).isEqualByComparingTo("0.00");
        // Taxa = 100 * 2.5% + 0.50 = 3.00
        assertThat(resultado.getValorTaxaEstimada()).isEqualByComparingTo("3.00");
        assertThat(resultado.getValorLiquidoEstimado()).isEqualByComparingTo("97.00");
        assertThat(resultado.getPoliticaJuros()).isEqualTo(PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA);
        assertThat(resultado.getChaveIdempotencia()).isEqualTo("IDEMP-123");
        assertThat(resultado.getComprovanteReferencia()).isEqualTo("AUTH-999");
    }

    @Test
    void deveProcessarPagamentoComTaxaRepassadaAoCliente() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        config.setParcelamentoHabilitado(true);
        config.setParcelasMinimas(1);
        config.setParcelasMaximas(12);

        TaxaPagamentoEntity taxa = new TaxaPagamentoEntity();
        taxa.setTaxaPercentual(new BigDecimal("10.00")); // 10%
        taxa.setTaxaFixa(new BigDecimal("1.00"));
        taxa.setRepassarAoCliente(true);

        when(taxaPagamentoRepository.findByEstabelecimentoIdAndFormaAndParcelasAndAtivoTrue(
                estabelecimentoId, FormaPagamentoCaixa.CREDITO, 3))
                .thenReturn(Optional.of(taxa));

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.CREDITO, new BigDecimal("100.00"), 3, null, null, null);

        PagamentoPedidoEntity resultado = processador.processar(pedido, sessao, config, dto);

        // Repasse: valorCobrado = (100 + 1.00) / (1 - 0.10) = 101 / 0.90 = 112.22
        assertThat(resultado.getValorCobrado()).isEqualByComparingTo("112.22");
        assertThat(resultado.getValorJuros()).isEqualByComparingTo("12.22");
        assertThat(resultado.getValorTaxaEstimada()).isEqualByComparingTo("12.22");
        assertThat(resultado.getValorLiquidoEstimado()).isEqualByComparingTo("100.00");
        assertThat(resultado.getPoliticaJuros()).isEqualTo(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
    }

    @Test
    void deveFalharQuandoTaxaPercentualParaRepasseForMaiorOuIgualACem() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();

        TaxaPagamentoEntity taxa = new TaxaPagamentoEntity();
        taxa.setTaxaPercentual(new BigDecimal("100.00"));
        taxa.setRepassarAoCliente(true);

        when(taxaPagamentoRepository.findByEstabelecimentoIdAndFormaAndParcelasAndAtivoTrue(
                estabelecimentoId, FormaPagamentoCaixa.PIX, 1))
                .thenReturn(Optional.of(taxa));

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.PIX, new BigDecimal("50.00"), 1, null, null, null);

        assertThatThrownBy(() -> processador.processar(pedido, sessao, config, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("igual ou superior a 100%");
    }

    @Test
    void deveProcessarSemTaxaCadastradaUsandoValoresPadrao() {
        ConfiguracaoCaixaEntity config = configuracaoPadrao();
        when(taxaPagamentoRepository.findByEstabelecimentoIdAndFormaAndParcelasAndAtivoTrue(
                any(), any(), eq(1)))
                .thenReturn(Optional.empty());

        RegistrarPagamentoPedidoRequestDto dto = new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.PIX, new BigDecimal("80.00"), null, null, "   ", "");

        PagamentoPedidoEntity resultado = processador.processar(pedido, sessao, config, dto);

        assertThat(resultado.getValorCobrado()).isEqualByComparingTo("80.00");
        assertThat(resultado.getValorTaxaEstimada()).isEqualByComparingTo("0.00");
        assertThat(resultado.getValorLiquidoEstimado()).isEqualByComparingTo("80.00");
        assertThat(resultado.getChaveIdempotencia()).isNull();
        assertThat(resultado.getComprovanteReferencia()).isNull();
    }

    private ConfiguracaoCaixaEntity configuracaoPadrao() {
        ConfiguracaoCaixaEntity config = new ConfiguracaoCaixaEntity();
        config.setPermitirPagamentoManual(true);
        config.setDebitoHabilitado(true);
        config.setCreditoHabilitado(true);
        config.setPixHabilitado(true);
        config.setDinheiroHabilitado(true);
        config.setPoliticaJuros(PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA);
        return config;
    }
}
