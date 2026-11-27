package valhalla.core.stock.app.modules.caixa.point.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.point.client.dto.CriarOrderPointCommand;
import valhalla.core.stock.app.modules.caixa.point.client.dto.ModoOperacaoTerminalPoint;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.client.dto.TerminalMercadoPagoDto;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.point.config.ModoMercadoPagoPoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpMercadoPagoPointClientTest {

    @Mock
    private HttpClient httpClient;

    private MercadoPagoPointProperties propriedadesPadrao() {
        return new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.HOMOLOGACAO,
                "APP_USR-TOKEN-TESTE",
                "secret-teste",
                true,
                300L,
                "https://api.mercadopago.test",
                1000L,
                2000L,
                "PT5M",
                300L,
                20L,
                15000L,
                false,
                300,
                30
        );
    }

    private HttpMercadoPagoPointClient criarClient() {
        return new HttpMercadoPagoPointClient(propriedadesPadrao(), httpClient);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> respostaHttp(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    @SuppressWarnings("unchecked")
    void criaConsultaECancelaOrderPointEGereTerminaisViaHttp() throws Exception {
        AtomicReference<HttpRequest> ultimaRequisicao = new AtomicReference<>();

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest req = invocation.getArgument(0);
                    ultimaRequisicao.set(req);
                    String path = req.uri().getPath();
                    String method = req.method();
                    if ("POST".equals(method) && "/v1/orders".equals(path)) {
                        return respostaHttp(201, """
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
                    }
                    if ("GET".equals(method) && "/v1/orders/ORD-HTTP-01".equals(path)) {
                        return respostaHttp(200, """
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
                    }
                    if ("POST".equals(method) && "/v1/orders/ORD-HTTP-01/cancel".equals(path)) {
                        return respostaHttp(200, """
                                {"id":"ORD-HTTP-01","status":"canceled","status_detail":"canceled_by_user","external_reference":"VHL-01","total_amount":"55.90"}
                                """);
                    }
                    if ("POST".equals(method) && "/v1/orders/ORD-HTTP-01/refund".equals(path)) {
                        return respostaHttp(200, """
                                {"id":"ORD-HTTP-01","status":"refunded","status_detail":"refunded","external_reference":"VHL-01","total_amount":"55.90"}
                                """);
                    }
                    if ("GET".equals(method) && "/terminals/v1/list".equals(path)) {
                        return respostaHttp(200, """
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
                                """);
                    }
                    if ("PATCH".equals(method) && "/terminals/v1/setup".equals(path)) {
                        return respostaHttp(200, """
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
                                """);
                    }
                    return respostaHttp(404, "{\"message\":\"Order não encontrada\"}");
                });

        HttpMercadoPagoPointClient client = criarClient();

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
        assertThat(ultimaRequisicao.get().headers().firstValue("Authorization"))
                .contains("Bearer APP_USR-TOKEN-TESTE");
        assertThat(ultimaRequisicao.get().headers().firstValue("X-Idempotency-Key"))
                .contains("idem-01");

        OrderPointResultado consultada = client.consultarOrder("ORD-HTTP-01");
        assertThat(consultada.status()).isEqualTo("processed");
        assertThat(consultada.paymentStatus()).isEqualTo("accredited");

        OrderPointResultado cancelada = client.cancelarOrder("ORD-HTTP-01", "cancel-01");
        assertThat(cancelada.status()).isEqualTo("canceled");
        assertThat(ultimaRequisicao.get().headers().firstValue("X-Idempotency-Key"))
                .contains("cancel-01");

        OrderPointResultado canceladaSemIdemp = client.cancelarOrder("ORD-HTTP-01", " ");
        assertThat(canceladaSemIdemp.status()).isEqualTo("canceled");
        assertThat(ultimaRequisicao.get().headers().firstValue("X-Idempotency-Key")).isEmpty();

        OrderPointResultado estornada = client.estornarOrder("ORD-HTTP-01", "refund-01");
        assertThat(estornada.status()).isEqualTo("refunded");
        assertThat(ultimaRequisicao.get().headers().firstValue("X-Idempotency-Key"))
                .contains("refund-01");

        OrderPointResultado estornadaSemIdemp = client.estornarOrder("ORD-HTTP-01", null);
        assertThat(estornadaSemIdemp.status()).isEqualTo("refunded");
        assertThat(ultimaRequisicao.get().headers().firstValue("X-Idempotency-Key")).isEmpty();

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
    @SuppressWarnings("unchecked")
    void trataFormatosAlternativosECorposVaziosNasRespostas() throws Exception {
        HttpMercadoPagoPointClient client = criarClient();

        HttpResponse<String> respVazia = respostaHttp(200, "");
        HttpResponse<String> respRaiz = respostaHttp(200, """
                {"terminals":[{"id":"TERM-RAIZ","operating_mode":"PDV"}]}
                """);
        HttpResponse<String> respData = respostaHttp(200, """
                {"data":{"terminals":[{"id":"TERM-DATA","operating_mode":"STANDALONE"}]}}
                """);
        HttpResponse<String> respFallback = respostaHttp(200, "{}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(respVazia, respRaiz, respData, respFallback);

        OrderPointResultado vazia = client.consultarOrder("ORD-VAZIA");
        assertThat(vazia.orderId()).isNull();
        assertThat(vazia.totalAmount()).isNull();

        List<TerminalMercadoPagoDto> terminaisRaiz = client.listarTerminais();
        assertThat(terminaisRaiz).hasSize(1);
        assertThat(terminaisRaiz.get(0).id()).isEqualTo("TERM-RAIZ");

        TerminalMercadoPagoDto terminalViaData = client.atualizarModoOperacaoTerminal(
                "TERM-DATA",
                ModoOperacaoTerminalPoint.STANDALONE
        );
        assertThat(terminalViaData.id()).isEqualTo("TERM-DATA");

        TerminalMercadoPagoDto terminalFallback = client.atualizarModoOperacaoTerminal(
                "TERM-FALLBACK",
                ModoOperacaoTerminalPoint.PDV
        );
        assertThat(terminalFallback.id()).isEqualTo("TERM-FALLBACK");
        assertThat(terminalFallback.operatingMode()).isEqualTo("PDV");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapeiaErrosHttp4xx5xxETimeoutParaIntegracaoMercadoPagoException() throws Exception {
        HttpMercadoPagoPointClient client = criarClient();

        HttpResponse<String> r404 = respostaHttp(404, "{\"message\":\"Erro MP 404\"}");
        HttpResponse<String> r409 = respostaHttp(409, "{\"message\":\"Erro MP 409\"}");
        HttpResponse<String> r422 = respostaHttp(422, "{\"errors\":[{\"message\":\"Valor inválido\"}]}");
        HttpResponse<String> r400 = respostaHttp(400, "nao-eh-json");
        HttpResponse<String> r429 = respostaHttp(429, "{\"errors\":[{}]}");
        HttpResponse<String> r500 = respostaHttp(500, "");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r404, r409, r422, r400, r429, r500)
                .thenThrow(new HttpTimeoutException("read timed out"))
                .thenThrow(new IOException("connection refused"))
                .thenThrow(new InterruptedException("interrompida"));

        assertThatThrownBy(() -> client.consultarOrder("inexistente"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(mp.isErroTransitorio()).isFalse();
                });

        assertThatThrownBy(() -> client.consultarOrder("conflito"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> assertThat(((IntegracaoMercadoPagoException) ex).getHttpStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.consultarOrder("invalido"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .hasMessageContaining("Valor inválido");

        assertThatThrownBy(() -> client.consultarOrder("bad-request"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(mp.isErroTransitorio()).isFalse();
                    assertThat(mp.getMessage()).contains("HTTP 400");
                });

        assertThatThrownBy(() -> client.consultarOrder("rate-limit"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(mp.isErroTransitorio()).isTrue();
                });

        assertThatThrownBy(() -> client.consultarOrder("erro-500"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(mp.isErroTransitorio()).isTrue();
                });

        assertThatThrownBy(() -> client.consultarOrder("timeout"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(mp.isErroTransitorio()).isTrue();
                });

        assertThatThrownBy(() -> client.consultarOrder("rede"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                    assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(mp.isErroTransitorio()).isTrue();
                });

        try {
            assertThatThrownBy(() -> client.consultarOrder("interrompida"))
                    .isInstanceOf(IntegracaoMercadoPagoException.class)
                    .satisfies(ex -> {
                        IntegracaoMercadoPagoException mp = (IntegracaoMercadoPagoException) ex;
                        assertThat(mp.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        assertThat(mp.isErroTransitorio()).isTrue();
                    });
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void enviaInstallmentsCostConformePoliticaDeJurosEOmiteCamposOpcionais() throws Exception {
        AtomicReference<String> ultimoBody = new AtomicReference<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    ultimoBody.set(extrairBody(request));
                    return respostaHttp(201, """
                            {"id":"ORD-COST-01","status":"created","external_reference":"VHL-COST","total_amount":"100.00"}
                            """);
                });

        HttpMercadoPagoPointClient client = criarClient();

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

        client.criarOrderPoint(new CriarOrderPointCommand(
                "idem-debito",
                "VHL-DEBITO",
                new BigDecimal("40.00"),
                "PAX_A910__01",
                "debit_card",
                null,
                "PT5M",
                "  ",
                null
        ));
        assertThat(ultimoBody.get()).contains("\"default_type\":\"debit_card\"");
        assertThat(ultimoBody.get()).doesNotContain("\"description\"");
        assertThat(ultimoBody.get()).doesNotContain("\"installments_cost\"");

        client.criarOrderPoint(new CriarOrderPointCommand(
                "idem-default-installment",
                "VHL-INST",
                new BigDecimal("40.00"),
                "PAX_A910__01",
                "credit_card",
                2,
                "PT5M",
                null,
                " "
        ));
        assertThat(ultimoBody.get()).contains("\"installments_cost\":\"seller\"");
    }

    @Test
    void rejeitaOrderIdInvalidoContraPathTraversalOuInjecao() {
        HttpMercadoPagoPointClient client = criarClient();

        for (String invalido : List.of("", "   ", "../users", "ORD/1?x=1", "ORD 1", "A".repeat(101))) {
            assertThatThrownBy(() -> client.consultarOrder(invalido))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orderId");
            assertThatThrownBy(() -> client.cancelarOrder(invalido, "c1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orderId");
            assertThatThrownBy(() -> client.estornarOrder(invalido, "r1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orderId");
        }
        assertThatThrownBy(() -> client.consultarOrder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    private String extrairBody(HttpRequest request) throws InterruptedException {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        return out.toString(StandardCharsets.UTF_8);
    }
}
