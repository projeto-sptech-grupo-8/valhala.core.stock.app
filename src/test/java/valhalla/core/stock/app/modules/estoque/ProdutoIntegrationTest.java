package valhalla.core.stock.app.modules.estoque;

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

    private String novoProduto(int categoriaId) {
        return """
                {"nome":"Vodka Absolut","categoriaId":%d,"unidadeMedida":"garrafa",
                 "precoVenda":90.00,"precoCusto":0,"tipo":"PADRAO","fracionado":true,
                 "volumeEmbalagemMl":1000,"quantidadeInicial":0}
                """.formatted(categoriaId);
    }
}
