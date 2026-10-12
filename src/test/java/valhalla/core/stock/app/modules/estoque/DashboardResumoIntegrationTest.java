package valhalla.core.stock.app.modules.estoque;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardResumoIntegrationTest extends EstoqueIntegrationTestSupport {

    @Test
    void retornaResumoDeEstoqueDoEstabelecimentoAutenticado() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity critico = produtoPadrao("Espumante", categoria, false, "3");
        ProdutoEntity zerado = produtoPadrao("Gin", categoria, false, "0");
        ProdutoEntity saudavel = produtoPadrao("Agua", categoria, false, "12");
        definirEstoqueMinimo(critico, "6");
        definirEstoqueMinimo(zerado, "3");
        definirEstoqueMinimo(saudavel, "6");

        Cookie operador = autenticarComPermissoes(
                "dashboard@meraki.com", "VISUALIZAR_ESTOQUE", "MOVIMENTAR_ESTOQUE"
        );
        registrarMovimentacao(critico, operador, "SAIDA", "1");
        registrarMovimentacao(saudavel, operador, "ENTRADA", "1");

        String mesAtual = YearMonth.now().toString();
        mockMvc.perform(get("/dashboard/resumo").cookie(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referencia.fusoHorario").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.estoque.totalProdutos").value(3))
                .andExpect(jsonPath("$.estoque.criticos").value(2))
                .andExpect(jsonPath("$.estoque.zerados").value(1))
                .andExpect(jsonPath("$.estoque.saudaveis").value(1))
                .andExpect(jsonPath("$.estoque.saudePercentual").value(33))
                .andExpect(jsonPath("$.estoque.alertas.length()").value(2))
                .andExpect(jsonPath("$.estoque.alertas[0].status").value("ZERADO"))
                .andExpect(jsonPath("$.movimentacoes.ultimas.length()").value(2))
                .andExpect(jsonPath("$.movimentacoes.serieMensal[5].mes").value(mesAtual))
                .andExpect(jsonPath("$.movimentacoes.serieMensal[5].entradas").value(1))
                .andExpect(jsonPath("$.movimentacoes.serieMensal[5].saidas").value(1))
                .andExpect(jsonPath("$.financeiro.status").value("INDISPONIVEL"))
                .andExpect(jsonPath("$.caixa.status").value("INDISPONIVEL"));
    }

    @Test
    void exigePermissaoParaVisualizarResumo() throws Exception {
        Cookie semPermissao = autenticarComPermissoes("sem-dashboard@meraki.com");

        mockMvc.perform(get("/dashboard/resumo"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/dashboard/resumo").cookie(semPermissao))
                .andExpect(status().isForbidden());
    }

    @Test
    void trataEstoqueMinimoZeroComoNaoConfigurado() throws Exception {
        CategoriaEntity categoria = categoria("Bebidas");
        ProdutoEntity produto = produtoPadrao("Produto sem minimo", categoria, false, "0");
        definirEstoqueMinimo(produto, "0");
        Cookie operador = autenticarComPermissoes("minimo-zero@meraki.com", "VISUALIZAR_ESTOQUE");

        mockMvc.perform(get("/dashboard/resumo").cookie(operador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estoque.totalProdutos").value(1))
                .andExpect(jsonPath("$.estoque.produtosComMinimoDefinido").value(0))
                .andExpect(jsonPath("$.estoque.produtosSemMinimoDefinido").value(1))
                .andExpect(jsonPath("$.estoque.criticos").value(0))
                .andExpect(jsonPath("$.estoque.zerados").value(0))
                .andExpect(jsonPath("$.estoque.alertas.length()").value(0));
    }

    private void definirEstoqueMinimo(ProdutoEntity produto, String minimo) {
        EstoqueEntity estoque = estoqueRepository.findByProdutoId(produto.getId()).orElseThrow();
        estoque.setEstoqueMinimo(new BigDecimal(minimo));
        estoqueRepository.saveAndFlush(estoque);
    }

    private void registrarMovimentacao(
            ProdutoEntity produto,
            Cookie operador,
            String tipo,
            String quantidade
    ) throws Exception {
        String corpo = "{\"produtoId\":\"%s\",\"tipo\":\"%s\",\"quantidade\":%s}"
                .formatted(produto.getId(), tipo, quantidade);
        mockMvc.perform(post("/movimentacoes-estoque").cookie(operador).with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated());
    }
}
