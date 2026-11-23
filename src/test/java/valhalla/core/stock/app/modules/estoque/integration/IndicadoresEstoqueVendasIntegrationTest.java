package valhalla.core.stock.app.modules.estoque.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.support.PostgresIntegrationTestSupport;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Giro dos produtos, resumo de movimentações e bloco de vendas do dashboard. */
class IndicadoresEstoqueVendasIntegrationTest extends PostgresIntegrationTestSupport {

    private Cookie gerente;
    private ProdutoEntity cerveja;
    private ProdutoEntity vodka;

    @BeforeEach
    void setUp() throws Exception {
        gerente = autenticarGerente("gerente@meraki.com");
        cerveja = produtoPadrao("Cerveja Lata", categoria("Bebidas"), false, "10");
        vodka = produtoPadrao("Vodka", categoria("Destilados"), false, "5");

        vender(gerente, "BALCAO", null, cerveja, 3);
        String estornada = vender(gerente, "BALCAO", null, cerveja, 2);
        estornar(gerente, "gerente@meraki.com", JsonPath.read(estornada, "$.id"));
        acao("/movimentacoes-estoque", """
                {"produtoId": "%s", "tipo": "PERDA", "quantidade": 1, "motivo": "Quebrou", "motivoPerda": "QUEBRA"}
                """.formatted(cerveja.getId()));
        acao("/movimentacoes-estoque", """
                {"produtoId": "%s", "tipo": "AJUSTE_NEGATIVO", "quantidade": 1, "motivo": "Correção"}
                """.formatted(vodka.getId()));
    }

    @Test
    void produtoDeveTrazerAsSaidasDosUltimos30DiasSemAsVendasEstornadas() throws Exception {
        String produtos = getJson("/produtos?tamanho=100");
        assertThat(saidas(produtos, "Cerveja Lata")).isEqualByComparingTo("3"); // 3 + 2 − 2 estornadas; perda não conta
        assertThat(saidas(produtos, "Vodka")).isEqualByComparingTo("0");
    }

    @Test
    void resumoDeMovimentacoesDeveContarNoBanco() throws Exception {
        String resumo = getJson("/movimentacoes-estoque/resumo?dias=30");
        assertThat((Integer) JsonPath.read(resumo, "$.saidas")).isEqualTo(3);   // 2 saídas de venda + 1 perda
        assertThat((Integer) JsonPath.read(resumo, "$.perdas")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(resumo, "$.ajustes")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(resumo, "$.estornosVenda")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(resumo, "$.total")).isEqualTo(5);
        assertThat(new BigDecimal(String.valueOf((Object) JsonPath.read(resumo, "$.taxaPerdasPercentual"))))
                .isEqualByComparingTo("33.33");
        assertThat(new BigDecimal(String.valueOf((Object) JsonPath.read(resumo, "$.taxaAjustesPercentual"))))
                .isEqualByComparingTo("20");
        assertThat((Object) JsonPath.read(getJson("/movimentacoes-estoque/resumo"), "$.desde")).isNull();
    }

    @Test
    void dashboardDeveTrazerAsVendasDeHojeParaQuemPodeVerVendas() throws Exception {
        String dashboard = getJson("/dashboard/resumo?meses=6");
        assertThat((Integer) JsonPath.read(dashboard, "$.vendas.vendasHoje")).isEqualTo(1);
        assertThat(new BigDecimal(String.valueOf((Object) JsonPath.read(dashboard, "$.vendas.faturamentoHoje"))))
                .isEqualByComparingTo("60");
        assertThat(new BigDecimal(String.valueOf((Object) JsonPath.read(dashboard, "$.vendas.ticketMedioHoje"))))
                .isEqualByComparingTo("60");
        assertThat((List<String>) JsonPath.read(dashboard, "$.vendas.porFormaPagamento[*].forma"))
                .containsExactly("DINHEIRO");
        List<Object> dinheiro = JsonPath.read(dashboard, "$.vendas.porFormaPagamento[?(@.forma == 'DINHEIRO')].valor");
        assertThat(new BigDecimal(String.valueOf(dinheiro.getFirst()))).isEqualByComparingTo("60");
        assertThat(new BigDecimal(String.valueOf((Object) JsonPath.read(dashboard, "$.vendas.mediaDiariaUltimos30Dias"))))
                .isEqualByComparingTo("0");

        Cookie estoquista = autenticarComPermissoes("estoque@meraki.com", "VISUALIZAR_ESTOQUE");
        String semVendas = mockMvc.perform(get("/dashboard/resumo").cookie(estoquista))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(semVendas, "$.vendas")).isNull();
    }

    private BigDecimal saidas(String json, String nome) {
        List<Object> valores = JsonPath.read(json, "$.itens[?(@.nome == '%s')].saidasUltimos30Dias".formatted(nome));
        return new BigDecimal(String.valueOf(valores.getFirst()));
    }

    private String acao(String url, String corpo) throws Exception {
        return mockMvc.perform(post(url).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isBetween(200, 201))
                .andReturn().getResponse().getContentAsString();
    }

    private String getJson(String url) throws Exception {
        entityManager.flush();
        return mockMvc.perform(get(url).cookie(gerente))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
