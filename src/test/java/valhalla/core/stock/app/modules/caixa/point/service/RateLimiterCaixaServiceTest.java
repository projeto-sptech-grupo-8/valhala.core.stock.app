package valhalla.core.stock.app.modules.caixa.point.service;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.point.config.ModoMercadoPagoPoint;
import valhalla.core.stock.app.modules.caixa.exception.RateLimitExcedidoException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterCaixaServiceTest {

    @Test
    void bloqueiaCriacaoPointEWebhookQuandoExcedeLimitePorMinuto() {
        MercadoPagoPointProperties props = propriedades(2, 2);
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

    @Test
    void limitaQuantidadeDeChavesEmMemoriaERemoveExpiradosOuMaisAntigos() {
        MercadoPagoPointProperties props = propriedades(5, 5);
        AtomicLong relogio = new AtomicLong(1_000L);
        RateLimiterCaixaService rateLimiter = new RateLimiterCaixaService(props, 3, relogio::get);

        rateLimiter.verificarLimiteWebhook("10.0.0.1");
        relogio.addAndGet(100L);
        rateLimiter.verificarLimiteWebhook("10.0.0.2");
        relogio.addAndGet(100L);
        rateLimiter.verificarLimiteWebhook("10.0.0.3");
        assertThat(rateLimiter.quantidadeChavesAtivas()).isEqualTo(3);

        // Sem expirar a janela de 60s, a 4a chave evicta a mais antiga (10.0.0.1) e mantém teto = 3
        relogio.addAndGet(100L);
        rateLimiter.verificarLimiteWebhook("10.0.0.4");
        assertThat(rateLimiter.quantidadeChavesAtivas()).isEqualTo(3);

        // Após > 60s, entradas expiradas são descartadas antes de aceitar nova chave
        relogio.addAndGet(70_000L);
        rateLimiter.verificarLimiteWebhook("10.0.0.5");
        assertThat(rateLimiter.quantidadeChavesAtivas()).isEqualTo(1);
    }

    private MercadoPagoPointProperties propriedades(int limiteWebhook, int limiteCriacao) {
        return new MercadoPagoPointProperties(
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
                limiteWebhook,
                limiteCriacao
        );
    }
}
