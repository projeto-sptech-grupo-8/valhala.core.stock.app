package valhalla.core.stock.app.modules.estoque;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovimentacaoEstoqueIntegrationTest extends EstoqueIntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private ComposicaoDrinkRepository composicaoDrinkRepository;

    @Test
    void endpointsDeMovimentacaoExigemPermissoesCorretas() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity produto = produtoPadrao("Red Bull", categoria, false, "10");
        Cookie semPermissao = autenticarComPermissoes("sem-movimentacao@meraki.com");
        String corpo = movimentacao(produto, "SAIDA", "1");

        mockMvc.perform(get("/movimentacoes-estoque")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/movimentacoes-estoque").cookie(semPermissao)).andExpect(status().isForbidden());
        mockMvc.perform(post("/movimentacoes-estoque").cookie(semPermissao).with(csrf(semPermissao))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/movimentacoes-estoque/saida-drink").cookie(semPermissao).with(csrf(semPermissao))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"drinkId\":\"%s\",\"quantidade\":1}".formatted(produto.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorMovimentaProdutoEListaHistoricoFiltrado() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity produto = produtoPadrao("Red Bull", categoria, false, "10");
        Cookie operador = autenticarComPermissoes("operador-movimentacao@meraki.com", "MOVIMENTAR_ESTOQUE", "VISUALIZAR_ESTOQUE");

        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON).content(movimentacao(produto, "SAIDA", "2")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("SAIDA"))
                .andExpect(jsonPath("$.usuarioNome").value("Operador Meraki"))
                .andExpect(jsonPath("$.usuarioId").doesNotExist())
                .andExpect(jsonPath("$.saldoAnterior").value(10)).andExpect(jsonPath("$.saldoPosterior").value(8));
        mockMvc.perform(get("/movimentacoes-estoque").param("produtoId", produto.getId().toString())
                        .param("tipo", "SAIDA").param("tamanho", "1").cookie(operador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].produtoNome").value("Red Bull"));
    }

    @Test
    void saidaDeDrinkBaixaTodosIngredientesEExigeCsrf() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity drink = drink("Vodka Energy", categoria);
        ProdutoEntity vodka = produtoPadrao("Vodka Absolut", categoria, true, "1000");
        ProdutoEntity energetico = produtoPadrao("Red Bull", categoria, false, "10");
        salvarIngrediente(drink, vodka, "50", UnidadeConsumoDrink.ML);
        salvarIngrediente(drink, energetico, "1", UnidadeConsumoDrink.UN);
        Cookie operador = autenticarComPermissoes("operador-drink@meraki.com", "MOVIMENTAR_ESTOQUE");
        String corpo = "{\"drinkId\":\"%s\",\"quantidade\":2,\"motivo\":\"Mesa 4\"}".formatted(drink.getId());

        mockMvc.perform(post("/movimentacoes-estoque/saida-drink").cookie(operador)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/movimentacoes-estoque/saida-drink").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("SAIDA_DRINK"))
                .andExpect(jsonPath("$[0].receitaAplicada.drinkNome").value("Vodka Energy"));
    }

    private String movimentacao(ProdutoEntity produto, String tipo, String quantidade) {
        return "{\"produtoId\":\"%s\",\"tipo\":\"%s\",\"quantidade\":%s,\"motivo\":\"Teste\"}"
                .formatted(produto.getId(), tipo, quantidade);
    }

    private void salvarIngrediente(ProdutoEntity drink, ProdutoEntity ingrediente, String quantidade, UnidadeConsumoDrink unidade) {
        ComposicaoDrinkEntity composicao = new ComposicaoDrinkEntity();
        composicao.setDrink(drink);
        composicao.setProduto(ingrediente);
        composicao.setQuantidade(new BigDecimal(quantidade));
        composicao.setUnidadeConsumo(unidade);
        composicaoDrinkRepository.saveAndFlush(composicao);
    }
}
