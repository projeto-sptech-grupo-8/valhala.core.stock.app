package valhalla.core.stock.app.modules.caixa.service;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.caixa.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.config.ModoMercadoPagoPoint;
import valhalla.core.stock.app.modules.caixa.exception.WebhookAssinaturaInvalidaException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorAssinaturaWebhookMercadoPagoTest {

    private static final Instant AGORA = Instant.parse("2026-11-13T18:00:00Z");
    private static final Clock CLOCK_FIXO = Clock.fixed(AGORA, ZoneOffset.UTC);

    private ValidadorAssinaturaWebhookMercadoPago validador(
            ModoMercadoPagoPoint modo,
            String secret,
            boolean validarAssinatura,
            long toleranciaSegundos
    ) {
        MercadoPagoPointProperties props = new MercadoPagoPointProperties(
                modo,
                modo.usaApiExternaReal() ? "APP_USR-token" : null,
                secret,
                validarAssinatura,
                toleranciaSegundos,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                300,
                30
        );
        return new ValidadorAssinaturaWebhookMercadoPago(props, CLOCK_FIXO);
    }

    @Test
    void aceitaAssinaturaHmacValidaDentroDaTolerancia() {
        ValidadorAssinaturaWebhookMercadoPago validador =
                validador(ModoMercadoPagoPoint.PRODUCAO, "segredo-super-forte", true, 300L);

        long ts = AGORA.getEpochSecond() - 30;
        String xSignature = validador.gerarAssinatura("ORD-ABC-123", "req-999", ts);

        assertThatCode(() -> validador.validar(xSignature, "req-999", "ORD-ABC-123"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejeitaAssinaturaAusenteAdulteradaOuExpirada() {
        ValidadorAssinaturaWebhookMercadoPago validador =
                validador(ModoMercadoPagoPoint.MOCK, "segredo-test", true, 300L);

        long tsValido = AGORA.getEpochSecond();
        String valida = validador.gerarAssinatura("ord-1", "req-1", tsValido);

        assertThatThrownBy(() -> validador.validar(null, "req-1", "ord-1"))
                .isInstanceOf(WebhookAssinaturaInvalidaException.class);
        assertThatThrownBy(() -> validador.validar(valida, null, "ord-1"))
                .isInstanceOf(WebhookAssinaturaInvalidaException.class);
        assertThatThrownBy(() -> validador.validar(valida.replace("v1=", "v1=0"), "req-1", "ord-1"))
                .isInstanceOf(WebhookAssinaturaInvalidaException.class);
        assertThatThrownBy(() -> validador.validar(valida, "req-outro", "ord-1"))
                .isInstanceOf(WebhookAssinaturaInvalidaException.class);

        long tsExpirado = AGORA.getEpochSecond() - 301;
        String expirada = validador.gerarAssinatura("ord-1", "req-1", tsExpirado);
        assertThatThrownBy(() -> validador.validar(expirada, "req-1", "ord-1"))
                .isInstanceOf(WebhookAssinaturaInvalidaException.class)
                .hasMessageContaining("expirada");
    }

    @Test
    void ignoraValidacaoEmMockQuandoFlagDesabilitadaOuSegredoVazio() {
        ValidadorAssinaturaWebhookMercadoPago semFlag =
                validador(ModoMercadoPagoPoint.MOCK, "segredo", false, 300L);
        assertThatCode(() -> semFlag.validar(null, null, null)).doesNotThrowAnyException();

        ValidadorAssinaturaWebhookMercadoPago semSegredo =
                validador(ModoMercadoPagoPoint.MOCK, "", true, 300L);
        assertThatCode(() -> semSegredo.validar(null, null, null)).doesNotThrowAnyException();
    }
}
