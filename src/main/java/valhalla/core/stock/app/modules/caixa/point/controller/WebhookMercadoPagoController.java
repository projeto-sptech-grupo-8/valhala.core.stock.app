package valhalla.core.stock.app.modules.caixa.point.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.point.enums.StatusProcessamentoWebhook;
import valhalla.core.stock.app.modules.caixa.point.service.WebhookMercadoPagoPointService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/mercado-pago")
@Tag(name = "Webhooks Mercado Pago", description = "Recebimento assíncrono e idempotente de eventos de Orders Point do Mercado Pago.")
public class WebhookMercadoPagoController {

    private final WebhookMercadoPagoPointService webhookService;

    public WebhookMercadoPagoController(WebhookMercadoPagoPointService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/point")
    @Operation(
            summary = "Receber notificação webhook de pagamento Point",
            description = "Valida assinatura HMAC-SHA256 (x-signature), deduplica eventos em evento_webhook_pagamento e confirma o estado da Order na API do Mercado Pago (Zero-Trust)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook recebido e processado idempotentemente."),
            @ApiResponse(responseCode = "401", description = "Assinatura HMAC inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit de webhook excedido.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> receberWebhookPoint(
            @RequestBody(required = false) String rawPayload,
            @RequestParam(name = "data.id", required = false) String dataIdQuery,
            @RequestParam(name = "id", required = false) String idQuery,
            @RequestParam(name = "type", required = false) String typeQuery,
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId,
            HttpServletRequest request
    ) {
        String origemIp = resolverOrigemIp(request);
        StatusProcessamentoWebhook status = webhookService.processarWebhook(
                rawPayload,
                dataIdQuery,
                idQuery,
                typeQuery,
                xSignature,
                xRequestId,
                origemIp
        );
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    private String resolverOrigemIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int virgula = forwardedFor.indexOf(',');
            return virgula > 0 ? forwardedFor.substring(0, virgula).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }
}
