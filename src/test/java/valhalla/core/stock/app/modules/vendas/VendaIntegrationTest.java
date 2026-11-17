package valhalla.core.stock.app.modules.vendas;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.web.servlet.ResultActions;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.support.PostgresIntegrationTestSupport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VendaIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Cookie gerente;
    private ProdutoEntity cerveja;
    private ProdutoEntity gin;
    private ProdutoEntity ginTonica;

    @BeforeEach
    void setUp() throws Exception {
        gerente = autenticarGerente("gerente@meraki.com");
        CategoriaEntity bebidas = categoria("Bebidas");
        cerveja = produtoPadrao("Cerveja Lata", bebidas, false, "10");
        gin = produtoPadrao("Gin", bebidas, true, "3000");
        // 50 ml de gin + 1 lata (para exercitar as duas unidades de consumo)
        ginTonica = drink("Gin Tônica", categoria("Drinks"),
                gin, "50", UnidadeConsumoDrink.ML, cerveja, "1", UnidadeConsumoDrink.UN);
    }

    @Test
    void deveRegistrarVendaComPrecoDoCadastroDescontoTrocoEBaixaDeEstoque() throws Exception {
        String resposta = vender(gerente, """
                {
                  "canal": "MESA",
                  "observacao": "Mesa 4",
                  "desconto": 10,
                  "itens": [
                    {"produtoId": "%s", "quantidade": 2},
                    {"produtoId": "%s", "quantidade": 1},
                    {"produtoId": "%s", "quantidade": 2}
                  ],
                  "pagamentos": [
                    {"forma": "PIX", "valor": 60},
                    {"forma": "DINHEIRO", "valor": 50}
                  ]
                }
                """.formatted(cerveja.getId(), gin.getId(), ginTonica.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.subtotal").value(110.00))   // 2×20 + 1×20 + 2×25
                .andExpect(jsonPath("$.desconto").value(10.00))
                .andExpect(jsonPath("$.total").value(100.00))
                .andExpect(jsonPath("$.troco").value(10.00))
                .andExpect(jsonPath("$.pagamentos[1].forma").value("DINHEIRO"))
                .andExpect(jsonPath("$.pagamentos[1].valor").value(40.00))
                .andReturn().getResponse().getContentAsString();

        // cerveja: 2 vendidas + 2 dos drinks; gin: 1 garrafa (1000 ml) + 2 × 50 ml
        assertThat(saldo(cerveja)).isEqualByComparingTo("6");
        assertThat(saldo(gin)).isEqualByComparingTo("1900");

        UUID idVenda = UUID.fromString(JsonPath.read(resposta, "$.id"));
        List<Map<String, Object>> itens = jdbc.queryForList(
                "SELECT produto_id, custo_unitario FROM item_pedido WHERE pedido_id = ? ORDER BY id", idVenda);
        assertThat(itens).extracting(i -> (BigDecimal) i.get("custo_unitario"))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10.5"));
        Integer baixasVinculadas = jdbc.queryForObject("""
                SELECT COUNT(*) FROM movimentacao_estoque m JOIN item_pedido i ON i.id = m.item_pedido_id
                WHERE i.pedido_id = ? AND m.tipo IN ('SAIDA', 'SAIDA_DRINK')
                """, Integer.class, idVenda);
        assertThat(baixasVinculadas).isEqualTo(4); // 2 saídas diretas + 2 ingredientes do drink
    }

    @Test
    void deveNumerarVendasEmSequenciaPorEstabelecimento() throws Exception {
        vender(gerente, vendaSimples(cerveja, "1", "20")).andExpect(jsonPath("$.numero").value(1));
        vender(gerente, vendaSimples(cerveja, "1", "20")).andExpect(jsonPath("$.numero").value(2));
    }

    @Test
    void naoDeveGravarNadaQuandoUmItemNaoTemEstoque() throws Exception {
        vender(gerente, """
                {"canal": "BALCAO",
                 "itens": [{"produtoId": "%s", "quantidade": 1}, {"produtoId": "%s", "quantidade": 4}],
                 "pagamentos": [{"forma": "PIX", "valor": 100}]}
                """.formatted(cerveja.getId(), gin.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Gin")));

        // O teste roda dentro de uma transação: a falha da venda a marca para rollback, desfazendo
        // o pedido e a baixa da cerveja já feitos. Fora de testes, a transação da venda é a própria requisição.
        EntityManagerHolder transacao = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(entityManagerFactory);
        assertThat(transacao.getEntityManager().getTransaction().getRollbackOnly()).isTrue();
    }

    @Test
    void deveRejeitarPagamentoInsuficienteETrocoSemDinheiro() throws Exception {
        vender(gerente, vendaSimples(cerveja, "1", "19.99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("não cobrem o total")));
        vender(gerente, """
                {"canal": "BALCAO", "itens": [{"produtoId": "%s", "quantidade": 1}],
                 "pagamentos": [{"forma": "CARTAO_CREDITO", "valor": 25}]}
                """.formatted(cerveja.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Troco só é permitido sobre pagamentos em dinheiro"));
    }

    @Test
    void deveRejeitarDescontoMaiorQueOSubtotalEProdutoInativo() throws Exception {
        vender(gerente, """
                {"canal": "BALCAO", "desconto": 21, "itens": [{"produtoId": "%s", "quantidade": 1}],
                 "pagamentos": [{"forma": "PIX", "valor": 1}]}
                """.formatted(cerveja.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O desconto não pode ser maior que o subtotal da venda"));

        cerveja.setAtivo(false);
        produtoRepository.saveAndFlush(cerveja);
        vender(gerente, vendaSimples(cerveja, "1", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O produto Cerveja Lata está inativo"));
    }

    @Test
    void deveCancelarVendaDevolvendoEstoqueAosLotesEEstornandoPagamentos() throws Exception {
        String resposta = vender(gerente, """
                {"canal": "DELIVERY",
                 "itens": [{"produtoId": "%s", "quantidade": 3}, {"produtoId": "%s", "quantidade": 1}],
                 "pagamentos": [{"forma": "CARTAO_DEBITO", "valor": 85}]}
                """.formatted(cerveja.getId(), ginTonica.getId()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idVenda = JsonPath.read(resposta, "$.id");
        assertThat(saldo(cerveja)).isEqualByComparingTo("6");
        BigDecimal loteGinAntes = saldoLotes(gin);

        mockMvc.perform(post("/vendas/{id}/cancelamento", idVenda).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Pedido lançado errado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Pedido lançado errado"))
                .andExpect(jsonPath("$.pagamentos[0].status").value("ESTORNADO"));

        assertThat(saldo(cerveja)).isEqualByComparingTo("10");
        assertThat(saldo(gin)).isEqualByComparingTo("3000");
        assertThat(saldoLotes(gin)).isEqualByComparingTo(loteGinAntes.add(new BigDecimal("50")));
        assertThat(saldoLotes(cerveja)).isEqualByComparingTo("10");

        mockMvc.perform(post("/vendas/{id}/cancelamento", idVenda).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"De novo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A venda já está cancelada"));
    }

    @Test
    void deveConsultarEListarVendas() throws Exception {
        String resposta = vender(gerente, vendaSimples(cerveja, "2", "40"))
                .andReturn().getResponse().getContentAsString();
        String idVenda = JsonPath.read(resposta, "$.id");

        mockMvc.perform(get("/vendas/{id}", idVenda).cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].descricao").value("Cerveja Lata"))
                .andExpect(jsonPath("$.troco").doesNotExist());
        mockMvc.perform(get("/vendas").param("status", "CONCLUIDO").cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(idVenda))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    void deveExigirPermissoesDeVendaEDeCancelamento() throws Exception {
        Cookie estoquista = autenticarComPermissoes("estoque@meraki.com", "VISUALIZAR_ESTOQUE", "MOVIMENTAR_ESTOQUE");
        vender(estoquista, vendaSimples(cerveja, "1", "20")).andExpect(status().isForbidden());

        Cookie caixa = autenticarComPermissoes("caixa@meraki.com", "REGISTRAR_VENDA");
        String resposta = vender(caixa, vendaSimples(cerveja, "1", "20"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/vendas/{id}/cancelamento", (String) JsonPath.read(resposta, "$.id"))
                        .cookie(caixa).with(csrf(caixa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Teste\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions vender(Cookie usuario, String corpo) throws Exception {
        return mockMvc.perform(post("/vendas").cookie(usuario).with(csrf(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String vendaSimples(ProdutoEntity produto, String quantidade, String pago) {
        return """
                {"canal": "BALCAO", "itens": [{"produtoId": "%s", "quantidade": %s}],
                 "pagamentos": [{"forma": "DINHEIRO", "valor": %s}]}
                """.formatted(produto.getId(), quantidade, pago);
    }

    private BigDecimal saldoLotes(ProdutoEntity produto) {
        entityManager.flush();
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.quantidade_atual), 0) FROM lote_estoque l
                JOIN estoque e ON e.id = l.estoque_id WHERE e.produto_id = ?
                """, BigDecimal.class, produto.getId());
    }
}
