package valhalla.core.stock.app.modules.estoque;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReceitaDrinkIntegrationTest extends EstoqueIntegrationTestSupport {

    @Test
    void endpointsDeReceitaExigemPermissoesDeLeituraEGerenciamento() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity drink = drink("Vodka Energy", categoria);
        ProdutoEntity ingrediente = produtoPadrao("Red Bull", categoria, false, "10");
        Cookie semPermissao = autenticarComPermissoes("sem-receita@meraki.com");
        String receitaValida = "{\"ingredientes\":[{\"produtoId\":\"%s\",\"quantidade\":1,\"unidadeConsumo\":\"UN\"}]}"
                .formatted(ingrediente.getId());

        mockMvc.perform(get("/drinks/{id}/receita", drink.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/drinks/{id}/receita", drink.getId()).cookie(semPermissao)).andExpect(status().isForbidden());
        mockMvc.perform(put("/drinks/{id}/receita", drink.getId()).cookie(semPermissao).with(csrf(semPermissao))
                        .contentType(MediaType.APPLICATION_JSON).content(receitaValida))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteSubstituiEConsultaReceitaComUnidadesCorretas() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity drink = drink("Vodka Energy", categoria);
        ProdutoEntity vodka = produtoPadrao("Vodka Absolut", categoria, true, "1000");
        ProdutoEntity energetico = produtoPadrao("Red Bull", categoria, false, "24");
        Cookie gerente = autenticarGerente("gerente-receitas@meraki.com");

        mockMvc.perform(put("/drinks/{id}/receita", drink.getId()).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON).content(receita(vodka, energetico)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].produtoId").value(vodka.getId().toString()))
                .andExpect(jsonPath("$[0].unidadeConsumo").value("ML"))
                .andExpect(jsonPath("$[1].unidadeConsumo").value("UN"));

        mockMvc.perform(get("/drinks/{id}/receita", drink.getId()).cookie(gerente))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejeitaReceitaSemCsrfEUnidadeIncompativel() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity drink = drink("Vodka Energy", categoria);
        ProdutoEntity vodka = produtoPadrao("Vodka Absolut", categoria, true, "1000");
        Cookie gerente = autenticarGerente("csrf-receita@meraki.com");
        String corpo = "{\"ingredientes\":[{\"produtoId\":\"%s\",\"quantidade\":50,\"unidadeConsumo\":\"ML\"}]}"
                .formatted(vodka.getId());

        mockMvc.perform(put("/drinks/{id}/receita", drink.getId()).cookie(gerente)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/drinks/{id}/receita", drink.getId()).cookie(gerente).with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.replace("\"ML\"", "\"UN\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingrediente Vodka Absolut deve usar a unidade ML"));
    }

    private String receita(ProdutoEntity vodka, ProdutoEntity energetico) {
        return """
                {"ingredientes":[
                  {"produtoId":"%s","quantidade":50,"unidadeConsumo":"ML"},
                  {"produtoId":"%s","quantidade":1,"unidadeConsumo":"UN"}
                ]}
                """.formatted(vodka.getId(), energetico.getId());
    }
}
