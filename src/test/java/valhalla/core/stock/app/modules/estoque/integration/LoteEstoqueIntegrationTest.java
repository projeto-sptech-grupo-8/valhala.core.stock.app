package valhalla.core.stock.app.modules.estoque.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoteEstoqueIntegrationTest extends EstoqueIntegrationTestSupport {

    private static final LocalDate HOJE = LocalDate.now();

    @Autowired
    private ComposicaoDrinkRepository composicaoDrinkRepository;

    @Test
    void consultaDeLotesExigeVisualizarEstoque() throws Exception {
        Cookie semPermissao = autenticarComPermissoes("sem-lotes@meraki.com");

        mockMvc.perform(get("/lotes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/lotes").cookie(semPermissao)).andExpect(status().isForbidden());
        mockMvc.perform(get("/lotes/resumo").cookie(semPermissao)).andExpect(status().isForbidden());
    }

    @Test
    void entradaComLoteEValidadeCriaLoteERepeteOMesmoCodigoNoMesmoLote() throws Exception {
        ProdutoEntity produto = produtoPadrao("Heineken 600ml", categoria("Cervejas"), false, "0");
        Cookie operador = operador("lote-entrada@meraki.com");
        LocalDate validade = HOJE.plusDays(60);

        entrada(operador, produto, "12", "L2611A", validade)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lote").value("L2611A"))
                .andExpect(jsonPath("$.lotes[0].codigo").value("L2611A"))
                .andExpect(jsonPath("$.lotes[0].validade").value(validade.toString()))
                .andExpect(jsonPath("$.lotes[0].quantidade").value(12));
        entrada(operador, produto, "6", "L2611A", null).andExpect(status().isCreated());

        mockMvc.perform(get("/lotes").param("produtoId", produto.getId().toString()).cookie(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].codigo").value("L2611A"))
                .andExpect(jsonPath("$.itens[0].quantidadeAtual").value(18))
                .andExpect(jsonPath("$.itens[0].situacao").value("VALIDO"))
                .andExpect(jsonPath("$.itens[0].diasParaVencer").value(60))
                .andExpect(jsonPath("$.itens[0].valorCusto").value(180.0));
    }

    @Test
    void mesmoLoteComOutraValidadeEValidadeEmSaidaSaoRejeitados() throws Exception {
        ProdutoEntity produto = produtoPadrao("Heineken 600ml", categoria("Cervejas"), false, "0");
        Cookie operador = operador("lote-regras@meraki.com");
        entrada(operador, produto, "5", "L1", HOJE.plusDays(10)).andExpect(status().isCreated());

        entrada(operador, produto, "5", "L1", HOJE.plusDays(20))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("já está cadastrado com validade")));
        registrar(operador, """
                {"produtoId":"%s","tipo":"SAIDA","quantidade":1,"validade":"%s"}"""
                .formatted(produto.getId(), HOJE.plusDays(10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("validade só é informada em entradas")));
    }

    @Test
    void saidaConsomePrimeiroOLoteQueVenceAntes() throws Exception {
        ProdutoEntity produto = produtoPadrao("Heineken 600ml", categoria("Cervejas"), false, "0");
        Cookie operador = operador("lote-fefo@meraki.com");
        entrada(operador, produto, "10", "VENCE-DEPOIS", HOJE.plusDays(90)).andExpect(status().isCreated());
        entrada(operador, produto, "4", "VENCE-ANTES", HOJE.plusDays(5)).andExpect(status().isCreated());

        registrar(operador, saida(produto, "SAIDA", "6"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoPosterior").value(8))
                .andExpect(jsonPath("$.lote").value("VENCE-ANTES, VENCE-DEPOIS"))
                .andExpect(jsonPath("$.lotes.length()").value(2))
                .andExpect(jsonPath("$.lotes[0].codigo").value("VENCE-ANTES"))
                .andExpect(jsonPath("$.lotes[0].quantidade").value(4))
                .andExpect(jsonPath("$.lotes[1].codigo").value("VENCE-DEPOIS"))
                .andExpect(jsonPath("$.lotes[1].quantidade").value(2));

        mockMvc.perform(get("/lotes").param("produtoId", produto.getId().toString()).cookie(operador))
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].codigo").value("VENCE-DEPOIS"))
                .andExpect(jsonPath("$.itens[0].quantidadeAtual").value(8));
        mockMvc.perform(get("/movimentacoes-estoque").param("produtoId", produto.getId().toString())
                        .param("tipo", "SAIDA").cookie(operador))
                .andExpect(jsonPath("$.itens[0].lotes.length()").value(2));
    }

    @Test
    void vendaNaoUsaLoteVencidoEPerdaDoLoteVencidoLiberaOEstoque() throws Exception {
        ProdutoEntity produto = produtoPadrao("Heineken 600ml", categoria("Cervejas"), false, "0");
        Cookie operador = operador("lote-vencido@meraki.com");
        String loteVencido = idDoLote(entrada(operador, produto, "3", "VENCIDO", HOJE.minusDays(2)));
        entrada(operador, produto, "2", "BOM", HOJE.plusDays(40)).andExpect(status().isCreated());

        registrar(operador, saida(produto, "SAIDA", "4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("registre a perda")));
        registrar(operador, """
                {"produtoId":"%s","tipo":"SAIDA","quantidade":1,"loteId":%s}""".formatted(produto.getId(), loteVencido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("venceu em")));

        registrar(operador, """
                {"produtoId":"%s","tipo":"PERDA","quantidade":3,"motivo":"Vencimento","loteId":%s}"""
                .formatted(produto.getId(), loteVencido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotes[0].codigo").value("VENCIDO"))
                .andExpect(jsonPath("$.saldoPosterior").value(2));
        registrar(operador, saida(produto, "SAIDA", "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotes[0].codigo").value("BOM"));
    }

    @Test
    void perdaSemLoteInformadoConsomeOVencidoPrimeiro() throws Exception {
        ProdutoEntity produto = produtoPadrao("Heineken 600ml", categoria("Cervejas"), false, "0");
        Cookie operador = operador("lote-perda-fefo@meraki.com");
        entrada(operador, produto, "2", "BOM", HOJE.plusDays(40)).andExpect(status().isCreated());
        entrada(operador, produto, "1", "VENCIDO", HOJE.minusDays(1)).andExpect(status().isCreated());

        registrar(operador, saida(produto, "PERDA", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotes[0].codigo").value("VENCIDO"));
    }

    @Test
    void consultaDeLotesNaoReconciliaEmLeituraEMovimentacaoReconciliaSaldoAnteriorParaSemLote() throws Exception {
        ProdutoEntity produto = produtoPadrao("Red Bull", categoria("Energéticos"), false, "10");
        Cookie operador = operador("lote-legado@meraki.com");

        mockMvc.perform(get("/lotes").param("produtoId", produto.getId().toString()).cookie(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(0));

        entrada(operador, produto, "5", null, HOJE.plusDays(15))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotes[0].codigo").value("VAL-" + HOJE.plusDays(15)));

        mockMvc.perform(get("/lotes").param("produtoId", produto.getId().toString()).cookie(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2));

        registrar(operador, saida(produto, "SAIDA", "7"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotes[0].codigo").value("VAL-" + HOJE.plusDays(15)))
                .andExpect(jsonPath("$.lotes[0].quantidade").value(5))
                .andExpect(jsonPath("$.lotes[1].codigo").value("SEM-LOTE"))
                .andExpect(jsonPath("$.lotes[1].quantidade").value(2));
    }

    @Test
    void criacaoDeProdutoComSaldoInicialCriaLoteSemLote() throws Exception {
        CategoriaEntity categoria = categoria("Cervejas");
        Cookie gerente = autenticarComPermissoes("lote-produto@meraki.com",
                "GERENCIAR_ESTOQUE", "VISUALIZAR_ESTOQUE");
        String corpo = """
                {"nome":"Stella 330ml","categoriaId":%d,"unidadeMedida":"un","precoVenda":9.9,"precoCusto":5,"quantidadeInicial":24}"""
                .formatted(categoria.getId());
        String produtoId = JsonPath.read(mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/lotes").param("produtoId", produtoId).cookie(gerente))
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].codigo").value("SEM-LOTE"))
                .andExpect(jsonPath("$.itens[0].quantidadeAtual").value(24));
        mockMvc.perform(get("/movimentacoes-estoque").param("produtoId", produtoId).cookie(gerente))
                .andExpect(jsonPath("$.itens[0].tipo").value("ENTRADA_INICIAL"))
                .andExpect(jsonPath("$.itens[0].lotes[0].codigo").value("SEM-LOTE"));
    }

    @Test
    void saidaDeDrinkNaoConsomeIngredienteVencido() throws Exception {
        CategoriaEntity categoria = categoria("Drinks");
        ProdutoEntity drink = drink("Vodka Energy", categoria);
        ProdutoEntity vodka = produtoPadrao("Vodka Absolut", categoria, true, "0");
        ingrediente(drink, vodka, "50");
        Cookie operador = operador("lote-drink@meraki.com");
        entrada(operador, vodka, "1000", "VODKA-VENCIDA", HOJE.minusDays(1)).andExpect(status().isCreated());

        mockMvc.perform(post("/movimentacoes-estoque/saida-drink").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drinkId\":\"%s\",\"quantidade\":1}".formatted(drink.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("registre a perda")));

        entrada(operador, vodka, "1000", "VODKA-BOA", HOJE.plusDays(300)).andExpect(status().isCreated());
        mockMvc.perform(post("/movimentacoes-estoque/saida-drink").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drinkId\":\"%s\",\"quantidade\":2}".formatted(drink.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].lotes[0].codigo").value("VODKA-BOA"))
                .andExpect(jsonPath("$[0].lotes[0].quantidade").value(100));
    }

    @Test
    void filtroPorSituacaoEResumoDeValidade() throws Exception {
        CategoriaEntity cervejas = categoria("Cervejas");
        ProdutoEntity produto = produtoPadrao("Heineken 600ml", cervejas, false, "0");
        ProdutoEntity vodka = produtoPadrao("Vodka 1L", cervejas, true, "0");
        ProdutoEntity inativo = produtoPadrao("Inativo", cervejas, false, "0");
        Cookie operador = operador("lote-resumo@meraki.com");
        entrada(operador, produto, "1", "VENCIDO", HOJE.minusDays(3)).andExpect(status().isCreated());
        entrada(operador, produto, "2", "SETE", HOJE.plusDays(7)).andExpect(status().isCreated());
        entrada(operador, produto, "3", "TRINTA", HOJE.plusDays(30)).andExpect(status().isCreated());
        entrada(operador, produto, "4", "LONGE", HOJE.plusDays(200)).andExpect(status().isCreated());
        entrada(operador, produto, "2", null, null).andExpect(status().isCreated());
        // fracionado: precoCusto = 10, volume = 1000ml -> 500ml = 5.00
        entrada(operador, vodka, "500", "VODKA-VENC", HOJE.minusDays(1)).andExpect(status().isCreated());
        entrada(operador, inativo, "10", "INATIVO-VENC", HOJE.minusDays(1)).andExpect(status().isCreated());
        inativo.setAtivo(false);
        produtoRepository.saveAndFlush(inativo);

        mockMvc.perform(get("/lotes").param("situacao", "VENCE_EM_7_DIAS").cookie(operador))
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].codigo").value("SETE"));
        mockMvc.perform(get("/lotes").param("venceEmDias", "30").cookie(operador))
                .andExpect(jsonPath("$.totalItens").value(5))
                .andExpect(jsonPath("$.itens[0].codigo").value("VENCIDO"));

        mockMvc.perform(get("/lotes/resumo").cookie(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotesComSaldo").value(6))
                .andExpect(jsonPath("$.vencidos").value(2))
                .andExpect(jsonPath("$.vencemEm7Dias").value(1))
                .andExpect(jsonPath("$.vencemEm30Dias").value(1))
                .andExpect(jsonPath("$.semValidade").value(1))
                .andExpect(jsonPath("$.valorVencido").value(15.0))
                .andExpect(jsonPath("$.valorEmRisco").value(65.0));
    }

    private Cookie operador(String email) throws Exception {
        return autenticarComPermissoes(email, "MOVIMENTAR_ESTOQUE", "VISUALIZAR_ESTOQUE");
    }

    private ResultActions entrada(Cookie operador, ProdutoEntity produto, String quantidade, String lote, LocalDate validade)
            throws Exception {
        String corpo = "{\"produtoId\":\"%s\",\"tipo\":\"ENTRADA\",\"quantidade\":%s%s%s}".formatted(
                produto.getId(), quantidade,
                lote == null ? "" : ",\"lote\":\"%s\"".formatted(lote),
                validade == null ? "" : ",\"validade\":\"%s\"".formatted(validade));
        return registrar(operador, corpo);
    }

    private ResultActions registrar(Cookie operador, String corpo) throws Exception {
        return mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private String saida(ProdutoEntity produto, String tipo, String quantidade) {
        return "{\"produtoId\":\"%s\",\"tipo\":\"%s\",\"quantidade\":%s,\"motivo\":\"Teste\"}"
                .formatted(produto.getId(), tipo, quantidade);
    }

    private String idDoLote(ResultActions entrada) throws Exception {
        Number id = JsonPath.read(entrada.andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(), "$.lotes[0].loteId");
        return id.toString();
    }

    private void ingrediente(ProdutoEntity drink, ProdutoEntity produto, String quantidadeMl) {
        ComposicaoDrinkEntity composicao = new ComposicaoDrinkEntity();
        composicao.setDrink(drink);
        composicao.setProduto(produto);
        composicao.setQuantidade(new BigDecimal(quantidadeMl));
        composicao.setUnidadeConsumo(UnidadeConsumoDrink.ML);
        composicaoDrinkRepository.saveAndFlush(composicao);
    }
}
