package valhalla.core.stock.app.modules.caixa.point.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mercado-pago.point")
public record MercadoPagoPointProperties(
        ModoMercadoPagoPoint modo,
        String accessToken,
        String webhookSecret,
        Boolean webhookValidarAssinatura,
        Long webhookToleranciaSegundos,
        String apiBaseUrl,
        Long httpConnectTimeoutMs,
        Long httpReadTimeoutMs,
        String orderExpirationTime,
        Long reservaExpiracaoSegundos,
        Long reconciliacaoAtrasoSegundos,
        Long reconciliacaoIntervaloMs,
        Boolean reconciliacaoAtivaHabilitada,
        Integer rateLimitWebhookPorMinuto,
        Integer rateLimitCriacaoPorMinuto
) {

    public MercadoPagoPointProperties {
        if (modo == null) {
            modo = ModoMercadoPagoPoint.MOCK;
        }
        if (webhookValidarAssinatura == null) {
            webhookValidarAssinatura = true;
        }
        if (webhookToleranciaSegundos == null || webhookToleranciaSegundos <= 0) {
            webhookToleranciaSegundos = 300L;
        }
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://api.mercadopago.com";
        } else if (apiBaseUrl.endsWith("/")) {
            apiBaseUrl = apiBaseUrl.substring(0, apiBaseUrl.length() - 1);
        }
        if (httpConnectTimeoutMs == null || httpConnectTimeoutMs <= 0) {
            httpConnectTimeoutMs = 3000L;
        }
        if (httpReadTimeoutMs == null || httpReadTimeoutMs <= 0) {
            httpReadTimeoutMs = 8000L;
        }
        if (orderExpirationTime == null || orderExpirationTime.isBlank()) {
            orderExpirationTime = "PT5M";
        }
        if (reservaExpiracaoSegundos == null || reservaExpiracaoSegundos <= 0) {
            reservaExpiracaoSegundos = 300L;
        }
        if (reconciliacaoAtrasoSegundos == null || reconciliacaoAtrasoSegundos <= 0) {
            reconciliacaoAtrasoSegundos = 20L;
        }
        if (reconciliacaoIntervaloMs == null || reconciliacaoIntervaloMs <= 0) {
            reconciliacaoIntervaloMs = 15000L;
        }
        if (reconciliacaoAtivaHabilitada == null) {
            reconciliacaoAtivaHabilitada = true;
        }
        if (rateLimitWebhookPorMinuto == null || rateLimitWebhookPorMinuto <= 0) {
            rateLimitWebhookPorMinuto = 300;
        }
        if (rateLimitCriacaoPorMinuto == null || rateLimitCriacaoPorMinuto <= 0) {
            rateLimitCriacaoPorMinuto = 30;
        }

        if (modo.usaApiExternaReal()) {
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException(
                        "MP_ACCESS_TOKEN é obrigatório quando MP_POINT_MODO=" + modo
                );
            }
            if (webhookSecret == null || webhookSecret.isBlank()) {
                throw new IllegalStateException(
                        "MP_WEBHOOK_SECRET é obrigatório quando MP_POINT_MODO=" + modo
                );
            }
            if (!Boolean.TRUE.equals(webhookValidarAssinatura)) {
                throw new IllegalStateException(
                        "MP_WEBHOOK_VALIDAR_ASSINATURA deve ser true quando MP_POINT_MODO=" + modo
                );
            }
        }
    }
}
