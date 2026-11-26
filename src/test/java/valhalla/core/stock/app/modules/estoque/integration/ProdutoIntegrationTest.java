package valhalla.core.stock.app.modules.estoque.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProdutoIntegrationTest extends EstoqueIntegrationTestSupport {

    @Test
    void endpointsDeProdutoExigemAutenticacaoEPermissoesAdequadas() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity produto = produtoPadrao("Red Bull", categoria, false, "10");
        Cookie semPermissao = autenticarComPermissoes("sem-permissao@meraki.com");

        mockMvc.perform(get("/produtos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/produtos").cookie(semPermissao)).andExpect(status().isForbidden());
        mockMvc.perform(post("/produtos").cookie(semPermissao).with(csrf(semPermissao))
                        .contentType(MediaType.APPLICATION_JSON).content(novoProduto(categoria.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/produtos/{id}", produto.getId()).cookie(semPermissao)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/produtos/{id}", produto.getId()).cookie(semPermissao).with(csrf(semPermissao))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Outro\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/produtos/{id}", produto.getId()).cookie(semPermissao).with(csrf(semPermissao)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteExecutaCrudDeProdutosComFiltroPaginacaoECsrf() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        Cookie gerente = autenticarGerente("gerente-produtos@meraki.com");

        MvcResult criado = mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content(novoProduto(categoria.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(org.hamcrest.Matchers.startsWith("PRD-")))
                .andExpect(jsonPath("$.quantidadeEstoque").value(0))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(criado.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/produtos").param("busca", "absolut").param("tipo", "PADRAO")
                        .param("pagina", "0").param("tamanho", "1").cookie(gerente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Vodka Absolut"));
        mockMvc.perform(get("/produtos/{id}", id).cookie(gerente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fracionado").value(true));
        mockMvc.perform(patch("/produtos/{id}", id).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precoVenda\":95.00,\"estoqueMinimo\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precoVenda").value(95))
                .andExpect(jsonPath("$.estoqueMinimo").value(5));
        mockMvc.perform(delete("/produtos/{id}", id).cookie(gerente).with(csrf(gerente)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/produtos/{id}", id).cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void inativaProdutoSemHistoricoEBloqueiaQuandoHaMovimentacaoOuVinculoEmDrink() throws Exception {
        CategoriaEntity bebidas = categoria("Bebidas");
        Cookie gerente = autenticarGerente("gerente-inativar@meraki.com");

        // Produto padrão com saldo inicial zero e lote inicial zerado (cenário do formulário web) deve permitir inativação lógica
        MvcResult semHistoricoRes = mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Água Tônica Zero","categoriaId":%d,"unidadeMedida":"un",
                                 "precoVenda":8.00,"precoCusto":3.50,"tipo":"PADRAO",
                                 "quantidadeInicial":0,"validade":"2027-06-30"}
                                """.formatted(bebidas.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String semHistoricoId = com.jayway.jsonpath.JsonPath.read(
                semHistoricoRes.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(delete("/produtos/{id}", semHistoricoId).cookie(gerente).with(csrf(gerente)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/produtos/{id}", semHistoricoId).cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        // Reativação e inativação via PATCH funcionam quando não há histórico
        mockMvc.perform(patch("/produtos/{id}", semHistoricoId).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(patch("/produtos/{id}", semHistoricoId).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        // Produto padrão criado com saldo inicial > 0 gera ENTRADA_INICIAL e deve bloquear inativação (DELETE e PATCH)
        MvcResult comSaldoRes = mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Cerveja Premium","categoriaId":%d,"unidadeMedida":"un",
                                 "precoVenda":15.00,"precoCusto":8.00,"tipo":"PADRAO","quantidadeInicial":10}
                                """.formatted(bebidas.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String comSaldoId = com.jayway.jsonpath.JsonPath.read(
                comSaldoRes.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(delete("/produtos/{id}", comSaldoId).cookie(gerente).with(csrf(gerente)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/produtos/{id}", comSaldoId).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isConflict());

        // Produto ingrediente sem movimentação mas vinculado a receita de drink deve bloquear inativação;
        // o próprio drink sem histórico pode ser inativado
        MvcResult ingredienteRes = mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Xarope de Hortelã","categoriaId":%d,"unidadeMedida":"un",
                                 "precoVenda":35.00,"precoCusto":15.00,"tipo":"PADRAO",
                                 "fracionado":true,"volumeEmbalagemMl":1000,"quantidadeInicial":0}
                                """.formatted(bebidas.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String ingredienteId = com.jayway.jsonpath.JsonPath.read(
                ingredienteRes.getResponse().getContentAsString(), "$.id").toString();

        MvcResult drinkRes = mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Soda Italiana","categoriaId":%d,"unidadeMedida":"copo",
                                 "precoVenda":18.00,"tipo":"DRINK"}
                                """.formatted(bebidas.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String drinkId = com.jayway.jsonpath.JsonPath.read(
                drinkRes.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/drinks/{idDrink}/receita", drinkId).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredientes\":[{\"produtoId\":\"%s\",\"quantidade\":30,\"unidadeConsumo\":\"ML\"}]}"
                                .formatted(ingredienteId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/produtos/{id}", ingredienteId).cookie(gerente).with(csrf(gerente)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/produtos/{id}", drinkId).cookie(gerente).with(csrf(gerente)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/produtos/{id}", drinkId).cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void rejeitaCriacaoSemCsrfEDadosInvalidos() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        Cookie gerente = autenticarGerente("csrf-produtos@meraki.com");
        mockMvc.perform(post("/produtos").cookie(gerente).contentType(MediaType.APPLICATION_JSON)
                        .content(novoProduto(categoria.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Inválido\",\"categoriaId\":%d,\"unidadeMedida\":\"un\",\"precoVenda\":-1}".formatted(categoria.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.precoVenda").exists());
    }

    @Test
    void filtraPorCategoriasExportaExcelEConverteFardo() throws Exception {
        CategoriaEntity bebidas = categoria("Bebidas");
        CategoriaEntity destilados = categoria("Destilados");
        Cookie semPermissao = autenticarComPermissoes("sem-export@meraki.com");
        Cookie gerente = autenticarGerente("gerente-export@meraki.com");

        mockMvc.perform(get("/produtos/exportar").cookie(semPermissao))
                .andExpect(status().isForbidden());

        MvcResult criado = mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Brahma Lata","categoriaId":%d,"unidadeMedida":"un",
                                 "precoVenda":5.50,"precoCusto":3.00,"tipo":"PADRAO",
                                 "fardo":true,"unidadesPorFardo":12,"quantidadeInicial":2,"quantidadeInicialEmFardos":true}
                                """.formatted(bebidas.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fardo").value(true))
                .andExpect(jsonPath("$.unidadesPorFardo").value(12))
                .andExpect(jsonPath("$.quantidadeEstoque").value(24))
                .andReturn();
        String idBrahma = com.jayway.jsonpath.JsonPath.read(criado.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(novoProduto(destilados.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/produtos")
                        .param("categoriaId", String.valueOf(bebidas.getId()))
                        .cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Brahma Lata"));

        mockMvc.perform(get("/produtos")
                        .param("categoriaId", String.valueOf(bebidas.getId()), String.valueOf(destilados.getId()))
                        .cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2));

        MvcResult exportado = mockMvc.perform(get("/produtos/exportar")
                        .param("ids", idBrahma)
                        .cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn();

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(exportado.getResponse().getContentAsByteArray()))) {
            var sheet = wb.getSheet("Produtos");
            org.assertj.core.api.Assertions.assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Brahma Lata");
        }
    }

    @Test
    void criaProdutoComLoteEValidadeSalvaNoBancoERetornaNaResposta() throws Exception {
        CategoriaEntity cervejas = categoria("Cervejas");
        Cookie gerente = autenticarGerente("gerente-lote-produto@meraki.com");

        MvcResult criado = mockMvc.perform(post("/produtos").cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Heineken 600ml","categoriaId":%d,"unidadeMedida":"un",
                                 "precoVenda":15.00,"precoCusto":9.00,"tipo":"PADRAO",
                                 "quantidadeInicial":18,"lote":"L2611A","validade":"2027-06-30"}
                                """.formatted(cervejas.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lote").value("L2611A"))
                .andExpect(jsonPath("$.validade").value("2027-06-30"))
                .andExpect(jsonPath("$.quantidadeEstoque").value(18))
                .andReturn();

        String produtoId = com.jayway.jsonpath.JsonPath.read(criado.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/lotes").param("produtoId", produtoId).cookie(gerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].codigo").value("L2611A"))
                .andExpect(jsonPath("$.itens[0].validade").value("2027-06-30"))
                .andExpect(jsonPath("$.itens[0].quantidadeAtual").value(18));
    }

    private String novoProduto(int categoriaId) {
        return """
                {"nome":"Vodka Absolut","categoriaId":%d,"unidadeMedida":"garrafa",
                 "precoVenda":90.00,"precoCusto":0,"tipo":"PADRAO","fracionado":true,
                 "volumeEmbalagemMl":1000,"quantidadeInicial":0}
                """.formatted(categoriaId);
    }
}
