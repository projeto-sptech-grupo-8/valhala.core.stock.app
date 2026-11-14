package valhalla.core.stock.app.modules.caixa.point.service;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrquestradorPagamentoPointServiceTest {

    @Test
    void resolveInstallmentsCostConformePoliticaEJurosCalculados() {
        PagamentoPedidoEntity absorvida = new PagamentoPedidoEntity();
        absorvida.setPoliticaJuros(PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA);
        absorvida.setValorJuros(BigDecimal.ZERO);
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(absorvida)).isEqualTo("seller");

        PagamentoPedidoEntity repassadaComJurosNoValorCobrado = new PagamentoPedidoEntity();
        repassadaComJurosNoValorCobrado.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        repassadaComJurosNoValorCobrado.setValorJuros(new BigDecimal("5.24"));
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(repassadaComJurosNoValorCobrado))
                .isEqualTo("seller");

        PagamentoPedidoEntity repassadaSemTaxaAtiva = new PagamentoPedidoEntity();
        repassadaSemTaxaAtiva.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        repassadaSemTaxaAtiva.setValorJuros(BigDecimal.ZERO);
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(repassadaSemTaxaAtiva))
                .isEqualTo("buyer");

        PagamentoPedidoEntity repassadaComJurosNulo = new PagamentoPedidoEntity();
        repassadaComJurosNulo.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        repassadaComJurosNulo.setValorJuros(null);
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(repassadaComJurosNulo))
                .isEqualTo("buyer");
    }
}
