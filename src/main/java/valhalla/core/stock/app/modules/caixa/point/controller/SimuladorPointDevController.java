package valhalla.core.stock.app.modules.caixa.point.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.point.client.MockMercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.dto.SimularEventoPointRequestDto;
import valhalla.core.stock.app.modules.caixa.point.enums.StatusProcessamentoWebhook;
import valhalla.core.stock.app.modules.caixa.point.service.ValidadorAssinaturaWebhookMercadoPago;
import valhalla.core.stock.app.modules.caixa.point.service.WebhookMercadoPagoPointService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@Profile({"dev", "test"})
@ConditionalOnProperty(
        prefix = "app.mercado-pago.point",
        name = "modo",
        havingValue = "MOCK",
        matchIfMissing = true
)
@RequestMapping("/dev/mercado-pago/point")
@Tag(name = "Simulador Point (Dev/Test)", description = "Simula eventos da maquininha Point Smart 2 localmente quando MP_POINT_MODO=MOCK.")
@SecurityRequirement(name = "accessTokenCookie")
public class SimuladorPointDevController {

    private final MockMercadoPagoPointClient mockMercadoPagoPointClient;
    private final WebhookMercadoPagoPointService webhookMercadoPagoPointService;
    private final ValidadorAssinaturaWebhookMercadoPago validadorAssinatura;

    public SimuladorPointDevController(
            MockMercadoPagoPointClient mockMercadoPagoPointClient,
            WebhookMercadoPagoPointService webhookMercadoPagoPointService,
            ValidadorAssinaturaWebhookMercadoPago validadorAssinatura
    ) {
        this.mockMercadoPagoPointClient = mockMercadoPagoPointClient;
        this.webhookMercadoPagoPointService = webhookMercadoPagoPointService;
        this.validadorAssinatura = validadorAssinatura;
    }

    @PostMapping("/orders/{orderId}/simular")
    @SecurityRequirement(name = "csrfTokenHeader")
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    @Operation(
            summary = "Simular transição de Order na maquininha Point (MOCK)",
            description = "Atualiza o estado da Order simulada em memória e, por padrão, dispara o pipeline completo de webhook."
    )
    public ResponseEntity<Map<String, Object>> simularEventoOrder(
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody SimularEventoPointRequestDto requestDto
    ) {
        OrderPointResultado resultado = mockMercadoPagoPointClient.atualizarStatusMock(
                orderId,
                requestDto.status(),
                requestDto.statusDetail(),
                requestDto.paymentStatus()
        );

        StatusProcessamentoWebhook statusWebhook = null;
        if (requestDto.dispararWebhook() == null || Boolean.TRUE.equals(requestDto.dispararWebhook())) {
            String requestId = "sim-req-" + UUID.randomUUID();
            String eventoId = "sim-evt-" + UUID.randomUUID();
            String payload = """
                    {"id":"%s","type":"order","action":"order.%s","data":{"id":"%s"}}
                    """.formatted(eventoId, requestDto.status(), orderId).trim();
            String assinatura = validadorAssinatura.gerarAssinatura(
                    orderId,
                    requestId,
                    Instant.now().getEpochSecond()
            );
            statusWebhook = webhookMercadoPagoPointService.processarWebhook(
                    payload,
                    orderId,
                    orderId,
                    "order",
                    assinatura,
                    requestId,
                    "127.0.0.1"
            );
        }

        return ResponseEntity.ok(Map.of(
                "orderId", resultado.orderId(),
                "status", resultado.status(),
                "paymentStatus", resultado.paymentStatus() == null ? "" : resultado.paymentStatus(),
                "webhookProcessado", statusWebhook == null ? "NAO_DISPARADO" : statusWebhook.name()
        ));
    }
}
