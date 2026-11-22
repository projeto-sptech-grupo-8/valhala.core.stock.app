package valhalla.core.stock.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validaEExportaContratoOpenApiEmYamlQuandoSolicitado() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn();

        String yaml = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml)
                .contains("openapi:")
                .contains("/categorias:")
                .contains("/produtos:")
                .contains("/drinks/{idDrink}/receita:")
                .contains("/movimentacoes-estoque:")
                .contains("/lotes:")
                .contains("/lotes/resumo:")
                .contains("/dashboard/resumo:")
                .contains("/caixa/configuracao:")
                .contains("/caixa/caixas:")
                .contains("/caixa/caixas/{caixaId}/terminais:")
                .contains("/caixa/taxas:")
                .contains("/caixa/sessoes:")
                .contains("/caixa/sessoes/atual:")
                .contains("/caixa/sessoes/{id}/movimentacoes:")
                .contains("/caixa/sessoes/{id}/fechamento:")
                .contains("/caixa/sessoes/{id}/resolucao-divergencia:")
                .contains("/caixa/auditoria:")
                .contains("/caixa/terminais/mercado-pago:")
                .contains("/caixa/terminais/{id}/modo-operacao:")
                .contains("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/cancelamento-point:")
                .contains("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/sincronizar:")
                .contains("/webhooks/mercado-pago/point:")
                .contains("/auth/csrf:")
                .contains("/estabelecimento:")
                .contains("csrfTokenHeader:")
                .doesNotContain("/dev/mercado-pago/point/");

        String caminhoDeExportacao = System.getProperty("openapi.export.path");
        if (caminhoDeExportacao != null && !caminhoDeExportacao.isBlank()) {
            Files.writeString(Path.of(caminhoDeExportacao), yaml);
        }
    }
}
