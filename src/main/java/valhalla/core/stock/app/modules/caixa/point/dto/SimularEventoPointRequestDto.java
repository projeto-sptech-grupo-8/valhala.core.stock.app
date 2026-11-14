package valhalla.core.stock.app.modules.caixa.point.dto;

import jakarta.validation.constraints.NotBlank;

public record SimularEventoPointRequestDto(
        @NotBlank(message = "Informe o status da Order a simular (ex.: processed, failed, canceled, expired, at_terminal)")
        String status,
        String statusDetail,
        String paymentStatus,
        Boolean dispararWebhook
) {
}
