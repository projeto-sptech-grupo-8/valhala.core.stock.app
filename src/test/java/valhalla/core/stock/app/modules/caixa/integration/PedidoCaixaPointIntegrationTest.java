package valhalla.core.stock.app.modules.caixa.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.point.client.MockMercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ReservaEstoqueEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.point.repository.EventoWebhookPagamentoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ReservaEstoqueRepository;
import valhalla.core.stock.app.modules.caixa.point.service.RateLimiterCaixaService;
import valhalla.core.stock.app.modules.caixa.point.service.ReconciliacaoPointScheduler;
import valhalla.core.stock.app.modules.caixa.point.service.TransicaoPagamentoPointService;
import valhalla.core.stock.app.modules.caixa.point.service.ValidadorAssinaturaWebhookMercadoPago;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.mercado-pago.point.modo=MOCK",
        "app.mercado-pago.point.webhook-secret=valhalla-point-webhook-secret-test",
        "app.mercado-pago.point.webhook-validar-assinatura=true",
        "app.mercado-pago.point.reconciliacao-ativa-habilitada=false",
        "app.mercado-pago.point.rate-limit-criacao-por-minuto=30",
        "app.mercado-pago.point.rate-limit-webhook-por-minuto=40"
})
class PedidoCaixaPointIntegrationTest extends CaixaIntegrationTestSupport {

    private static final String[] PERMISSOES_ATENDENTE = {
            "CAIXA_OPERAR",
            "CAIXA_VISUALIZAR",
            "CAIXA_SANGRIA",
            "CAIXA_SUPRIMENTO"
    };

    @Autowired
    private MockMercadoPagoPointClient mockPointClient;

    @Autowired
    private ReservaEstoqueRepository reservaEstoqueRepository;

    @Autowired
    private EventoWebhookPagamentoRepository eventoWebhookRepository;

    @Autowired
    private PagamentoPedidoRepository pagamentoPedidoRepository;

    @Autowired
    private EstoqueRepository estoqueRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;

    @Autowired
    private ValidadorAssinaturaWebhookMercadoPago validadorAssinatura;

    @Autowired
    private RateLimiterCaixaService rateLimiterCaixaService;

    @Autowired
    private ReconciliacaoPointScheduler reconciliacaoScheduler;

    @Autowired
    private TransicaoPagamentoPointService transicaoPagamentoPointService;

    @BeforeEach
    void prepararAmbientePoint() {
        mockPointClient.limparEstadoMock();
        rateLimiterCaixaService.limpar();
    }

    @AfterEach
    void limparTabelasFase2() {
        eventoWebhookRepository.deleteAllInBatch();
        reservaEstoqueRepository.deleteAllInBatch();
        mockPointClient.limparEstadoMock();
        rateLimiterCaixaService.limpar();
    }

    @Test
    void fluxoCompletoPointReservaConcorrenciaWebhookHmacEIdempotencia() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.point1@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.point1@meraki.com", PERMISSOES_ATENDENTE);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Point 01");
        String terminalUuid = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__SMARTPOS_MOCK_01", "Smart 2 Balcão");
        habilitarPointNaConfiguracao(true);

        abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity cat = categoria("Bebidas Point");
        ProdutoEntity cerveja = produtoPadrao(
                "Heineken Long Neck", cat, false, "6.00", "15.00", null, "2.000"
        );
        ProdutoEntity gin = produtoPadrao(
                "Gin Bombay 1L", cat, true, "120.00", "220.00", "1000.000", "1000.000"
        );
        ProdutoEntity drink = drinkComReceita(
                "Gin Tônica Point", cat, "30.00", gin, "100.000", UnidadeConsumoDrink.ML
        );

        // Pedido 1 consome as últimas 2 Heinekens (R$ 30) + 1 drink (R$ 30) = R$ 60,00
        String pedido1Id = criarPedido(operador, "Mesa 01", cerveja.getId(), 2, drink.getId(), 1);

        // Envia cobrança para a Point
        MvcResult pagamentoRes = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedido1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "CREDITO",
                                  "valor": 60.00,
                                  "parcelas": 2,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-happy-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_MAQUINA"))
                .andExpect(jsonPath("$.pagamentos[0].status").value("PENDENTE_NA_POINT"))
                .andExpect(jsonPath("$.pagamentos[0].orderExternaId").isNotEmpty())
                .andExpect(jsonPath("$.pagamentos[0].terminalId").value("PAX_A910__SMARTPOS_MOCK_01"))
                .andReturn();

        String orderExternaId = JsonPath.read(pagamentoRes.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        // Reservas ativas criadas (1 para cerveja + 1 para insumo do drink)
        List<ReservaEstoqueEntity> reservasAtivas = reservaEstoqueRepository.findByPedidoIdOrderByIdAsc(UUID.fromString(pedido1Id));
        assertThat(reservasAtivas).hasSize(2);
        assertThat(reservasAtivas).allMatch(r -> r.getStatus() == StatusReservaEstoque.ATIVA);

        // Pedido 2 concorrente tentando comprar a última Heineken já reservada deve ser bloqueado (409)
        mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA",
                                  "identificadorAtendimento": "Mesa 02",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(cerveja.getId())))
                .andExpect(status().isConflict());

        // Pedido 2B com drink que ainda possui insumo disponível é criado, mas bloqueia no terminal ocupado (409)
        String pedido2Id = criarPedidoSimples(operador, "Mesa 02", drink.getId(), 1);
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedido2Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "PIX",
                                  "valor": 30.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "terminal-ocupado-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isConflict());

        // Webhook sem assinatura ou adulterado -> 401
        String webhookPayload = """
                {"id":"evt-happy-01","type":"order","action":"order.processed","data":{"id":"%s"}}
                """.formatted(orderExternaId).trim();

        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .header("x-request-id", "req-adulterado")
                        .header("x-signature", "ts=" + Instant.now().getEpochSecond() + ",v1=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isUnauthorized());

        // Simula aprovação na maquininha e dispara webhook assinado
        mockPointClient.atualizarStatusMock(orderExternaId, "processed", "accredited", "accredited");
        String requestId = "req-happy-01";
        String xSignature = validadorAssinatura.gerarAssinatura(orderExternaId, requestId, Instant.now().getEpochSecond());

        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .queryParam("data.id", orderExternaId)
                        .queryParam("type", "order")
                        .header("x-request-id", requestId)
                        .header("x-signature", xSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSADO"));

        // Reenvios idênticos do mesmo webhook não duplicam baixa nem quebram
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/webhooks/mercado-pago/point")
                            .queryParam("data.id", orderExternaId)
                            .header("x-request-id", requestId)
                            .header("x-signature", xSignature)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(webhookPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PROCESSADO"));
        }

        // Pedido concluído e reservas convertidas
        mockMvc.perform(get("/caixa/pedidos/{id}", pedido1Id)
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.pagamentos[0].status").value("APROVADO"));

        List<ReservaEstoqueEntity> reservasFinais = reservaEstoqueRepository.findByPedidoIdOrderByIdAsc(UUID.fromString(pedido1Id));
        assertThat(reservasFinais).allMatch(r -> r.getStatus() == StatusReservaEstoque.EFETIVADA);

        EstoqueEntity estoqueCerveja = estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow();
        EstoqueEntity estoqueGin = estoqueRepository.findByProdutoId(gin.getId()).orElseThrow();
        assertThat(estoqueCerveja.getQuantidadeAtual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(estoqueGin.getQuantidadeAtual()).isEqualByComparingTo(new BigDecimal("900.000"));

        List<MovimentacaoEstoqueEntity> movs = movimentacaoEstoqueRepository
                .findAllByEstoqueProdutoEstabelecimentoIdOrderByOcorridoEmDesc(estabelecimento.getId());
        assertThat(movs).filteredOn(m -> "WEBHOOK".equals(m.getOrigem())).hasSize(2);
    }

    @Test
    void recusaECancelamentoLiberamReservaETerminalETwoPhaseCompensaFalha() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.point2@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.point2@meraki.com", PERMISSOES_ATENDENTE);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Point 02");
        String terminalUuid = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__SMARTPOS_MOCK_01", "Smart 2");
        habilitarPointNaConfiguracao(true);

        abrirSessao(operador, caixa.getId(), "50.00");

        CategoriaEntity cat = categoria("Cervejas Especiais");
        ProdutoEntity ipa = produtoPadrao("IPA 500ml", cat, false, "10.00", "20.00", null, "1.000");

        String pedidoId = criarPedidoSimples(operador, "Mesa 05", ipa.getId(), 1);

        // 1) Falha HTTP não transitória ao criar Order -> Two-Phase compensa reserva e marca RECUSADO
        mockPointClient.configurarProximaFalhaCriacao(new IntegracaoMercadoPagoException(
                "Terminal desligado", HttpStatus.BAD_GATEWAY, false
        ));

        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DEBITO",
                                  "valor": 20.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-falha-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isBadGateway());

        List<ReservaEstoqueEntity> aposFalha = reservaEstoqueRepository.findByPedidoIdOrderByIdAsc(UUID.fromString(pedidoId));
        assertThat(aposFalha).allMatch(r -> r.getStatus() == StatusReservaEstoque.LIBERADA);

        // 2) Novo envio funciona porque terminal e estoque foram liberados -> Simulador recusa na Point
        MvcResult envio2 = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DEBITO",
                                  "valor": 20.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-recusa-02"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andReturn();

        String orderId2 = JsonPath.read(envio2.getResponse().getContentAsString(), "$.pagamentos[1].orderExternaId");

        // Usa SimuladorPointDevController (ativo via @Profile("test")) para simular recusa
        mockMvc.perform(post("/dev/mercado-pago/point/orders/{orderId}/simular", orderId2)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "failed",
                                  "statusDetail": "cc_rejected_insufficient_amount",
                                  "paymentStatus": "rejected",
                                  "dispararWebhook": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.webhookProcessado").value("PROCESSADO"));

        // 3) Novo envio e cancelamento remoto via controller
        MvcResult envio3 = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "PIX",
                                  "valor": 20.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-cancela-03"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andReturn();

        String pagamentoId3 = JsonPath.read(envio3.getResponse().getContentAsString(), "$.pagamentos[2].id");

        mockMvc.perform(post("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/cancelamento-point", pedidoId, pagamentoId3)
                        .cookie(operador.accessToken())
                        .with(csrf(operador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.pagamentos[2].status").value("CANCELADO"));

        // Estoque continuou intacto em 1.000 unidade
        assertThat(estoqueRepository.findByProdutoId(ipa.getId()).orElseThrow().getQuantidadeAtual())
                .isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void timeoutTransitorioGeraConciliacaoSchedulerReconciliaEExpiraReservasEGereTerminais() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.point3@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.point3@meraki.com", PERMISSOES_ATENDENTE);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Point 03");
        String terminalUuid = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__SMARTPOS_MOCK_01", "Smart 2");
        habilitarPointNaConfiguracao(true);

        // Lista terminais MP e alterna modo de operação
        mockMvc.perform(get("/caixa/terminais/mercado-pago")
                        .cookie(gerente.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("PAX_A910__SMARTPOS_MOCK_01"));

        mockMvc.perform(patch("/caixa/terminais/{id}/modo-operacao", terminalUuid)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modoOperacao\":\"PDV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoOperacao").value("PDV"));

        abrirSessao(operador, caixa.getId(), "50.00");

        CategoriaEntity cat = categoria("Destilados");
        ProdutoEntity whisky = produtoPadrao("Whisky 12 Anos", cat, false, "100.00", "250.00", null, "5.000");

        // Cenário A: Timeout transitório -> PENDENTE_CONCILIACAO + rejeição manual libera reserva
        String pedidoTimeoutId = criarPedidoSimples(operador, "Mesa 10", whisky.getId(), 1);
        mockPointClient.configurarProximaFalhaCriacao(new IntegracaoMercadoPagoException(
                "Timeout na API MP", HttpStatus.GATEWAY_TIMEOUT, true
        ));

        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoTimeoutId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "CREDITO",
                                  "valor": 250.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-timeout-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isGatewayTimeout());

        MvcResult resTimeout = mockMvc.perform(get("/caixa/pedidos/{id}", pedidoTimeoutId)
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos[0].status").value("PENDENTE_CONCILIACAO"))
                .andReturn();
        String pagTimeoutId = JsonPath.read(resTimeout.getResponse().getContentAsString(), "$.pagamentos[0].id");

        mockMvc.perform(post("/caixa/pedidos/{p}/pagamentos/{pag}/conciliacao", pedidoTimeoutId, pagTimeoutId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprovado\":false,\"motivo\":\"Comunicação interrompida antes de gerar Order\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos[0].status").value("CANCELADO"));

        // Cenário B: Reconciliação ativa pelo Scheduler aprova pagamento sem webhook
        String pedidoReconciliaId = criarPedidoSimples(operador, "Mesa 11", whisky.getId(), 1);
        MvcResult resRec = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoReconciliaId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "PIX",
                                  "valor": 250.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-reconcilia-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andReturn();
        String pagRecId = JsonPath.read(resRec.getResponse().getContentAsString(), "$.pagamentos[0].id");
        String orderRecId = JsonPath.read(resRec.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        // Simula pagamento antigo (> 20s) já pago na maquininha sem webhook entregue
        PagamentoPedidoEntity entityRec = pagamentoPedidoRepository.findById(UUID.fromString(pagRecId)).orElseThrow();
        entityRec.setSolicitadoEm(LocalDateTime.now().minusMinutes(1));
        pagamentoPedidoRepository.saveAndFlush(entityRec);
        mockPointClient.atualizarStatusMock(orderRecId, "processed", "accredited", "accredited");

        int reconciliados = reconciliacaoScheduler.reconciliarPagamentosPendentes();
        assertThat(reconciliados).isEqualTo(1);
        assertThat(pagamentoPedidoRepository.findById(UUID.fromString(pagRecId)).orElseThrow().getStatus())
                .isEqualTo(StatusPagamentoPedido.APROVADO);

        // Cenário C: Expiração de reserva vencida pelo Scheduler
        String pedidoExpId = criarPedidoSimples(operador, "Mesa 12", whisky.getId(), 1);
        MvcResult resExp = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoExpId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DEBITO",
                                  "valor": 250.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-expira-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andReturn();
        String pagExpId = JsonPath.read(resExp.getResponse().getContentAsString(), "$.pagamentos[0].id");

        List<ReservaEstoqueEntity> resVencidas = reservaEstoqueRepository.findByPedidoIdOrderByIdAsc(UUID.fromString(pedidoExpId));
        resVencidas.forEach(r -> r.setExpiraEm(LocalDateTime.now().minusMinutes(1)));
        reservaEstoqueRepository.saveAllAndFlush(resVencidas);

        int expirados = reconciliacaoScheduler.expirarReservasVencidas();
        assertThat(expirados).isEqualTo(1);
        assertThat(pagamentoPedidoRepository.findById(UUID.fromString(pagExpId)).orElseThrow().getStatus())
                .isEqualTo(StatusPagamentoPedido.EXPIRADO);
        assertThat(reservaEstoqueRepository.findByPedidoIdOrderByIdAsc(UUID.fromString(pedidoExpId)))
                .allMatch(r -> r.getStatus() == StatusReservaEstoque.EXPIRADA);
    }

    @Test
    void sincronizacaoWebhookIgnoradoContingenciaManualERateLimit429() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.point4@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.point4@meraki.com", PERMISSOES_ATENDENTE);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Point 04");
        String terminalUuid = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__SMARTPOS_MOCK_01", "Smart 2");
        habilitarPointNaConfiguracao(true);

        abrirSessao(operador, caixa.getId(), "50.00");

        CategoriaEntity cat = categoria("Vinhos");
        ProdutoEntity vinho = produtoPadrao("Malbec Reserva", cat, false, "40.00", "90.00", null, "5.000");

        // 1) Sincronização manual de pagamento Point (at_terminal -> action_required -> processed)
        String pedidoSyncId = criarPedidoSimples(operador, "Mesa 20", vinho.getId(), 1);
        MvcResult cobSync = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoSyncId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "CREDITO",
                                  "valor": 90.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-sync-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andReturn();
        String pagSyncId = JsonPath.read(cobSync.getResponse().getContentAsString(), "$.pagamentos[0].id");
        String orderSyncId = JsonPath.read(cobSync.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        mockPointClient.atualizarStatusMock(orderSyncId, "at_terminal", "waiting_card", "created");
        mockMvc.perform(post("/caixa/pedidos/{p}/pagamentos/{pag}/sincronizar", pedidoSyncId, pagSyncId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos[0].status").value("EM_TERMINAL"));

        mockPointClient.atualizarStatusMock(orderSyncId, "action_required", "verify_on_device", "pending");
        mockMvc.perform(post("/caixa/pedidos/{p}/pagamentos/{pag}/sincronizar", pedidoSyncId, pagSyncId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos[0].status").value("ACAO_NECESSARIA"));

        mockPointClient.atualizarStatusMock(orderSyncId, "processed", "accredited", "accredited");
        mockMvc.perform(post("/caixa/pedidos/{p}/pagamentos/{pag}/sincronizar", pedidoSyncId, pagSyncId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos[0].status").value("APROVADO"))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        // Evento atrasado de cancelamento após aprovação nunca reverte venda liquidada
        mockPointClient.atualizarStatusMock(orderSyncId, "canceled", "late_event", "cancelled");
        String lateReq = "req-late-01";
        String lateSig = validadorAssinatura.gerarAssinatura(orderSyncId, lateReq, Instant.now().getEpochSecond());
        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .queryParam("data.id", orderSyncId)
                        .header("x-request-id", lateReq)
                        .header("x-signature", lateSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"evt-late-01","type":"order","action":"order.canceled","data":{"id":"%s"}}
                                """.formatted(orderSyncId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSADO"));
        assertThat(pagamentoPedidoRepository.findById(UUID.fromString(pagSyncId)).orElseThrow().getStatus())
                .isEqualTo(StatusPagamentoPedido.APROVADO);

        // 2) Webhook IGNORADO para tipo não-order e para order desconhecida
        String ignReq1 = "req-ign-01";
        String ignSig1 = validadorAssinatura.gerarAssinatura("99999", ignReq1, Instant.now().getEpochSecond());
        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .queryParam("data.id", "99999")
                        .header("x-request-id", ignReq1)
                        .header("x-signature", ignSig1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt-ign-01\",\"type\":\"merchant_order\",\"data\":{\"id\":\"99999\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORADO"));

        // 3) Contingência manual mesmo com Point habilitada
        String pedidoManualId = criarPedidoSimples(operador, "Mesa 21", vinho.getId(), 1);
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoManualId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "PIX",
                                  "valor": 90.00,
                                  "contingenciaManual": true,
                                  "chaveIdempotencia": "manual-contingencia-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos[0].status").value("PENDENTE_CONCILIACAO"));

        // 4) Rate Limit HTTP 429 ao exceder criação na Point no mesmo minuto
        rateLimiterCaixaService.limpar();
        for (int i = 0; i < 30; i++) {
            rateLimiterCaixaService.verificarLimiteCriacaoPoint(operador.usuarioId());
        }
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoManualId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DEBITO",
                                  "valor": 90.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-ratelimit-429"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isTooManyRequests());

        // 5) Terminal inexistente -> 404
        mockMvc.perform(patch("/caixa/terminais/{id}/modo-operacao", UUID.randomUUID())
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modoOperacao\":\"PDV\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotenciaCriacaoPointCorridaCancelamentoJaPagoEWebhookExpiradoE404() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.point5@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.point5@meraki.com", PERMISSOES_ATENDENTE);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Point 05");
        String terminalUuid = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__SMARTPOS_MOCK_01", "Smart 2");
        habilitarPointNaConfiguracao(true);
        abrirSessao(operador, caixa.getId(), "50.00");

        CategoriaEntity cat = categoria("Espumantes");
        ProdutoEntity espumante = produtoPadrao("Chandon", cat, false, "60.00", "120.00", null, "4.000");

        // 1) Idempotência ao reenviar criação com mesma chave + corrida de cancelamento com pagamento já aprovado
        String pedidoCorridaId = criarPedidoSimples(operador, "Mesa 30", espumante.getId(), 1);
        String bodyCobranca = """
                {
                  "forma": "CREDITO",
                  "valor": 120.00,
                  "terminalCaixaId": "%s",
                  "chaveIdempotencia": "point-idem-corrida-01"
                }
                """.formatted(terminalUuid);

        MvcResult cob1 = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoCorridaId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCobranca))
                .andExpect(status().isOk())
                .andReturn();
        String pagCorridaId = JsonPath.read(cob1.getResponse().getContentAsString(), "$.pagamentos[0].id");
        String orderCorridaId = JsonPath.read(cob1.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        // Reenvio idêntico retorna a mesma Order sem criar segunda cobrança
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoCorridaId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCobranca))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos.length()").value(1))
                .andExpect(jsonPath("$.pagamentos[0].orderExternaId").value(orderCorridaId));

        // Cliente paga no aparelho antes de cancelar -> cancelar-point sincroniza e aprova!
        mockPointClient.atualizarStatusMock(orderCorridaId, "processed", "accredited", "accredited");
        mockMvc.perform(post("/caixa/pedidos/{p}/pagamentos/{pag}/cancelamento-point", pedidoCorridaId, pagCorridaId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.pagamentos[0].status").value("APROVADO"));

        // 2) Webhook expired via notificação assinada + Webhook para order 404 -> IGNORADO
        String pedidoExpWhId = criarPedidoSimples(operador, "Mesa 31", espumante.getId(), 1);
        MvcResult cobExp = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoExpWhId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "PIX",
                                  "valor": 120.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-wh-exp-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andReturn();
        String orderExpWhId = JsonPath.read(cobExp.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        mockPointClient.atualizarStatusMock(orderExpWhId, "expired", "expired", "expired");
        String expReq = "req-exp-wh-01";
        String expSig = validadorAssinatura.gerarAssinatura(orderExpWhId, expReq, Instant.now().getEpochSecond());
        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .queryParam("data.id", orderExpWhId)
                        .header("x-request-id", expReq)
                        .header("x-signature", expSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"evt-exp-wh-01","type":"order","action":"order.expired","data":{"id":"%s"}}
                                """.formatted(orderExpWhId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSADO"));

        String missReq = "req-miss-404";
        String missSig = validadorAssinatura.gerarAssinatura("ORD-NAO-EXISTE", missReq, Instant.now().getEpochSecond());
        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .queryParam("data.id", "ORD-NAO-EXISTE")
                        .header("x-request-id", missReq)
                        .header("x-signature", missSig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt-miss-404\",\"type\":\"order\",\"action\":\"order.processed\",\"data\":{\"id\":\"ORD-NAO-EXISTE\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORADO"));

        // 3) Zero-Trust rejeita divergência de valor ou external_reference e garante retry idempotente
        String pedidoZtId = criarPedidoSimples(operador, "Mesa 32", espumante.getId(), 1);
        MvcResult cobZt = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoZtId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DEBITO",
                                  "valor": 120.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-zt-01"
                                }
                                """.formatted(terminalUuid)))
                .andExpect(status().isOk())
                .andReturn();
        String pagZtId = JsonPath.read(cobZt.getResponse().getContentAsString(), "$.pagamentos[0].id");
        String orderZtId = JsonPath.read(cobZt.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        assertThatThrownBy(() -> transicaoPagamentoPointService.aplicarResultadoOrder(
                UUID.fromString(pagZtId),
                new OrderPointResultado(orderZtId, "processed", "accredited", "PAY-ZT", "accredited", "REF-ERRADA", new BigDecimal("120.00"), "{}"),
                OrigemAuditoriaCaixa.WEBHOOK,
                null
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("external_reference");

        assertThatThrownBy(() -> transicaoPagamentoPointService.aplicarResultadoOrder(
                UUID.fromString(pagZtId),
                new OrderPointResultado(orderZtId, "processed", "accredited", "PAY-ZT", "accredited", null, new BigDecimal("1.00"), "{}"),
                OrigemAuditoriaCaixa.WEBHOOK,
                null
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("Divergência de valor");

        // Simulador sem disparar webhook + sincronização em pagamento manual bloqueada (409)
        mockMvc.perform(post("/dev/mercado-pago/point/orders/{orderId}/simular", orderZtId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"created\",\"dispararWebhook\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookProcessado").value("NAO_DISPARADO"));

        // Pagamento parcial + sincronização inválida em dinheiro
        String pedidoDinId = criarPedidoSimples(operador, "Mesa 33", espumante.getId(), 1);
        MvcResult cobDin = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoDinId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forma\":\"DINHEIRO\",\"valor\":120.00,\"valorRecebido\":150.00}"))
                .andExpect(status().isOk())
                .andReturn();
        String pagDinId = JsonPath.read(cobDin.getResponse().getContentAsString(), "$.pagamentos[0].id");

        mockMvc.perform(post("/caixa/pedidos/{p}/pagamentos/{pag}/sincronizar", pedidoDinId, pagDinId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/caixa/pedidos/{p}/pagamentos/{pag}/cancelamento-point", pedidoDinId, pagDinId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador)))
                .andExpect(status().isConflict());
    }

    private String abrirSessao(UsuarioAutenticado usuario, UUID caixaId, String fundoTroco) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/caixa/sessoes")
                        .cookie(usuario.accessToken())
                        .with(csrf(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caixaId\":\"%s\",\"fundoTroco\":%s}".formatted(caixaId, fundoTroco)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    private String cadastrarTerminal(UsuarioAutenticado gerente, UUID caixaId, String terminalId, String nome) throws Exception {
        MvcResult res = mockMvc.perform(post("/caixa/caixas/{caixaId}/terminais", caixaId)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "terminalId": "%s",
                                  "nome": "%s"
                                }
                                """.formatted(terminalId, nome)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private void habilitarPointNaConfiguracao(boolean permitirManual) {
        ConfiguracaoCaixaEntity config = configuracao(estabelecimento);
        config.setPointHabilitado(true);
        config.setPermitirPagamentoManual(permitirManual);
        config.setParcelamentoHabilitado(true);
        config.setParcelasMinimas(1);
        config.setParcelasMaximas(3);
        configuracaoCaixaRepository.saveAndFlush(config);
    }

    private String criarPedido(
            UsuarioAutenticado operador,
            String atendimento,
            UUID p1,
            int q1,
            UUID p2,
            int q2
    ) throws Exception {
        MvcResult res = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA",
                                  "identificadorAtendimento": "%s",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": %d},
                                    {"produtoId": "%s", "quantidade": %d}
                                  ]
                                }
                                """.formatted(atendimento, p1, q1, p2, q2)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private String criarPedidoSimples(
            UsuarioAutenticado operador,
            String atendimento,
            UUID p1,
            int q1
    ) throws Exception {
        MvcResult res = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA",
                                  "identificadorAtendimento": "%s",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": %d}
                                  ]
                                }
                                """.formatted(atendimento, p1, q1)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void fluxoPointBloqueiaSaldoVencidoNaReservaConsomeFefoNoWebhookELiberaSemAlterarLotesQuandoRecusado()
            throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.point.fefo@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.point.fefo@meraki.com", PERMISSOES_ATENDENTE);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Point FEFO");
        String terminalUuid1 = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__SMARTPOS_FEFO_01", "Smart FEFO 1");
        String terminalUuid2 = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__SMARTPOS_FEFO_02", "Smart FEFO 2");
        habilitarPointNaConfiguracao(true);
        abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity cat = categoria("Bebidas FEFO Point");
        ProdutoEntity cerveja = produtoPadrao("Eisenbahn 600ml", cat, false, "7.00", "15.00", null, "0.000");
        LocalDate hoje = LocalDate.now();
        lote(cerveja, "CERV-VENCIDA", hoje.minusDays(1), "2.000");
        lote(cerveja, "CERV-A", hoje.plusDays(3), "1.000");
        lote(cerveja, "CERV-B", hoje.plusDays(20), "3.000");

        ProdutoEntity vodka = produtoPadrao("Vodka 1L", cat, true, "80.00", "150.00", "1000.000", "0.000");
        lote(vodka, "VOD-A", hoje.plusDays(5), "100.000");
        lote(vodka, "VOD-B", hoje.plusDays(60), "500.000");
        ProdutoEntity drink = drinkComReceita("Caipiroska", cat, "25.00", vodka, "100.000", UnidadeConsumoDrink.ML);

        // Pedido 1 (R$ 45 + R$ 50 = R$ 95) e Pedido 2 concorrente (2 cervejas) criados antes da reserva
        String pedido1Id = criarPedido(operador, "Mesa FEFO 01", cerveja.getId(), 3, drink.getId(), 2);
        String pedido2Id = criarPedidoSimples(operador, "Mesa FEFO 02", cerveja.getId(), 2);

        // 1) Envia Pedido 1 para Point -> reserva 3 cervejas e 200ml de vodka sem debitar os lotes ainda
        MvcResult pag1 = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedido1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "PIX",
                                  "valor": 95.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-fefo-01"
                                }
                                """.formatted(terminalUuid1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_MAQUINA"))
                .andReturn();
        String orderId1 = JsonPath.read(pag1.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        EstoqueEntity estoqueCervejaReservado = estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow();
        assertThat(estoqueCervejaReservado.getQuantidadeReservada()).isEqualByComparingTo("3.000");
        assertThat(loteEstoqueRepository.findAllByEstoqueId(estoqueCervejaReservado.getId()))
                .extracting(LoteEstoqueEntity::getCodigo, LoteEstoqueEntity::getQuantidadeAtual)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CERV-VENCIDA", new BigDecimal("2.000")),
                        org.assertj.core.groups.Tuple.tuple("CERV-A", new BigDecimal("1.000")),
                        org.assertj.core.groups.Tuple.tuple("CERV-B", new BigDecimal("3.000"))
                );

        // 2) Pedido 2 tentando reservar 2 cervejas no terminal 2 falha com 409 porque restam apenas 1 não vencida
        // (saldo físico livre é 6 - 3 = 3, mas 2 são vencidas)
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedido2Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DEBITO",
                                  "valor": 30.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-fefo-bloqueio-02"
                                }
                                """.formatted(terminalUuid2)))
                .andExpect(status().isConflict());

        // 3) Webhook aprova Pedido 1 -> efetiva reservas debitando lotes por FEFO e gravando movimentacao_estoque_lote
        mockPointClient.atualizarStatusMock(orderId1, "processed", "accredited", "accredited");
        String requestId = "req-fefo-01";
        String xSignature = validadorAssinatura.gerarAssinatura(orderId1, requestId, Instant.now().getEpochSecond());
        mockMvc.perform(post("/webhooks/mercado-pago/point")
                        .queryParam("data.id", orderId1)
                        .queryParam("type", "order")
                        .header("x-request-id", requestId)
                        .header("x-signature", xSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"evt-fefo-01","type":"order","action":"order.processed","data":{"id":"%s"}}
                                """.formatted(orderId1).trim()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSADO"));

        EstoqueEntity estoqueCervejaFinal = estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow();
        EstoqueEntity estoqueVodkaFinal = estoqueRepository.findByProdutoId(vodka.getId()).orElseThrow();
        assertThat(estoqueCervejaFinal.getQuantidadeReservada()).isEqualByComparingTo("0.000");
        assertThat(estoqueCervejaFinal.getQuantidadeAtual()).isEqualByComparingTo("3.000");
        assertThat(estoqueVodkaFinal.getQuantidadeAtual()).isEqualByComparingTo("400.000");

        assertThat(loteEstoqueRepository.findAllByEstoqueId(estoqueCervejaFinal.getId()))
                .extracting(LoteEstoqueEntity::getCodigo, LoteEstoqueEntity::getQuantidadeAtual)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CERV-VENCIDA", new BigDecimal("2.000")),
                        org.assertj.core.groups.Tuple.tuple("CERV-A", new BigDecimal("0.000")),
                        org.assertj.core.groups.Tuple.tuple("CERV-B", new BigDecimal("1.000"))
                );
        assertThat(loteEstoqueRepository.findAllByEstoqueId(estoqueVodkaFinal.getId()))
                .satisfiesExactlyInAnyOrder(
                        l -> {
                            assertThat(l.getCodigo()).isEqualTo("VOD-A");
                            assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("0.000");
                        },
                        l -> {
                            assertThat(l.getCodigo()).isEqualTo("VOD-B");
                            assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("400.000");
                        }
                );

        List<MovimentacaoEstoqueEntity> movsWebhook = movimentacaoEstoqueRepository
                .findAllByEstoqueProdutoEstabelecimentoIdOrderByOcorridoEmDesc(estabelecimento.getId())
                .stream()
                .filter(m -> "WEBHOOK".equals(m.getOrigem()))
                .toList();
        assertThat(movsWebhook).hasSize(2);
        List<Long> idsMovsWebhook = movsWebhook.stream().map(MovimentacaoEstoqueEntity::getId).toList();
        List<MovimentacaoLoteEntity> vinculosWebhook = movimentacaoLoteRepository
                .findAllByMovimentacaoIdInOrderByIdAsc(idsMovsWebhook);
        assertThat(vinculosWebhook).hasSize(4);

        // 4) Pedido 3 (1 cerveja restante de CERV-B) tem recusa na Point -> libera reserva sem debitar CERV-B
        String pedido3Id = criarPedidoSimples(operador, "Mesa FEFO 03", cerveja.getId(), 1);
        MvcResult pag3 = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedido3Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "CREDITO",
                                  "valor": 15.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "point-fefo-recusa-03"
                                }
                                """.formatted(terminalUuid1)))
                .andExpect(status().isOk())
                .andReturn();
        String orderId3 = JsonPath.read(pag3.getResponse().getContentAsString(), "$.pagamentos[0].orderExternaId");

        mockMvc.perform(post("/dev/mercado-pago/point/orders/{orderId}/simular", orderId3)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "failed",
                                  "statusDetail": "cc_rejected_insufficient_amount",
                                  "paymentStatus": "rejected",
                                  "dispararWebhook": true
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow().getQuantidadeReservada())
                .isEqualByComparingTo("0.000");
        assertThat(loteEstoqueRepository.findAllByEstoqueId(estoqueCervejaFinal.getId()))
                .filteredOn(l -> "CERV-B".equals(l.getCodigo()))
                .first()
                .satisfies(l -> assertThat(l.getQuantidadeAtual()).isEqualByComparingTo("1.000"));
    }
}
