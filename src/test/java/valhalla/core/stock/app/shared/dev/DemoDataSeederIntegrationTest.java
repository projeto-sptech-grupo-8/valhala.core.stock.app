package valhalla.core.stock.app.shared.dev;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MvcResult;
import valhalla.core.stock.app.support.PostgresIntegrationTestSupport;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoDataSeederIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void scriptDeDemonstracaoDeveSerIdempotenteEPopularRelatoriosFinanceiroEProdutos() throws Exception {
        ClassPathResource script = new ClassPathResource("db/demo/dados_demonstracao.sql");
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            ScriptUtils.executeSqlScript(conn, script);
            return null;
        });
        // Segunda execução garante idempotência completa sem duplicar ou violar constraints.
        jdbcTemplate.execute((ConnectionCallback<Void>) conn -> {
            ScriptUtils.executeSqlScript(conn, script);
            return null;
        });
        entityManager.flush();
        entityManager.clear();

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@meraki.com\",\"senha\":\"Senha@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie devToken = login.getResponse().getCookie("accessToken");
        MvcResult csrfRes = mockMvc.perform(get("/auth/csrf").cookie(devToken))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = JsonPath.read(csrfRes.getResponse().getContentAsString(), "$.token");

        // Relatórios populados (JSON e exportação XLSX)
        mockMvc.perform(get("/relatorios/abc").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isNotEmpty());
        mockMvc.perform(get("/relatorios/margem-categorias").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isNotEmpty());
        mockMvc.perform(get("/relatorios/ruptura").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.dias > 0)]").isNotEmpty());
        mockMvc.perform(get("/relatorios/horarios").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isNotEmpty());
        mockMvc.perform(get("/relatorios/canais").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isNotEmpty());
        mockMvc.perform(get("/relatorios/perdas").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isNotEmpty());

        MvcResult abcXlsx = mockMvc.perform(get("/relatorios/abc/exportar").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andReturn();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(abcXlsx.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheet("Curva ABC").getPhysicalNumberOfRows()).isGreaterThan(5);
        }

        MvcResult rupturaXlsx = mockMvc.perform(get("/relatorios/ruptura/exportar").param("dias", "30").cookie(devToken))
                .andExpect(status().isOk())
                .andReturn();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(rupturaXlsx.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheet("Falta de Estoque").getPhysicalNumberOfRows()).isGreaterThan(5);
        }

        // Financeiro populado (resumo, contas ABERTA/PAGA/INATIVA)
        mockMvc.perform(get("/financeiro/resumo").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fluxoCaixa.length()").value(6))
                .andExpect(jsonPath("$.aPagar30Dias.quantidade").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/financeiro/contas").param("status", "INATIVA").cookie(devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isNotEmpty());

        // Produto sem histórico (BEB-016) pode ser inativado; produto com histórico (BEB-001) retorna 409
        String produtosSemHist = mockMvc.perform(get("/produtos").param("busca", "BEB-016").cookie(devToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> idsSemHist = JsonPath.read(produtosSemHist, "$.itens[*].id");
        assertThat(idsSemHist).hasSize(1);

        mockMvc.perform(delete("/produtos/{id}", idsSemHist.getFirst())
                        .cookie(devToken).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());

        String produtosComHist = mockMvc.perform(get("/produtos").param("busca", "BEB-001").cookie(devToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> idsComHist = JsonPath.read(produtosComHist, "$.itens[*].id");
        assertThat(idsComHist).hasSize(1);

        mockMvc.perform(delete("/produtos/{id}", idsComHist.getFirst())
                        .cookie(devToken).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isConflict());
    }
}
