package valhalla.core.stock.app.modules.caixa.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.caixa.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.exception.WebhookAssinaturaInvalidaException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class ValidadorAssinaturaWebhookMercadoPago {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final MercadoPagoPointProperties properties;
    private final Clock clock;

    @Autowired
    public ValidadorAssinaturaWebhookMercadoPago(MercadoPagoPointProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public ValidadorAssinaturaWebhookMercadoPago(MercadoPagoPointProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void validar(String xSignatureHeader, String xRequestIdHeader, String dataId) {
        if (!deveValidarAssinatura()) {
            return;
        }

        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new WebhookAssinaturaInvalidaException("Segredo de webhook do Mercado Pago não configurado");
        }
        if (xSignatureHeader == null || xSignatureHeader.isBlank()) {
            throw new WebhookAssinaturaInvalidaException("Cabeçalho x-signature ausente no webhook");
        }
        if (xRequestIdHeader == null || xRequestIdHeader.isBlank()) {
            throw new WebhookAssinaturaInvalidaException("Cabeçalho x-request-id ausente no webhook");
        }
        if (dataId == null || dataId.isBlank()) {
            throw new WebhookAssinaturaInvalidaException("Identificador do recurso (data.id) ausente para validação HMAC");
        }

        String tsValor = null;
        String v1Valor = null;
        for (String parte : xSignatureHeader.split(",")) {
            String trimmed = parte.trim();
            int idx = trimmed.indexOf('=');
            if (idx <= 0 || idx == trimmed.length() - 1) {
                continue;
            }
            String chave = trimmed.substring(0, idx).trim();
            String valor = trimmed.substring(idx + 1).trim();
            if ("ts".equals(chave)) {
                tsValor = valor;
            } else if ("v1".equals(chave)) {
                v1Valor = valor;
            }
        }

        if (tsValor == null || v1Valor == null) {
            throw new WebhookAssinaturaInvalidaException("Formato inválido no cabeçalho x-signature");
        }

        long timestampSegundos;
        try {
            timestampSegundos = Long.parseLong(tsValor);
        } catch (NumberFormatException ex) {
            throw new WebhookAssinaturaInvalidaException("Timestamp inválido no cabeçalho x-signature");
        }

        long agoraSegundos = Instant.now(clock).getEpochSecond();
        long diferenca = Math.abs(agoraSegundos - timestampSegundos);
        if (diferenca > properties.webhookToleranciaSegundos()) {
            throw new WebhookAssinaturaInvalidaException("Assinatura do webhook expirada ou fora da janela de tolerância");
        }

        String assinaturaEsperada = calcularHmacHex(dataId, xRequestIdHeader.trim(), tsValor, secret);
        byte[] bytesEsperados = assinaturaEsperada.getBytes(StandardCharsets.US_ASCII);
        byte[] bytesRecebidos = v1Valor.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);

        if (!MessageDigest.isEqual(bytesEsperados, bytesRecebidos)) {
            throw new WebhookAssinaturaInvalidaException("Assinatura HMAC do webhook inválida");
        }
    }

    public boolean deveValidarAssinatura() {
        if (!Boolean.TRUE.equals(properties.webhookValidarAssinatura())) {
            return false;
        }
        return properties.modo().usaApiExternaReal()
                || (properties.webhookSecret() != null && !properties.webhookSecret().isBlank());
    }

    public String gerarAssinatura(String dataId, String requestId, long timestampSegundos) {
        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return gerarAssinatura(dataId, requestId, timestampSegundos, secret);
    }

    public String gerarAssinatura(String dataId, String requestId, long timestampSegundos, String secret) {
        String tsStr = Long.toString(timestampSegundos);
        String hmac = calcularHmacHex(dataId, requestId, tsStr, secret);
        return "ts=" + tsStr + ",v1=" + hmac;
    }

    private String calcularHmacHex(String dataId, String requestId, String ts, String secret) {
        String manifest = "id:" + dataId.trim().toLowerCase(Locale.ROOT)
                + ";request-id:" + requestId
                + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Falha ao calcular HMAC-SHA256 para webhook Mercado Pago", ex);
        }
    }
}
