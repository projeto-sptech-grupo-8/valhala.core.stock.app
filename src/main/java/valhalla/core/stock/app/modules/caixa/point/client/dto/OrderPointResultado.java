package valhalla.core.stock.app.modules.caixa.point.client.dto;

import java.math.BigDecimal;

public record OrderPointResultado(
        String orderId,
        String status,
        String statusDetail,
        String paymentId,
        String paymentStatus,
        String externalReference,
        BigDecimal totalAmount,
        String rawJson
) {
}
