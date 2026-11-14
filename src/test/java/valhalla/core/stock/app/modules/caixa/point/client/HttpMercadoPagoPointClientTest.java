package valhalla.core.stock.app.modules.caixa.point.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import valhalla.core.stock.app.modules.caixa.point.client.dto.CriarOrderPointCommand;
import valhalla.core.stock.app.modules.caixa.point.client.dto.ModoOperacaoTerminalPoint;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.client.dto.TerminalMercadoPagoDto;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.point.config.ModoMercadoPagoPoint;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpMercadoPagoPointClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void iniciarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void pararServidor() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpMercadoPagoPointClient criarClient(long readTimeoutMs, String customBaseUrl) {
        MercadoPagoPointProperties props = new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.HOMOLOGACAO,
                "APP_USR-TOKEN-TESTE",
                "secret-teste",
                true,
                300L,
                customBaseUrl,
                1000L,
                readTimeoutMs,
                "PT5M",
                300L,
                20L,
                15000L,
                false,
                300,
                30
        );
        return new HttpMercadoPagoPointClient(props);
    }

    @Test
    void criaConsultaECancelaOrderPointEGereTerminaisViaHttp() {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> idempotencyHeader = new AtomicReference<>();

        server.createContext("/v1/orders", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod()) && "/v1/orders".equals(exchange.getRequestURI().getPath())) {
                idempotencyHeader.set(exchange.getRequestHeaders().getFirst("X-Idempotency-Key"));
                responderJson(exchange, 201, """
                        {
                          "id": "ORD-HTTP-01",
                          "status": "created",
                          "status_detail": "waiting_terminal",
                          "external_reference": "VHL-01",
                          "transactions": {
                            "payments": [
                              {"id": "PAY-HTTP-01", "status": "created", "amount": "55.90"}
                            ]
                          }
                        }
                        """);
            } else if ("GET".equals(exchange.getRequestMethod()) && "/v1/orders/ORD-HTTP-01".equals(exchange.getRequestURI().getPath())) {
                responderJson(exchange, 200, """
                        {
                          "id": "ORD-HTTP-01",
                          "status": "processed",
                          "status_detail": "accredited",
                          "external_reference": "VHL-01",
                          "total_amount": "55.90",
                          "transactions": {
                            "payments": [
                              {"id": "PAY-HTTP-01", "status": "accredited", "amount": "55.90"}
                            ]
                          }
                        }
                        """);
            } else if ("POST".equals(exchange.getRequestMethod()) && "/v1/orders/ORD-HTTP-01/cancel".equals(exchange.getRequestURI().getPath())) {
                responderJson(exchange, 200, """
                        {"id":"ORD-HTTP-01","status":"canceled","status_detail":"canceled_by_user","external_reference":"VHL-01","total_amount":"55.90"}
                        """);
            } else {
                responderJson(exchange, 404, "{\"message\":\"Order não encontrada\"}");
            }
        });

        server.createContext("/terminals/v1/list", exchange -> responderJson(exchange, 200, """
                {
                  "data": {
                    "terminals": [
                      {
                        "id": "PAX_A910__01",
                        "pos_id": "10",
                        "store_id": "20",
                        "external_pos_id": "CAIXA-01",
                        "operating_mode": "PDV"
                      }
                    ]
                  }
                }
                """));

        server.createContext("/terminals/v1/setup", exchange -> responderJson(exchange, 200, """
                {
                  "terminals": [
                    {
                      "id": "PAX_A910__01",
                      "pos_id": "10",
                      "store_id": "20",
                      "external_pos_id": "CAIXA-01",
                      "operating_mode": "STANDALONE"
                    }
                  ]
                }
                """));

        HttpMercadoPagoPointClient client = criarClient(3000L, baseUrl);

        OrderPointResultado criada = client.criarOrderPoint(new CriarOrderPointCommand(
                "idem-01",
                "VHL-01",
                new BigDecimal("55.90"),
                "PAX_A910__01",
                "credit_card",
                2,
                "PT5M",
                "Pedido 1"
        ));
        assertThat(criada.orderId()).isEqualTo("ORD-HTTP-01");
        assertThat(criada.totalAmount()).isEqualByComparingTo(new BigDecimal("55.90"));
        assertThat(authHeader.get()).isEqualTo("Bearer APP_USR-TOKEN-TESTE");
        assertThat(idempotencyHeader.get()).isEqualTo("idem-01");

        OrderPointResultado consultada = client.consultarOrder("ORD-HTTP-01");
        assertThat(consultada.status()).isEqualTo("processed");
        assertThat(consultada.paymentStatus()).isEqualTo("accredited");

        OrderPointResultado cancelada = client.cancelarOrder("ORD-HTTP-01", "cancel-01");
        assertThat(cancelada.status()).isEqualTo("canceled");

        List<TerminalMercadoPagoDto> terminais = client.listarTerminais();
        assertThat(terminais).hasSize(1);
        assertThat(terminais.get(0).operatingMode()).isEqualTo("PDV");

        TerminalMercadoPagoDto atualizado = client.atualizarModoOperacaoTerminal(
                "PAX_A910__01",
                ModoOperacaoTerminalPoint.STANDALONE
        );
        assertThat(atualizado.operatingMode()).isEqualTo("STANDALONE");
    }

    @Test
    void mapeiaErrosHttp4xx5xxETimeoutParaIntegracaoMercadoPagoException() {
        AtomicInteger modoErro = new AtomicInteger(404);
        server.createContext("/v1/orders", exchange -> {
            int code = modoErro.get();
            if (code == 999) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                responderJson(exchange, 200, "{}");
            } else if (code == 422) {
                responderJson(exchange, 422, "{\"errors\":[{\"message\":\"Valor inválido\"}]}");
            } else {
                responderJson(exchange, code, "{\"message\":\"Erro MP " + code + "\"}");
            }
        });

        HttpMercadoPagoPointClient client = criarClient(150L, baseUrl);

        assertThatThrownBy(() -> client.consultarOrder("inexistente"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(mp.isErroTransitorio()).isFalse();
                });

        modoErro.set(409);
        assertThatThrownBy(() -> client.consultarOrder("conflito"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> assertThat(((IntegracaoMercadoPagoException) ex).getHttpStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        modoErro.set(422);
        assertThatThrownBy(() -> client.consultarOrder("invalido"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .hasMessageContaining("Valor inválido");

        modoErro.set(500);
        assertThatThrownBy(() -> client.consultarOrder("erro-500"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(mp.isErroTransitorio()).isTrue();
                });

        modoErro.set(999);
        assertThatThrownBy(() -> client.consultarOrder("timeout"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(mp.isErroTransitorio()).isTrue();
                });

        HttpMercadoPagoPointClient clientPortaFechada = criarClient(200L, "http://127.0.0.1:1");
        assertThatThrownBy(() -> clientPortaFechada.consultarOrder("rede"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> assertThat(((IntegracaoMercadoPagoException) ex).isErroTransitorio()).isTrue());
    }

    @Test
    void enviaInstallmentsCostConformePoliticaDeJuros() {
        AtomicReference<String> ultimoBody = new AtomicReference<>();
        server.createContext("/v1/orders", exchange -> {
            ultimoBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responderJson(exchange, 201, """
                    {"id":"ORD-COST-01","status":"created","external_reference":"VHL-COST","total_amount":"100.00"}
                    """);
        });

        HttpMercadoPagoPointClient client = criarClient(3000L, baseUrl);

        client.criarOrderPoint(new CriarOrderPointCommand(
                "idem-seller",
                "VHL-COST",
                new BigDecimal("100.00"),
                "PAX_A910__01",
                "credit_card",
                3,
                "PT5M",
                "Pedido Seller"
        ));
        assertThat(ultimoBody.get()).contains("\"installments_cost\":\"seller\"");

        client.criarOrderPoint(new CriarOrderPointCommand(
                "idem-buyer",
                "VHL-COST",
                new BigDecimal("100.00"),
                "PAX_A910__01",
                "credit_card",
                3,
                "PT5M",
                "Pedido Buyer",
                "buyer"
        ));
        assertThat(ultimoBody.get()).contains("\"installments_cost\":\"buyer\"");
    }

    private void responderJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
