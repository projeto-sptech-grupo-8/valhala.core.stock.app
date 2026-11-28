package valhalla.core.stock.app.modules.caixa.point.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercadoPagoPointPropertiesTest {

    @Test
    void aplicaValoresPadraoEmModoMock() {
        MercadoPagoPointProperties props = new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.MOCK,
                null,
                null,
                null,
                null,
                "https://api.mercadopago.com/",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(props.modo()).isEqualTo(ModoMercadoPagoPoint.MOCK);
        assertThat(props.apiBaseUrl()).isEqualTo("https://api.mercadopago.com");
    }

    @Test
    void assumeModoProducaoPorPadraoQuandoModoNaoInformado() {
        assertThatThrownBy(() -> new MercadoPagoPointProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalStateException.class);

        MercadoPagoPointProperties props = new MercadoPagoPointProperties(
                null,
                "APP_USR-123",
                "segredo-webhook",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(props.modo()).isEqualTo(ModoMercadoPagoPoint.PRODUCAO);
        assertThat(props.webhookValidarAssinatura()).isTrue();
        assertThat(props.webhookToleranciaSegundos()).isEqualTo(300L);
        assertThat(props.apiBaseUrl()).isEqualTo("https://api.mercadopago.com");
        assertThat(props.httpConnectTimeoutMs()).isEqualTo(3000L);
        assertThat(props.httpReadTimeoutMs()).isEqualTo(8000L);
        assertThat(props.orderExpirationTime()).isEqualTo("PT5M");
        assertThat(props.reservaExpiracaoSegundos()).isEqualTo(300L);
        assertThat(props.reconciliacaoAtrasoSegundos()).isEqualTo(20L);
        assertThat(props.reconciliacaoIntervaloMs()).isEqualTo(15000L);
        assertThat(props.reconciliacaoAtivaHabilitada()).isTrue();
        assertThat(props.rateLimitWebhookPorMinuto()).isEqualTo(300);
        assertThat(props.rateLimitCriacaoPorMinuto()).isEqualTo(30);
    }

    @Test
    void exigeCredenciaisESegredoObrigatoriosEmHomologacaoEProducao() {
        assertThatThrownBy(() -> new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.HOMOLOGACAO,
                " ",
                "segredo",
                true,
                300L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MP_ACCESS_TOKEN");

        assertThatThrownBy(() -> new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.PRODUCAO,
                "APP_USR-123",
                "",
                true,
                300L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MP_WEBHOOK_SECRET");

        assertThatThrownBy(() -> new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.PRODUCAO,
                "APP_USR-123",
                "segredo",
                false,
                300L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MP_WEBHOOK_VALIDAR_ASSINATURA");
    }
}
