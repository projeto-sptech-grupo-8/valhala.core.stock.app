package valhalla.core.stock.app.modules.relatorios;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.support.PostgresIntegrationTestSupport;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelatorioIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie gerente;
    private ProdutoEntity cerveja;
    private ProdutoEntity vodka;

    @BeforeEach
    void setUp() throws Exception {
        gerente = autenticarGerente("gerente@meraki.com");
        cerveja = produtoPadrao("Cerveja Lata", categoria("Bebidas"), false, "10");
        vodka = produtoPadrao("Vodka", categoria("Destilados"), false, "5");
    }

    @Test
    void curvaAbcEMargemDevemDistribuirODescontoEUsarOCustoDaVenda() throws Exception {
        // pedido 1: 3 cervejas + 1 vodka = 80, desconto 8 → 72 (90% do subtotal em cada item)
        vender("MESA", 8, cerveja, 3, vodka, 1);
        // pedido 2: 1 vodka = 20
        vender("BALCAO", 0, vodka, 1);
        // custo alterado depois da venda não muda o CMV já realizado
        cerveja.setPrecoCusto(new BigDecimal("15.00"));
        produtoRepository.saveAndFlush(cerveja);

        String abc = relatorio("abc", 7);
        assertThat(numero(abc, "$.totais.vendas")).isEqualByComparingTo("2");
        assertThat(numero(abc, "$.totais.faturamento")).isEqualByComparingTo("92");
        assertThat(linha(abc, "nome", "Cerveja Lata", "receita")).isEqualByComparingTo("54");
        assertThat(linha(abc, "nome", "Cerveja Lata", "posicao")).isEqualByComparingTo("1");
        assertThat(linha(abc, "nome", "Vodka", "receita")).isEqualByComparingTo("38");
        assertThat(numero(abc, "$.itens[1].acumulado")).isEqualByComparingTo("100");

        String margem = relatorio("margem-categorias", 7);
        assertThat(linha(margem, "categoria", "Bebidas", "receita")).isEqualByComparingTo("54");
        assertThat(linha(margem, "categoria", "Bebidas", "cmv")).isEqualByComparingTo("30");
        assertThat(linha(margem, "categoria", "Bebidas", "lucro")).isEqualByComparingTo("24");
        assertThat(linha(margem, "categoria", "Destilados", "cmv")).isEqualByComparingTo("20");
        assertThat(linha(margem, "categoria", "Destilados", "margem").setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("47.37");
    }

    @Test
    void vendaEstornadaNaoEntraNosRelatorios() throws Exception {
        String venda = vender("BALCAO", 0, cerveja, 2);
        estornar(gerente, "gerente@meraki.com", JsonPath.read(venda, "$.id"));

        String abc = relatorio("abc", 7);
        assertThat(numero(abc, "$.totais.vendas")).isEqualByComparingTo("0");
        assertThat((List<?>) JsonPath.read(abc, "$.itens")).isEmpty();
    }

    @Test
    void perdasDevemUsarOMotivoEstruturadoEIgnorarAjustes() throws Exception {
        movimentar(cerveja, "PERDA", 1, "Degustação com cliente", "CONSUMO_INTERNO").andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivoPerda").value("CONSUMO_INTERNO"));
        // cliente antigo, sem motivoPerda: classificado pelo texto
        movimentar(vodka, "PERDA", 2, "Produto vencido", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivoPerda").value("VENCIMENTO"));
        movimentar(vodka, "AJUSTE_NEGATIVO", 1, "Estorno automático", null).andExpect(status().isCreated());
        movimentar(vodka, "SAIDA", 1, "Uso", "QUEBRA").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O motivo da perda só se aplica a movimentações do tipo PERDA"));

        String perdas = relatorio("perdas", 7);
        assertThat((String) JsonPath.read(perdas, "$.granularidade")).isEqualTo("DIA");
        assertThat((List<String>) JsonPath.read(perdas, "$.itens[*].motivo"))
                .containsExactlyInAnyOrder("Consumo interno", "Vencimento");
        assertThat(linha(perdas, "motivo", "Consumo interno", "valor")).isEqualByComparingTo("10");
        assertThat(linha(perdas, "motivo", "Vencimento", "valor")).isEqualByComparingTo("20");
    }

    @Test
    void rupturaDeveEstimarAVendaPerdidaPelaMediaDosDiasComEstoque() throws Exception {
        // cerveja: 10 vendidas hoje e zerada desde então → 1 dia zerado em 7;
        // média dos 6 dias com estoque = 10/6 → 2 unidades perdidas × R$ 20
        vender("BALCAO", 0, cerveja, 10);
        // rum: zerado há 40 dias, sem movimentação no período → zerado nos 7 dias, sem base de estimativa
        ProdutoEntity rum = produtoPadrao("Rum", categoria("Rum"), false, "0");
        UUID usuario = userRepository.findByEmailIgnoreCase("gerente@meraki.com").orElseThrow().getId();
        jdbc.update("""
                INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior,
                                                  saldo_posterior, motivo, ocorrido_em)
                SELECT e.id, ?, 'AJUSTE_NEGATIVO', 5, 5, 0, 'Correção', now() - interval '40 days'
                FROM estoque e WHERE e.produto_id = ?
                """, usuario, rum.getId());

        String ruptura = relatorio("ruptura", 7);
        assertThat(linha(ruptura, "nome", "Cerveja Lata", "dias")).isEqualByComparingTo("1");
        assertThat(linha(ruptura, "nome", "Cerveja Lata", "ocorrencias")).isEqualByComparingTo("1");
        assertThat(linha(ruptura, "nome", "Cerveja Lata", "unidadesPerdidas")).isEqualByComparingTo("2");
        assertThat(linha(ruptura, "nome", "Cerveja Lata", "vendaPerdida")).isEqualByComparingTo("40");
        assertThat(linha(ruptura, "nome", "Rum", "dias")).isEqualByComparingTo("7");
        assertThat(linha(ruptura, "nome", "Rum", "percentualDias")).isEqualByComparingTo("100");
        assertThat(linha(ruptura, "nome", "Rum", "vendaPerdida")).isEqualByComparingTo("0");
        assertThat(linha(ruptura, "nome", "Vodka", "dias")).isEqualByComparingTo("0");
    }

    @Test
    void canaisEHorariosDevemRefletirAsVendasDoPeriodo() throws Exception {
        vender("MESA", 0, cerveja, 1);
        vender("DELIVERY", 0, cerveja, 2);

        String canais = relatorio("canais", 7);
        assertThat(linha(canais, "canal", "Mesa", "faturamento")).isEqualByComparingTo("20");
        assertThat(linha(canais, "canal", "Delivery", "faturamento")).isEqualByComparingTo("40");
        assertThat(linha(canais, "canal", "Delivery", "itensPorVenda")).isEqualByComparingTo("2");

        String horarios = relatorio("horarios", 7);
        List<Integer> vendas = JsonPath.read(horarios, "$.itens[*].vendas");
        assertThat(vendas.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
        int hoje = FusoHorarioOperacao.agora().getDayOfWeek().getValue() - 1;
        assertThat((List<Integer>) JsonPath.read(horarios, "$.itens[*].diaSemana")).containsOnly(hoje);
    }

    @Test
    void relatoriosExigemPermissaoPropria() throws Exception {
        Cookie estoquista = autenticarComPermissoes("estoque@meraki.com", "VISUALIZAR_ESTOQUE");
        mockMvc.perform(get("/relatorios/abc").param("dias", "7").cookie(estoquista))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/relatorios/abc/exportar").param("dias", "7").cookie(estoquista))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/relatorios/ruptura/exportar").param("dias", "7").cookie(estoquista))
                .andExpect(status().isForbidden());

        Cookie financeiro = autenticarComPermissoes("financeiro@meraki.com", "VISUALIZAR_RELATORIOS");
        mockMvc.perform(get("/relatorios/abc").param("dias", "7").cookie(financeiro))
                .andExpect(status().isOk());
        mockMvc.perform(get("/relatorios/abc/exportar").param("dias", "7").cookie(financeiro))
                .andExpect(status().isOk());
        mockMvc.perform(get("/relatorios/ruptura/exportar").param("dias", "7").cookie(financeiro))
                .andExpect(status().isOk());
    }

    @Test
    void deveExportarCurvaAbcERupturaEmExcel() throws Exception {
        vender("BALCAO", 0, cerveja, 10);
        ProdutoEntity rum = produtoPadrao("Rum", categoria("Rum"), false, "0");
        UUID usuario = userRepository.findByEmailIgnoreCase("gerente@meraki.com").orElseThrow().getId();
        jdbc.update("""
                INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior,
                                                  saldo_posterior, motivo, ocorrido_em)
                SELECT e.id, ?, 'AJUSTE_NEGATIVO', 5, 5, 0, 'Correção', now() - interval '40 days'
                FROM estoque e WHERE e.produto_id = ?
                """, usuario, rum.getId());
        entityManager.flush();

        MvcResult abcExportado = mockMvc.perform(get("/relatorios/abc/exportar").param("dias", "7").cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("curva-abc-7d"),
                                org.hamcrest.Matchers.containsString(".xlsx"))))
                .andReturn();

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(abcExportado.getResponse().getContentAsByteArray()))) {
            var sheet = wb.getSheet("Curva ABC");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Cerveja Lata");
            assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(200.0);
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("A");
        }

        MvcResult rupturaExportada = mockMvc.perform(get("/relatorios/ruptura/exportar").param("dias", "7").cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("falta-estoque-7d"),
                                org.hamcrest.Matchers.containsString(".xlsx"))))
                .andReturn();

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(rupturaExportada.getResponse().getContentAsByteArray()))) {
            var sheet = wb.getSheet("Falta de Estoque");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Cerveja Lata");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("Recorrente");
        }
    }

    @Test
    void curvaAbcNaoDuplicaProdutoQuandoCategoriaMudaEntreVendas() throws Exception {
        vender("BALCAO", 0, cerveja, 1);
        CategoriaEntity novaCategoria = categoria("Bebidas Especiais");
        cerveja.setCategoria(novaCategoria);
        produtoRepository.saveAndFlush(cerveja);
        vender("BALCAO", 0, cerveja, 2);

        String abc = relatorio("abc", 7);
        List<Object> linhasCerveja = JsonPath.read(abc, "$.itens[?(@.nome == 'Cerveja Lata')]");
        assertThat(linhasCerveja).hasSize(1);
        assertThat((String) JsonPath.read(abc, "$.itens[0].categoria")).isEqualTo("Bebidas Especiais");
        assertThat(linha(abc, "nome", "Cerveja Lata", "quantidade")).isEqualByComparingTo("3");
        assertThat(linha(abc, "nome", "Cerveja Lata", "receita")).isEqualByComparingTo("60.00");
        assertThat(linha(abc, "nome", "Cerveja Lata", "percentual")).isEqualByComparingTo("100.00");

        String margem = relatorio("margem-categorias", 7);
        assertThat(linha(margem, "categoria", "Bebidas", "receita")).isEqualByComparingTo("20.00");
        assertThat(linha(margem, "categoria", "Bebidas Especiais", "receita")).isEqualByComparingTo("40.00");
    }

    @Test
    void rupturaIncluiProdutoCriadoZeradoSemMovimentacoesEIgnoraReposicaoNoMesmoDia() throws Exception {
        CategoriaEntity cat = categoria("SemMov");
        ProdutoEntity criadoZerado = produtoPadrao("Produto Criado Zerado", cat, false, "0");
        LocalDateTime tresDiasAtras = FusoHorarioOperacao.hoje().minusDays(3).atTime(10, 0);
        jdbc.update("UPDATE produto SET criado_em = ? WHERE id = ?", Timestamp.valueOf(tresDiasAtras), criadoZerado.getId());

        ProdutoEntity repostoMesmoDia = produtoPadrao("Reposto Mesmo Dia", cat, false, "5");
        UUID usuario = userRepository.findByEmailIgnoreCase("gerente@meraki.com").orElseThrow().getId();
        LocalDateTime hojeManha = FusoHorarioOperacao.hoje().atTime(10, 0);
        jdbc.update("""
                INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior,
                                                  saldo_posterior, motivo, ocorrido_em)
                SELECT e.id, ?, 'SAIDA', 5, 5, 0, 'Venda', ?
                FROM estoque e WHERE e.produto_id = ?
                """, usuario, Timestamp.valueOf(hojeManha), repostoMesmoDia.getId());
        jdbc.update("""
                INSERT INTO movimentacao_estoque (estoque_id, usuario_id, tipo, quantidade, saldo_anterior,
                                                  saldo_posterior, motivo, ocorrido_em)
                SELECT e.id, ?, 'ENTRADA', 5, 0, 5, 'Reposicao rapida', ?
                FROM estoque e WHERE e.produto_id = ?
                """, usuario, Timestamp.valueOf(hojeManha.plusMinutes(30)), repostoMesmoDia.getId());

        String ruptura = relatorio("ruptura", 7);
        assertThat(linha(ruptura, "nome", "Produto Criado Zerado", "dias")).isEqualByComparingTo("4");
        assertThat(linha(ruptura, "nome", "Produto Criado Zerado", "ocorrencias")).isEqualByComparingTo("1");
        assertThat(linha(ruptura, "nome", "Reposto Mesmo Dia", "dias")).isEqualByComparingTo("0");
        assertThat(linha(ruptura, "nome", "Reposto Mesmo Dia", "ocorrencias")).isEqualByComparingTo("0");
    }

    @Test
    void perdasSuportaGranularidadeSemanalEMensal() throws Exception {
        movimentar(cerveja, "PERDA", 1, "Quebrou", "QUEBRA").andExpect(status().isCreated());

        String semanal = relatorio("perdas", 30);
        assertThat((String) JsonPath.read(semanal, "$.granularidade")).isEqualTo("SEMANA");
        assertThat((List<String>) JsonPath.read(semanal, "$.itens[*].motivo")).contains("Perda / quebra");

        String mensal = relatorio("perdas", 90);
        assertThat((String) JsonPath.read(mensal, "$.granularidade")).isEqualTo("MES");
        assertThat((List<String>) JsonPath.read(mensal, "$.itens[*].motivo")).contains("Perda / quebra");
    }

    /** Venda no caixa pelo gerente; itens: pares (produto, quantidade). */
    private String vender(String canal, int desconto, Object... itens) throws Exception {
        return vender(gerente, canal, desconto == 0 ? null : String.valueOf(desconto), itens);
    }

    private org.springframework.test.web.servlet.ResultActions movimentar(
            ProdutoEntity produto, String tipo, int quantidade, String motivo, String motivoPerda) throws Exception {
        String extra = motivoPerda == null ? "" : ", \"motivoPerda\": \"%s\"".formatted(motivoPerda);
        return mockMvc.perform(post("/movimentacoes-estoque").cookie(gerente).with(csrf(gerente))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"produtoId": "%s", "tipo": "%s", "quantidade": %d, "motivo": "%s"%s}
                        """.formatted(produto.getId(), tipo, quantidade, motivo, extra)));
    }

    private String relatorio(String nome, int dias) throws Exception {
        entityManager.flush();
        return mockMvc.perform(get("/relatorios/" + nome).param("dias", String.valueOf(dias)).cookie(gerente))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static BigDecimal numero(String json, String caminho) {
        Object valor = JsonPath.read(json, caminho);
        return new BigDecimal(String.valueOf(valor));
    }

    private static BigDecimal linha(String json, String chave, String valorChave, String campo) {
        List<Object> valores = JsonPath.read(json, "$.itens[?(@.%s == '%s')].%s".formatted(chave, valorChave, campo));
        assertThat(valores).as("linha %s = %s", chave, valorChave).hasSize(1);
        return new BigDecimal(String.valueOf(valores.getFirst()));
    }
}
