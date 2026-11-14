package valhalla.core.stock.app.modules.caixa.point.service;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.point.config.ModoMercadoPagoPoint;
import valhalla.core.stock.app.modules.caixa.exception.RateLimitExcedidoException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterCaixaServiceTest {

    @Test
    void bloqueiaCriacaoPointEWebhookQuandoExcedeLimitePorMinuto() {
        MercadoPagoPointProperties props = new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.MOCK,
                null,
                null,
                true,
                300L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                2,
                2
        );
        RateLimiterCaixaService rateLimiter = new RateLimiterCaixaService(props);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        assertThatCode(() -> {
            rateLimiter.verificarLimiteCriacaoPoint(user1);
            rateLimiter.verificarLimiteCriacaoPoint(user1);
            rateLimiter.verificarLimiteCriacaoPoint(user2);
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> rateLimiter.verificarLimiteCriacaoPoint(user1))
                .isInstanceOf(RateLimitExcedidoException.class);

        rateLimiter.verificarLimiteWebhook("10.0.0.1");
        rateLimiter.verificarLimiteWebhook("10.0.0.1");
        assertThatThrownBy(() -> rateLimiter.verificarLimiteWebhook("10.0.0.1"))
                .isInstanceOf(RateLimitExcedidoException.class);

        rateLimiter.limpar();
        assertThatCode(() -> rateLimiter.verificarLimiteWebhook("10.0.0.1")).doesNotThrowAnyException();
    }
}
