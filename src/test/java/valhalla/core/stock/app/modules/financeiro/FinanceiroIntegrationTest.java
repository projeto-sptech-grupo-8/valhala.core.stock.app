package valhalla.core.stock.app.modules.financeiro;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;
import valhalla.core.stock.app.support.PostgresIntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceiroIntegrationTest extends PostgresIntegrationTestSupport {

    private Cookie gerente;
    private ProdutoEntity cerveja;
    private ProdutoEntity vodka;
    private LocalDate hoje;

    @BeforeEach
    void setUp() throws Exception {
        gerente = autenticarGerente("gerente@meraki.com");
        cerveja = produtoPadrao("Cerveja Lata", categoria("Bebidas"), false, "10");
        vodka = produtoPadrao("Vodka", categoria("Destilados"), false, "5");
        hoje = FusoHorarioOperacao.agora().toLocalDate();
    }

    @Test
    void resumoEResultadoDevemCombinarVendasEContas() throws Exception {
        // venda de 80 (custo 40), paga no caixa
        vender(gerente, "BALCAO", null, cerveja, 3, vodka, 1);
        // venda de 20 estornada: entra na receita bruta e nos cancelamentos, não no caixa
        String estornada = vender(gerente, "MESA", null, cerveja, 1);
        estornar(gerente, "gerente@meraki.com", JsonPath.read(estornada, "$.id"));

        conta("PAGAR", "Aluguel", "500", hoje.plusDays(5), "OPERACIONAL", "DESPESA_FIXA");
        conta("PAGAR", "Distribuidora", "300", hoje.minusDays(2), "COMPRAS", "MERCADORIA");
        String receber = conta("RECEBER", "Evento corporativo", "200", hoje.plusDays(10), "VENDAS", null);
        enviar(gerente, "/financeiro/contas/" + JsonPath.read(receber, "$.id") + "/baixa", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGA"))
                .andExpect(jsonPath("$.valorPago").value(200.00));
        String taxas = conta("PAGAR", "Taxas de cartão", "100", hoje, "VENDAS", "DESPESA_VARIAVEL");
        enviar(gerente, "/financeiro/contas/" + JsonPath.read(taxas, "$.id") + "/baixa", "{\"valorPago\": 90}")
                .andExpect(status().isOk());
        String inativar = conta("PAGAR", "Lançada errada", "50", hoje, "ADMINISTRATIVO", "DESPESA_FIXA");
        enviarPatch(gerente, "/financeiro/contas/" + JsonPath.read(inativar, "$.id") + "/inativacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVA"));

        String resumo = getJson("/financeiro/resumo");
        // saldo: venda recebida (80) + recebido (200) − pago (90); o pagamento estornado fica fora
        assertThat(numero(resumo, "$.saldoRealizado")).isEqualByComparingTo("190");
        assertThat(numero(resumo, "$.aPagar30Dias.valor")).isEqualByComparingTo("800");
        assertThat(numero(resumo, "$.aPagar30Dias.quantidade")).isEqualByComparingTo("2");
        assertThat(numero(resumo, "$.aPagar30Dias.vencidas")).isEqualByComparingTo("1");
        assertThat(numero(resumo, "$.aPagar30Dias.vencemEmAte7Dias")).isEqualByComparingTo("1");
        assertThat(numero(resumo, "$.aReceber30Dias.valor")).isEqualByComparingTo("0");
        assertThat(numero(resumo, "$.coberturaContasAPagar")).isEqualByComparingTo("0.24");
        assertThat(numero(resumo, "$.faturamento.mesAtual")).isEqualByComparingTo("80");
        assertThat(numero(resumo, "$.faturamento.cmvMesAtual")).isEqualByComparingTo("40");
        assertThat(numero(resumo, "$.faturamento.cmvPercentual")).isEqualByComparingTo("50");
        assertThat((Object) JsonPath.read(resumo, "$.faturamento.crescimentoPercentual")).isNull();
        List<Object> fluxo = JsonPath.read(resumo, "$.fluxoCaixa");
        assertThat(fluxo).hasSize(6);
        assertThat(numero(resumo, "$.fluxoCaixa[5].receitas")).isEqualByComparingTo("280");
        assertThat(numero(resumo, "$.fluxoCaixa[5].despesas")).isEqualByComparingTo("90");
        assertThat((String) JsonPath.read(resumo, "$.fluxoCaixa[5].mes")).isEqualTo(hoje.toString().substring(0, 7));

        String resultado = getJson("/financeiro/resultado?inicio=%s&fim=%s"
                .formatted(hoje.minusDays(40), hoje.plusDays(40)));
        assertThat(numero(resultado, "$.receitaBruta")).isEqualByComparingTo("100");
        assertThat(numero(resultado, "$.cancelamentos")).isEqualByComparingTo("20");
        assertThat(numero(resultado, "$.receitaLiquida")).isEqualByComparingTo("80");
        assertThat(numero(resultado, "$.cmv")).isEqualByComparingTo("40");
        assertThat(numero(resultado, "$.margemBrutaPercentual")).isEqualByComparingTo("50");
        // mercadoria e conta cancelada ficam fora; a variável entra pelo valor pago
        assertThat(numero(resultado, "$.despesasFixas")).isEqualByComparingTo("500");
        assertThat(numero(resultado, "$.despesasVariaveis")).isEqualByComparingTo("90");
        assertThat(numero(resultado, "$.lucroLiquido")).isEqualByComparingTo("-550");
        assertThat(numero(resultado, "$.vendasPorCategoria[0].valor")).isEqualByComparingTo("60");
        assertThat(numero(resultado, "$.vendasPorCategoria[0].participacaoPercentual")).isEqualByComparingTo("75");
        assertThat((String) JsonPath.read(resultado, "$.vendasPorCategoria[1].categoria")).isEqualTo("Destilados");
    }

    @Test
    void contasDevemRespeitarAsRegrasDeCadastroEBaixa() throws Exception {
        enviar(gerente, "/financeiro/contas", corpoConta("PAGAR", "Sem categoria", "10", hoje, "COMPRAS", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe a categoria da conta a pagar"));
        enviar(gerente, "/financeiro/contas", corpoConta("RECEBER", "Com categoria", "10", hoje, "VENDAS", "DESPESA_FIXA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Contas a receber não têm categoria"));

        String conta = conta("PAGAR", "Contador", "400", hoje.plusDays(3), "ADMINISTRATIVO", "DESPESA_FIXA");
        String id = JsonPath.read(conta, "$.id");
        assertThat((Integer) JsonPath.read(conta, "$.diasParaVencer")).isEqualTo(3);

        put(id, corpoConta("RECEBER", "Contador", "400", hoje, "ADMINISTRATIVO", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O tipo da conta não pode ser alterado"));
        put(id, corpoConta("PAGAR", "Contador mensal", "450", hoje.plusDays(4), "ADMINISTRATIVO", "DESPESA_FIXA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Contador mensal"))
                .andExpect(jsonPath("$.valor").value(450));

        enviar(gerente, "/financeiro/contas/" + id + "/baixa", "{\"pagoEm\": \"%s\"}".formatted(hoje.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A data do pagamento não pode ser futura"));
        enviar(gerente, "/financeiro/contas/" + id + "/baixa", "{}").andExpect(status().isOk());
        enviar(gerente, "/financeiro/contas/" + id + "/baixa", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A conta já foi baixada"));
        put(id, corpoConta("PAGAR", "Contador", "1", hoje, "ADMINISTRATIVO", "DESPESA_FIXA"))
                .andExpect(status().isBadRequest());

        conta("PAGAR", "Energia", "200", hoje.plusDays(1), "OPERACIONAL", "DESPESA_FIXA");
        conta("PAGAR", "Água", "80", hoje.minusDays(1), "OPERACIONAL", "DESPESA_FIXA");
        String contaInativar = conta("PAGAR", "Conta para inativar", "150", hoje.plusDays(2), "OPERACIONAL", "DESPESA_FIXA");
        String idInativar = JsonPath.read(contaInativar, "$.id");
        enviarPatch(gerente, "/financeiro/contas/" + idInativar + "/inativacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVA"));
        enviar(gerente, "/financeiro/contas/" + idInativar + "/baixa", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A conta está inativa"));
        enviarPatch(gerente, "/financeiro/contas/" + idInativar + "/inativacao")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A conta está inativa"));
        put(idInativar, corpoConta("PAGAR", "Editada", "150", hoje, "OPERACIONAL", "DESPESA_FIXA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A conta está inativa"));

        String lista = getJson("/financeiro/contas?tipo=PAGAR&status=ABERTA");
        assertThat((List<String>) JsonPath.read(lista, "$.itens[*].descricao")).containsExactly("Água", "Energia");
    }

    @Test
    void financeiroExigePermissoesProprias() throws Exception {
        Cookie estoquista = autenticarComPermissoes("estoque@meraki.com", "VISUALIZAR_ESTOQUE");
        mockMvc.perform(get("/financeiro/resumo").cookie(estoquista)).andExpect(status().isForbidden());

        Cookie leitor = autenticarComPermissoes("leitor@meraki.com", "VISUALIZAR_FINANCEIRO");
        mockMvc.perform(get("/financeiro/resumo").cookie(leitor)).andExpect(status().isOk());
        enviar(leitor, "/financeiro/contas", corpoConta("PAGAR", "Aluguel", "10", hoje, "OPERACIONAL", "DESPESA_FIXA"))
                .andExpect(status().isForbidden());

        String conta = conta("PAGAR", "Assinatura", "40", hoje.plusDays(3), "ADMINISTRATIVO", "DESPESA_FIXA");
        enviarPatch(leitor, "/financeiro/contas/" + JsonPath.read(conta, "$.id") + "/inativacao")
                .andExpect(status().isForbidden());
    }

    private String conta(String tipo, String descricao, String valor, LocalDate vencimento, String centro,
                         String categoria) throws Exception {
        return enviar(gerente, "/financeiro/contas", corpoConta(tipo, descricao, valor, vencimento, centro, categoria))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String corpoConta(String tipo, String descricao, String valor, LocalDate vencimento,
                                     String centro, String categoria) {
        return """
                {"tipo": "%s", "descricao": "%s", "valor": %s, "vencimento": "%s", "centroCusto": "%s"%s}
                """.formatted(tipo, descricao, valor, vencimento, centro,
                categoria == null ? "" : ", \"categoria\": \"%s\"".formatted(categoria));
    }

    private ResultActions enviar(Cookie usuario, String url, String corpo) throws Exception {
        return mockMvc.perform(post(url).cookie(usuario).with(csrf(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions enviarPatch(Cookie usuario, String url) throws Exception {
        return mockMvc.perform(patch(url).cookie(usuario).with(csrf(usuario)));
    }

    private ResultActions put(String idConta, String corpo) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/financeiro/contas/" + idConta).cookie(gerente).with(csrf(gerente))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private String getJson(String url) throws Exception {
        entityManager.flush();
        return mockMvc.perform(get(url).cookie(gerente))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static BigDecimal numero(String json, String caminho) {
        return new BigDecimal(String.valueOf((Object) JsonPath.read(json, caminho)));
    }
}
