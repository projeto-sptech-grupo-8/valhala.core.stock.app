package valhalla.core.stock.app.modules.caixa.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ReservaEstoqueEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ReservaEstoqueRepository;
import valhalla.core.stock.app.modules.caixa.point.client.HttpMercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.TerminalMercadoPagoDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes automatizados de homologação contra a API real do Mercado Pago Point
 * ({@code MP_POINT_MODO=HOMOLOGACAO}).
 *
 * <p>Fica desabilitado nos runs padrão (offline/H2) e é ativado explicitamente com
 * {@code -Dmp.homologacao.enabled=true}, usando as credenciais configuradas no {@code .env}
 * ou variáveis de ambiente ({@code MP_ACCESS_TOKEN}, {@code MP_WEBHOOK_SECRET}).
 */
@Tag("homologacao")
@EnabledIfSystemProperty(named = "mp.homologacao.enabled", matches = "true")
@SpringBootTest(properties = {
        "app.mercado-pago.point.modo=HOMOLOGACAO",
        "app.mercado-pago.point.webhook-validar-assinatura=true",
        "app.mercado-pago.point.reconciliacao-ativa-habilitada=false"
})
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class MercadoPagoPointHomologacaoIntegrationTest extends CaixaIntegrationTestSupport {

    @DynamicPropertySource
    static void registrarCredenciaisHomologacao(DynamicPropertyRegistry registry) {
        Properties env = carregarEnv();
        registrarSePresente(registry, "app.mercado-pago.point.access-token", env, "MP_ACCESS_TOKEN");
        registrarSePresente(registry, "app.mercado-pago.point.webhook-secret", env, "MP_WEBHOOK_SECRET");
        registrarSePresente(registry, "app.mercado-pago.point.api-base-url", env, "MP_API_BASE_URL");
    }

    private static Properties carregarEnv() {
        Properties props = new Properties();
        for (Path candidato : List.of(Path.of(".env"), Path.of("valhala.core.stock.app/.env"))) {
            if (Files.isRegularFile(candidato)) {
                try (Reader reader = Files.newBufferedReader(candidato, StandardCharsets.UTF_8)) {
                    props.load(reader);
                    break;
                } catch (IOException ignored) {
                    // fallback para variáveis de ambiente do processo
                }
            }
        }
        return props;
    }

    private static void registrarSePresente(
            DynamicPropertyRegistry registry,
            String propriedadeSpring,
            Properties envArquivo,
            String chaveEnv
    ) {
        String valor = System.getenv(chaveEnv);
        if (valor == null || valor.isBlank()) {
            valor = envArquivo.getProperty(chaveEnv);
        }
        if (valor != null && !valor.isBlank()) {
            String finalValor = valor.trim();
            registry.add(propriedadeSpring, () -> finalValor);
        }
    }

    private static final String[] PERMISSOES_OPERADOR = {
            "CAIXA_OPERAR", "CAIXA_VISUALIZAR", "CAIXA_SANGRIA", "CAIXA_SUPRIMENTO"
    };

    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Autowired
    private MercadoPagoPointClient pointClient;

    @Autowired
    private ReservaEstoqueRepository reservaEstoqueRepository;

    @Test
    void ativaClienteHttpRealEListaTerminaisNaApiMercadoPagoEmHomologacao() throws Exception {
        assertThat(pointClient).isInstanceOf(HttpMercadoPagoPointClient.class);

        UsuarioAutenticado gerente = autenticarGerente("gerente.homolog1@meraki.com");

        MvcResult result = mockMvc.perform(get("/caixa/terminais/mercado-pago")
                        .cookie(gerente.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode lista = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(lista.isArray()).isTrue();
    }

    @Test
    void consultaOrderInexistenteEFalhaAoCriarEmTerminalInexistenteCompensaReservaELotes() throws Exception {
        assertThat(pointClient).isInstanceOf(HttpMercadoPagoPointClient.class);

        assertThatThrownBy(() -> pointClient.consultarOrder("ORD_INEXISTENTE_VALHALLA_HOMOLOG"))
                .isInstanceOf(IntegracaoMercadoPagoException.class)
                .satisfies(ex -> {
                    IntegracaoMercadoPagoException mpEx = (IntegracaoMercadoPagoException) ex;
                    assertThat(mpEx.isErroTransitorio()).isFalse();
                    assertThat(mpEx.getHttpStatus().value()).isIn(400, 404);
                });

        UsuarioAutenticado gerente = autenticarGerente("gerente.homolog2@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.homolog2@meraki.com", PERMISSOES_OPERADOR);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Homologação Erro");
        String terminalUuid = cadastrarTerminal(gerente, caixa.getId(), "PAX_A910__INEXISTENTE_HOMOLOG", "Smart Inexistente");
        habilitarPoint();
        abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity cat = categoria("Homologação");
        ProdutoEntity cerveja = produtoPadrao("Heineken Homolog", cat, false, "7.00", "15.00", null, "0.000");
        lote(cerveja, "HML-L1", LocalDate.now().plusDays(15), "5.000");

        String pedidoId = criarPedidoSimples(operador, "Mesa Homolog 01", cerveja.getId(), 2);

        // Envio para terminal inexistente na API real do Mercado Pago rejeita (4xx), compensa reserva e preserva lote
        MvcResult erroCriar = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "PIX",
                                  "valor": 30.00,
                                  "terminalCaixaId": "%s",
                                  "chaveIdempotencia": "homolog-err-%s"
                                }
                                """.formatted(terminalUuid, UUID.randomUUID())))
                .andReturn();

        assertThat(erroCriar.getResponse().getStatus()).isIn(400, 404, 422);

        List<ReservaEstoqueEntity> reservas = reservaEstoqueRepository
                .findByPedidoIdOrderByIdAsc(UUID.fromString(pedidoId));
        assertThat(reservas).isNotEmpty();
        assertThat(reservas).allMatch(r -> r.getStatus() == StatusReservaEstoque.LIBERADA);

        EstoqueEntity estoque = estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow();
        assertThat(estoque.getQuantidadeReservada()).isEqualByComparingTo("0.000");
        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("5.000");
        List<LoteEstoqueEntity> lotes = loteEstoqueRepository.findAllByEstoqueId(estoque.getId());
        assertThat(lotes.getFirst().getQuantidadeAtual()).isEqualByComparingTo("5.000");
    }

    @Test
    void cicloDeOrderRealEmHomologacaoCriaSincronizaECancelaLiberandoReserva() throws Exception {
        List<TerminalMercadoPagoDto> terminais = pointClient.listarTerminais();
        TerminalMercadoPagoDto terminalPdv = terminais.stream()
                .filter(t -> "PDV".equalsIgnoreCase(t.operatingMode()))
                .findFirst()
                .orElse(terminais.isEmpty() ? null : terminais.getFirst());

        Assumptions.assumeTrue(
                terminalPdv != null && terminalPdv.id() != null,
                "Nenhum terminal Point vinculado à conta Mercado Pago para teste de ciclo completo de Order"
        );
        Assumptions.assumeTrue(
                "PDV".equalsIgnoreCase(terminalPdv.operatingMode()),
                "Terminal Point encontrado não está em modo PDV (modo atual: " + terminalPdv.operatingMode() + ")"
        );

        UsuarioAutenticado gerente = autenticarGerente("gerente.homolog3@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.homolog3@meraki.com", PERMISSOES_OPERADOR);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Homologação Real");
        String terminalUuid = cadastrarTerminal(gerente, caixa.getId(), terminalPdv.id(), "Smart Homolog Real");
        habilitarPoint();
        abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity cat = categoria("Homologação Live");
        ProdutoEntity cerveja = produtoPadrao("Stella Homolog", cat, false, "6.00", "12.00", null, "0.000");
        lote(cerveja, "HML-LIVE-1", LocalDate.now().plusDays(20), "4.000");

        String pedidoId = criarPedidoSimples(operador, "Mesa Live 01", cerveja.getId(), 1);
        String pagamentoId = null;

        try {
            MvcResult envio = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                            .cookie(operador.accessToken())
                            .with(csrf(operador))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "forma": "PIX",
                                      "valor": 12.00,
                                      "terminalCaixaId": "%s",
                                      "chaveIdempotencia": "homolog-live-%s"
                                    }
                                    """.formatted(terminalUuid, UUID.randomUUID())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AGUARDANDO_MAQUINA"))
                    .andExpect(jsonPath("$.pagamentos[0].orderExternaId").isNotEmpty())
                    .andReturn();

            pagamentoId = JsonPath.read(envio.getResponse().getContentAsString(), "$.pagamentos[0].id");

            mockMvc.perform(post("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/sincronizar", pedidoId, pagamentoId)
                            .cookie(operador.accessToken())
                            .with(csrf(operador)))
                    .andExpect(status().isOk());
        } finally {
            if (pagamentoId != null) {
                mockMvc.perform(post("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/cancelamento-point",
                                        pedidoId, pagamentoId)
                                .cookie(operador.accessToken())
                                .with(csrf(operador)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.pagamentos[0].status").value("CANCELADO"));
            }
        }

        EstoqueEntity estoqueFinal = estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow();
        assertThat(estoqueFinal.getQuantidadeReservada()).isEqualByComparingTo("0.000");
        assertThat(estoqueFinal.getQuantidadeAtual()).isEqualByComparingTo("4.000");
    }

    private void abrirSessao(UsuarioAutenticado operador, UUID caixaId, String fundoTroco) throws Exception {
        mockMvc.perform(post("/caixa/sessoes")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caixaId\":\"%s\",\"fundoTroco\":%s}".formatted(caixaId, fundoTroco)))
                .andExpect(status().isCreated());
    }

    private String cadastrarTerminal(
            UsuarioAutenticado gerente,
            UUID caixaId,
            String terminalId,
            String nome
    ) throws Exception {
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

    private void habilitarPoint() {
        ConfiguracaoCaixaEntity config = configuracao(estabelecimento);
        config.setPointHabilitado(true);
        config.setPermitirPagamentoManual(true);
        config.setParcelamentoHabilitado(true);
        config.setParcelasMinimas(1);
        config.setParcelasMaximas(3);
        configuracaoCaixaRepository.saveAndFlush(config);
    }

    private String criarPedidoSimples(
            UsuarioAutenticado operador,
            String atendimento,
            UUID produtoId,
            int quantidade
    ) throws Exception {
        MvcResult res = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA",
                                  "identificadorAtendimento": "%s",
                                  "itens": [{"produtoId": "%s", "quantidade": %d}]
                                }
                                """.formatted(atendimento, produtoId, quantidade)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }
}
