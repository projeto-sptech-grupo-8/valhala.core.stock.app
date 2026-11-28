package valhalla.core.stock.app.modules.caixa.point.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.point.client.dto.CriarOrderPointCommand;
import valhalla.core.stock.app.modules.caixa.point.client.dto.ModoOperacaoTerminalPoint;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.client.dto.TerminalMercadoPagoDto;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@ConditionalOnExpression(
        "'${app.mercado-pago.point.modo:PRODUCAO}'.equalsIgnoreCase('HOMOLOGACAO') "
                + "|| '${app.mercado-pago.point.modo:PRODUCAO}'.equalsIgnoreCase('PRODUCAO')"
)
public class HttpMercadoPagoPointClient implements MercadoPagoPointClient {

    private static final Pattern ID_EXTERNO_VALIDO = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    private final MercadoPagoPointProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpMercadoPagoPointClient(MercadoPagoPointProperties properties) {
        this(
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.httpConnectTimeoutMs()))
                        .build()
        );
    }

    HttpMercadoPagoPointClient(MercadoPagoPointProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public OrderPointResultado criarOrderPoint(CriarOrderPointCommand command) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "point");
        body.put("external_reference", command.referenciaExterna());
        body.put("expiration_time", command.expirationTime());
        if (command.descricao() != null && !command.descricao().isBlank()) {
            body.put("description", command.descricao());
        }

        ObjectNode transactions = body.putObject("transactions");
        ArrayNode payments = transactions.putArray("payments");
        ObjectNode payment = payments.addObject();
        payment.put("amount", command.valorCobrado().setScale(2, RoundingMode.HALF_UP).toPlainString());

        ObjectNode config = body.putObject("config");
        ObjectNode point = config.putObject("point");
        point.put("terminal_id", command.terminalId());
        point.put("print_on_terminal", "no_ticket");

        if (command.defaultPaymentType() != null && !command.defaultPaymentType().isBlank()) {
            ObjectNode paymentMethod = config.putObject("payment_method");
            paymentMethod.put("default_type", command.defaultPaymentType());
            if ("credit_card".equals(command.defaultPaymentType()) && command.parcelas() != null) {
                paymentMethod.put("default_installments", command.parcelas());
                String installmentsCost = (command.installmentsCost() != null && !command.installmentsCost().isBlank())
                        ? command.installmentsCost()
                        : "seller";
                paymentMethod.put("installments_cost", installmentsCost);
            }
        }

        HttpRequest request = requestBuilder("/v1/orders")
                .header("X-Idempotency-Key", command.chaveIdempotencia())
                .POST(HttpRequest.BodyPublishers.ofString(escreverJson(body), StandardCharsets.UTF_8))
                .build();

        JsonNode json = executar(request);
        return parseOrder(json);
    }

    @Override
    public OrderPointResultado consultarOrder(String orderId) {
        String idValidado = validarIdExterno(orderId, "orderId");
        HttpRequest request = requestBuilder("/v1/orders/" + idValidado)
                .GET()
                .build();
        JsonNode json = executar(request);
        return parseOrder(json);
    }

    @Override
    public OrderPointResultado cancelarOrder(String orderId, String chaveIdempotencia) {
        String idValidado = validarIdExterno(orderId, "orderId");
        HttpRequest.Builder builder = requestBuilder("/v1/orders/" + idValidado + "/cancel");
        if (chaveIdempotencia != null && !chaveIdempotencia.isBlank()) {
            builder.header("X-Idempotency-Key", chaveIdempotencia);
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        JsonNode json = executar(request);
        return parseOrder(json);
    }

    @Override
    public OrderPointResultado estornarOrder(String orderId, String chaveIdempotencia) {
        String idValidado = validarIdExterno(orderId, "orderId");
        HttpRequest.Builder builder = requestBuilder("/v1/orders/" + idValidado + "/refund");
        if (chaveIdempotencia != null && !chaveIdempotencia.isBlank()) {
            builder.header("X-Idempotency-Key", chaveIdempotencia);
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        JsonNode json = executar(request);
        return parseOrder(json);
    }

    private String validarIdExterno(String id, String campo) {
        if (id == null || !ID_EXTERNO_VALIDO.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Identificador externo inválido para " + campo + " no Mercado Pago"
            );
        }
        return id;
    }

    @Override
    public List<TerminalMercadoPagoDto> listarTerminais() {
        HttpRequest request = requestBuilder("/terminals/v1/list")
                .GET()
                .build();
        JsonNode json = executar(request);

        JsonNode lista = json.path("data").path("terminals");
        if (!lista.isArray()) {
            lista = json.path("terminals");
        }
        List<TerminalMercadoPagoDto> resultado = new ArrayList<>();
        if (lista.isArray()) {
            for (JsonNode node : lista) {
                resultado.add(parseTerminal(node));
            }
        }
        return resultado;
    }

    @Override
    public TerminalMercadoPagoDto atualizarModoOperacaoTerminal(
            String terminalId,
            ModoOperacaoTerminalPoint modoOperacao
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode terminals = body.putArray("terminals");
        ObjectNode item = terminals.addObject();
        item.put("id", terminalId);
        item.put("operating_mode", modoOperacao.name());

        HttpRequest request = requestBuilder("/terminals/v1/setup")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(escreverJson(body), StandardCharsets.UTF_8))
                .build();

        JsonNode json = executar(request);
        JsonNode lista = json.path("terminals");
        if (!lista.isArray()) {
            lista = json.path("data").path("terminals");
        }
        if (lista.isArray() && !lista.isEmpty()) {
            return parseTerminal(lista.get(0));
        }
        return new TerminalMercadoPagoDto(terminalId, null, null, null, modoOperacao.name());
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.apiBaseUrl() + path))
                .timeout(Duration.ofMillis(properties.httpReadTimeoutMs()))
                .header("Authorization", "Bearer " + properties.accessToken())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private JsonNode executar(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (status >= 200 && status < 300) {
                if (body.isBlank()) {
                    return objectMapper.createObjectNode();
                }
                return objectMapper.readTree(body);
            }

            boolean transitorio = status >= 500 || status == 429;
            HttpStatus httpStatus = transitorio
                    ? HttpStatus.BAD_GATEWAY
                    : (status == 409 ? HttpStatus.CONFLICT
                    : (status == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST));
            String mensagem = extrairMensagemErro(body, status);
            throw new IntegracaoMercadoPagoException(mensagem, httpStatus, transitorio);
        } catch (HttpTimeoutException timeout) {
            throw new IntegracaoMercadoPagoException(
                    "Tempo esgotado ao comunicar com a API do Mercado Pago Point",
                    timeout,
                    HttpStatus.GATEWAY_TIMEOUT,
                    true
            );
        } catch (IOException io) {
            throw new IntegracaoMercadoPagoException(
                    "Falha de comunicação de rede com a API do Mercado Pago Point",
                    io,
                    HttpStatus.BAD_GATEWAY,
                    true
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IntegracaoMercadoPagoException(
                    "Chamada ao Mercado Pago Point interrompida",
                    interrupted,
                    HttpStatus.BAD_GATEWAY,
                    true
            );
        }
    }

    private OrderPointResultado parseOrder(JsonNode json) {
        String orderId = textOrNull(json, "id");
        String status = textOrNull(json, "status");
        String statusDetail = textOrNull(json, "status_detail");
        String externalReference = textOrNull(json, "external_reference");

        BigDecimal totalAmount = null;
        if (json.hasNonNull("total_amount")) {
            totalAmount = new BigDecimal(json.get("total_amount").asText());
        }

        String paymentId = null;
        String paymentStatus = null;
        JsonNode payments = json.path("transactions").path("payments");
        if (payments.isArray() && !payments.isEmpty()) {
            JsonNode primeiro = payments.get(0);
            paymentId = textOrNull(primeiro, "id");
            paymentStatus = textOrNull(primeiro, "status");
            if (totalAmount == null && primeiro.hasNonNull("amount")) {
                totalAmount = new BigDecimal(primeiro.get("amount").asText());
            }
        }

        return new OrderPointResultado(
                orderId,
                status,
                statusDetail,
                paymentId,
                paymentStatus,
                externalReference,
                totalAmount,
                json.toString()
        );
    }

    private TerminalMercadoPagoDto parseTerminal(JsonNode node) {
        return new TerminalMercadoPagoDto(
                textOrNull(node, "id"),
                textOrNull(node, "pos_id"),
                textOrNull(node, "store_id"),
                textOrNull(node, "external_pos_id"),
                textOrNull(node, "operating_mode")
        );
    }

    private String extrairMensagemErro(String body, int status) {
        try {
            if (body != null && !body.isBlank()) {
                JsonNode node = objectMapper.readTree(body);
                if (node.hasNonNull("message")) {
                    return "Mercado Pago (" + status + "): " + node.get("message").asText();
                }
                JsonNode errors = node.path("errors");
                if (errors.isArray() && !errors.isEmpty()) {
                    JsonNode primeiro = errors.get(0);
                    if (primeiro.hasNonNull("message")) {
                        return "Mercado Pago (" + status + "): " + primeiro.get("message").asText();
                    }
                }
            }
        } catch (JsonProcessingException ignored) {
            // fallback para mensagem genérica abaixo
        }
        return "Erro na comunicação com o Mercado Pago Point (HTTP " + status + ")";
    }

    private String escreverJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar requisição para o Mercado Pago", e);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
