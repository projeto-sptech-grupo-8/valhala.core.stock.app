package valhalla.core.stock.app.modules.caixa.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.client.dto.CriarOrderPointCommand;
import valhalla.core.stock.app.modules.caixa.client.dto.ModoOperacaoTerminalPoint;
import valhalla.core.stock.app.modules.caixa.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.client.dto.TerminalMercadoPagoDto;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(
        prefix = "app.mercado-pago.point",
        name = "modo",
        havingValue = "MOCK",
        matchIfMissing = true
)
public class MockMercadoPagoPointClient implements MercadoPagoPointClient {

    private final Map<String, OrderPointResultado> ordersPorId = new ConcurrentHashMap<>();
    private final Map<String, String> orderIdPorChaveIdempotencia = new ConcurrentHashMap<>();
    private final Map<String, TerminalMercadoPagoDto> terminaisPorId = new ConcurrentHashMap<>();
    private final AtomicReference<RuntimeException> proximaFalhaCriacao = new AtomicReference<>();

    public MockMercadoPagoPointClient() {
        terminaisPorId.put(
                "PAX_A910__SMARTPOS_MOCK_01",
                new TerminalMercadoPagoDto(
                        "PAX_A910__SMARTPOS_MOCK_01",
                        "POS-MOCK-01",
                        "STORE-MOCK-01",
                        "CAIXA-01",
                        ModoOperacaoTerminalPoint.PDV.name()
                )
        );
    }

    @Override
    public OrderPointResultado criarOrderPoint(CriarOrderPointCommand command) {
        RuntimeException falha = proximaFalhaCriacao.getAndSet(null);
        if (falha != null) {
            throw falha;
        }

        if (command.chaveIdempotencia() != null) {
            String existenteId = orderIdPorChaveIdempotencia.get(command.chaveIdempotencia());
            if (existenteId != null && ordersPorId.containsKey(existenteId)) {
                return ordersPorId.get(existenteId);
            }
        }

        String orderId = "ORD-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String paymentId = "PAY-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        BigDecimal total = command.valorCobrado().setScale(2, RoundingMode.HALF_UP);

        String json = """
                {"id":"%s","type":"point","status":"created","status_detail":"waiting_terminal","external_reference":"%s","total_amount":"%s","transactions":{"payments":[{"id":"%s","status":"created","amount":"%s"}]}}
                """.formatted(
                orderId,
                command.referenciaExterna(),
                total.toPlainString(),
                paymentId,
                total.toPlainString()
        ).trim();

        OrderPointResultado resultado = new OrderPointResultado(
                orderId,
                "created",
                "waiting_terminal",
                paymentId,
                "created",
                command.referenciaExterna(),
                total,
                json
        );
        ordersPorId.put(orderId, resultado);
        if (command.chaveIdempotencia() != null) {
            orderIdPorChaveIdempotencia.put(command.chaveIdempotencia(), orderId);
        }
        return resultado;
    }

    @Override
    public OrderPointResultado consultarOrder(String orderId) {
        OrderPointResultado order = ordersPorId.get(orderId);
        if (order == null) {
            throw new IntegracaoMercadoPagoException(
                    "Order simulada não encontrada: " + orderId,
                    HttpStatus.NOT_FOUND,
                    false
            );
        }
        return order;
    }

    @Override
    public OrderPointResultado cancelarOrder(String orderId, String chaveIdempotencia) {
        OrderPointResultado atual = consultarOrder(orderId);
        if ("processed".equalsIgnoreCase(atual.status())) {
            throw new IntegracaoMercadoPagoException(
                    "A cobrança já foi paga na maquininha e não pode mais ser cancelada",
                    HttpStatus.CONFLICT,
                    false
            );
        }
        OrderPointResultado cancelada = atualizarStatusMock(orderId, "canceled", "canceled_by_pos", "canceled");
        return cancelada;
    }

    @Override
    public List<TerminalMercadoPagoDto> listarTerminais() {
        return new ArrayList<>(terminaisPorId.values());
    }

    @Override
    public TerminalMercadoPagoDto atualizarModoOperacaoTerminal(
            String terminalId,
            ModoOperacaoTerminalPoint modoOperacao
    ) {
        TerminalMercadoPagoDto existente = terminaisPorId.get(terminalId);
        TerminalMercadoPagoDto atualizado = new TerminalMercadoPagoDto(
                terminalId,
                existente == null ? "POS-MOCK" : existente.posId(),
                existente == null ? "STORE-MOCK" : existente.storeId(),
                existente == null ? null : existente.externalPosId(),
                modoOperacao.name()
        );
        terminaisPorId.put(terminalId, atualizado);
        return atualizado;
    }

    public OrderPointResultado atualizarStatusMock(
            String orderId,
            String status,
            String statusDetail,
            String paymentStatus
    ) {
        OrderPointResultado atual = consultarOrder(orderId);
        String json = """
                {"id":"%s","type":"point","status":"%s","status_detail":"%s","external_reference":"%s","total_amount":"%s","transactions":{"payments":[{"id":"%s","status":"%s","amount":"%s"}]}}
                """.formatted(
                atual.orderId(),
                status,
                statusDetail == null ? "" : statusDetail,
                atual.externalReference(),
                atual.totalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                atual.paymentId(),
                paymentStatus == null ? status : paymentStatus,
                atual.totalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
        ).trim();

        OrderPointResultado atualizado = new OrderPointResultado(
                atual.orderId(),
                status,
                statusDetail,
                atual.paymentId(),
                paymentStatus == null ? status : paymentStatus,
                atual.externalReference(),
                atual.totalAmount(),
                json
        );
        ordersPorId.put(orderId, atualizado);
        return atualizado;
    }

    public void configurarProximaFalhaCriacao(RuntimeException excecao) {
        proximaFalhaCriacao.set(excecao);
    }

    public void limparEstadoMock() {
        ordersPorId.clear();
        orderIdPorChaveIdempotencia.clear();
        proximaFalhaCriacao.set(null);
    }
}
