package valhalla.core.stock.app.modules.caixa.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TaxaPagamentoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.MovimentacaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.TipoMovimentacaoCaixa;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PedidoCaixaIntegrationTest extends CaixaIntegrationTestSupport {

    private static final String[] PERMISSOES_ATENDENTE = {
            "CAIXA_OPERAR", "CAIXA_VISUALIZAR", "CAIXA_FECHAR", "CAIXA_CANCELAR"
    };

    @Test
    void criaPedidosSimultaneosValidaEstoqueCongelaCustoECategoriaEAuditaItemRemovido() throws Exception {
        CaixaEntity caixa = caixa(estabelecimento, "Caixa Balcão");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.pedidos@meraki.com",
                PERMISSOES_ATENDENTE
        );
        String sessaoId = abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity catBebidas = categoria("Cervejas");
        CategoriaEntity catDrinks = categoria("Drinks Autorais");

        ProdutoEntity heineken = produtoPadrao(
                "Heineken 600ml", catBebidas, false, "8.50", "18.00", null, "10.000"
        );
        ProdutoEntity gin = produtoPadrao(
                "Gin Tanqueray 1L", catBebidas, true, "100.00", "220.00", "1000.000", "1000.000"
        );
        ProdutoEntity ginTonica = drinkComReceita(
                "Gin Tônica Meraki", catDrinks, "32.00", gin, "50.000", UnidadeConsumoDrink.ML
        );

        // Canal MESA exige identificadorAtendimento
        mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "O identificador do atendimento (ex.: Mesa 04) é obrigatório quando o canal é MESA"
                ));

        // Estoque insuficiente ao tentar pedir 15 Heinekens (saldo = 10)
        mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "identificadorAtendimento": "Balcão 1",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 15}
                                  ]
                                }
                                """.formatted(heineken.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.estoque").exists());

        // Cria Pedido #1 (Mesa 04) com 2 Heinekens (R$ 36,00) + 1 Gin Tônica (R$ 32,00, custo congelado = 50/1000 * 100 = R$ 5,00)
        MvcResult criacao1 = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA",
                                  "identificadorAtendimento": "Mesa 04",
                                  "observacao": "Sem gelo na cerveja",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 2},
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(heineken.getId(), ginTonica.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.sessaoCaixaId").value(sessaoId))
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.canal").value("MESA"))
                .andExpect(jsonPath("$.identificadorAtendimento").value("Mesa 04"))
                .andExpect(jsonPath("$.subtotal").value(68.0))
                .andExpect(jsonPath("$.total").value(68.0))
                .andExpect(jsonPath("$.saldoRestante").value(68.0))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].custoUnitario").value(8.5))
                .andExpect(jsonPath("$.itens[0].categoriaNome").value("Cervejas"))
                .andExpect(jsonPath("$.itens[1].custoUnitario").value(5.0))
                .andExpect(jsonPath("$.itens[1].categoriaNome").value("Drinks Autorais"))
                .andReturn();

        String pedido1Id = JsonPath.read(criacao1.getResponse().getContentAsString(), "$.id");

        // Cria Pedido #2 simultâneo (Mesa 07)
        mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA",
                                  "identificadorAtendimento": "Mesa 07",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(heineken.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.total").value(18.0));

        // Lista pedidos simultâneos da sessão
        mockMvc.perform(get("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .param("sessaoCaixaId", sessaoId)
                        .param("status", "EM_ATENDIMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2));

        // Atualiza Pedido #1 reduzindo Heineken de 2 para 1 -> audita ITEM_REMOVIDO
        mockMvc.perform(put("/caixa/pedidos/{id}", pedido1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 1},
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(heineken.getId(), ginTonica.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(50.0))
                .andExpect(jsonPath("$.total").value(50.0));

        assertThat(auditorias())
                .anyMatch(a -> "ITEM_REMOVIDO".equals(a.getTipoEvento())
                        && pedido1Id.equals(a.getPedidoId().toString())
                        && a.getValor().compareTo(new BigDecimal("18.00")) == 0);
    }

    @Test
    void fluxoHibridoDeDescontoAprovaDentroDoLimitePedeAutorizacaoAprovaNegaERemoveDesconto() throws Exception {
        CaixaEntity caixa = caixa(estabelecimento, "Caixa Descontos");
        ConfiguracaoCaixaEntity config = configuracao(estabelecimento);
        config.setLimiteDescontoPercentual(new BigDecimal("10.00"));
        config.setLimiteDescontoValor(new BigDecimal("15.00"));
        configuracaoCaixaRepository.saveAndFlush(config);

        UsuarioAutenticado atendente = autenticarComPermissoes(
                "atendente.desconto@meraki.com",
                PERMISSOES_ATENDENTE
        );
        UsuarioAutenticado gerente = autenticarGerente("gerente.desconto@meraki.com");

        abrirSessao(atendente, caixa.getId(), "50.00");

        CategoriaEntity cat = categoria("Porções");
        ProdutoEntity porcao = produtoPadrao(
                "Tábua Valhalla", cat, false, "40.00", "100.00", null, "20.000"
        );

        MvcResult criacao = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(atendente.accessToken())
                        .with(csrf(atendente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "identificadorAtendimento": "Comanda 10",
                                  "itens": [{"produtoId": "%s", "quantidade": 1}]
                                }
                                """.formatted(porcao.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String pedidoId = JsonPath.read(criacao.getResponse().getContentAsString(), "$.id");

        // 1) Desconto dentro do limite duplo: subtotal = 100, 10% = R$ 10,00 <= min(10%, R$ 15) = R$ 10,00
        mockMvc.perform(put("/caixa/pedidos/{id}/desconto", pedidoId)
                        .cookie(atendente.accessToken())
                        .with(csrf(atendente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoDesconto": "PERCENTUAL",
                                  "valorDescontoInformado": 10.00,
                                  "motivo": "Cliente fidelidade"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDesconto").value("APROVADO"))
                .andExpect(jsonPath("$.desconto").value(10.0))
                .andExpect(jsonPath("$.total").value(90.0));

        // 2) Desconto acima do limite (R$ 25,00 > R$ 10,00) sem credenciais do gerente -> fica PENDENTE_AUTORIZACAO
        mockMvc.perform(put("/caixa/pedidos/{id}/desconto", pedidoId)
                        .cookie(atendente.accessToken())
                        .with(csrf(atendente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoDesconto": "VALOR",
                                  "valorDescontoInformado": 25.00,
                                  "motivo": "Aniversariante da mesa"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDesconto").value("PENDENTE_AUTORIZACAO"))
                .andExpect(jsonPath("$.desconto").value(0.0))
                .andExpect(jsonPath("$.descontoSolicitado").value(25.0))
                .andExpect(jsonPath("$.total").value(100.0));

        // Enquanto PENDENTE_AUTORIZACAO, pagamento é bloqueado
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(atendente.accessToken())
                        .with(csrf(atendente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DINHEIRO",
                                  "valor": 100.00,
                                  "valorRecebido": 100.00
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "O pedido possui um desconto pendente de autorização. Aguarde a decisão do gerente ou remova o desconto para prosseguir"
                ));

        // 3) Gerente nega o desconto pendente -> statusDesconto = NEGADO
        mockMvc.perform(post("/caixa/pedidos/{id}/desconto/decisao", pedidoId)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aprovado": false,
                                  "observacao": "Desconto máximo hoje é 15 reais"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDesconto").value("NEGADO"))
                .andExpect(jsonPath("$.desconto").value(0.0))
                .andExpect(jsonPath("$.total").value(100.0))
                .andExpect(jsonPath("$.observacaoDecisaoDesconto").value("Desconto máximo hoje é 15 reais"));

        // 4) Atendente volta o pedido e retira o desconto (DELETE /desconto) -> NENHUM
        mockMvc.perform(delete("/caixa/pedidos/{id}/desconto", pedidoId)
                        .cookie(atendente.accessToken())
                        .with(csrf(atendente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDesconto").value("NENHUM"))
                .andExpect(jsonPath("$.desconto").value(0.0))
                .andExpect(jsonPath("$.descontoSolicitado").isEmpty())
                .andExpect(jsonPath("$.total").value(100.0));

        // 5) Solicita R$ 20,00 com credenciais do gerente no próprio balcão -> APROVADO na hora
        mockMvc.perform(put("/caixa/pedidos/{id}/desconto", pedidoId)
                        .cookie(atendente.accessToken())
                        .with(csrf(atendente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoDesconto": "VALOR",
                                  "valorDescontoInformado": 20.00,
                                  "motivo": "Cortesia autorizada no balcão",
                                  "autorizadorEmail": "gerente.desconto@meraki.com",
                                  "autorizadorSenha": "%s"
                                }
                                """.formatted(SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDesconto").value("APROVADO"))
                .andExpect(jsonPath("$.desconto").value(20.0))
                .andExpect(jsonPath("$.total").value(80.0))
                .andExpect(jsonPath("$.descontoDecididoPorUsuarioId").value(gerente.usuarioId().toString()));
    }

    @Test
    void processaPagamentoMistoDinheiroEManualComIdempotenciaConciliacaoBaixaDeEstoqueEEstornoTotal() throws Exception {
        CaixaEntity caixa = caixa(estabelecimento, "Caixa Principal PDV");
        ConfiguracaoCaixaEntity config = configuracao(estabelecimento);
        config.setPermitirPagamentoManual(true);
        config.setParcelamentoHabilitado(true);
        config.setParcelasMinimas(1);
        config.setParcelasMaximas(3);
        configuracaoCaixaRepository.saveAndFlush(config);

        TaxaPagamentoEntity taxaCredito2x = new TaxaPagamentoEntity();
        taxaCredito2x.setEstabelecimentoId(estabelecimento.getId());
        taxaCredito2x.setForma(FormaPagamentoCaixa.CREDITO);
        taxaCredito2x.setParcelas(2);
        taxaCredito2x.setTaxaPercentual(new BigDecimal("5.0000"));
        taxaCredito2x.setTaxaFixa(BigDecimal.ZERO);
        taxaCredito2x.setRepassarAoCliente(false);
        taxaCredito2x.setAtivo(true);
        taxaPagamentoRepository.saveAndFlush(taxaCredito2x);

        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.pagamento@meraki.com",
                PERMISSOES_ATENDENTE
        );
        UsuarioAutenticado gerente = autenticarGerente("gerente.estorno@meraki.com");

        String sessaoId = abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity cat = categoria("Bebidas");
        ProdutoEntity cerveja = produtoPadrao(
                "IPA Artesanal", cat, false, "10.00", "25.00", null, "10.000"
        );
        ProdutoEntity vodka = produtoPadrao(
                "Vodka Absolut 1L", cat, true, "80.00", "180.00", "1000.000", "1000.000"
        );
        ProdutoEntity moscowMule = drinkComReceita(
                "Moscow Mule", cat, "30.00", vodka, "60.000", UnidadeConsumoDrink.ML
        );

        // Pedido com 2 IPAs (R$ 50,00) + 1 Moscow Mule (R$ 30,00) = R$ 80,00
        MvcResult criacao = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "MESA",
                                  "identificadorAtendimento": "Mesa 12",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 2},
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(cerveja.getId(), moscowMule.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(80.0))
                .andReturn();
        String pedidoId = JsonPath.read(criacao.getResponse().getContentAsString(), "$.id");

        // 1ª parcela: R$ 30,00 em DINHEIRO (cliente entrega R$ 50,00 -> troco R$ 20,00)
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DINHEIRO",
                                  "valor": 30.00,
                                  "valorRecebido": 50.00,
                                  "chaveIdempotencia": "idem-dinheiro-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGAMENTO_PARCIAL"))
                .andExpect(jsonPath("$.totalPago").value(30.0))
                .andExpect(jsonPath("$.saldoRestante").value(50.0))
                .andExpect(jsonPath("$.pagamentos.length()").value(1))
                .andExpect(jsonPath("$.pagamentos[0].status").value("APROVADO"))
                .andExpect(jsonPath("$.pagamentos[0].valorTroco").value(20.0));

        // Repetição com a mesma chaveIdempotencia não duplica cobrança
        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DINHEIRO",
                                  "valor": 30.00,
                                  "valorRecebido": 50.00,
                                  "chaveIdempotencia": "idem-dinheiro-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentos.length()").value(1));

        // Dinheiro esperado na sessão de caixa já reflete os R$ 30,00 líquidos (100 + 30 = 130)
        mockMvc.perform(get("/caixa/sessoes/{id}", sessaoId).cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumoAtual.dinheiroEsperado").value(130.0));

        // 2ª parcela: R$ 50,00 em CREDITO 2x manual -> fica PENDENTE_CONCILIACAO
        MvcResult pagManual = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "CREDITO",
                                  "valor": 50.00,
                                  "parcelas": 2,
                                  "comprovanteReferencia": "NSU-998877"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGAMENTO_PARCIAL"))
                .andExpect(jsonPath("$.pagamentos.length()").value(2))
                .andExpect(jsonPath("$.pagamentos[1].status").value("PENDENTE_CONCILIACAO"))
                .andExpect(jsonPath("$.pagamentos[1].provedor").value("MANUAL"))
                .andExpect(jsonPath("$.pagamentos[1].valorTaxaEstimada").value(2.5))
                .andExpect(jsonPath("$.pagamentos[1].valorLiquidoEstimado").value(47.5))
                .andReturn();
        String pagamentoManualId = JsonPath.read(pagManual.getResponse().getContentAsString(), "$.pagamentos[1].id");

        // Consulta GET /caixa/pedidos/{id}/pagamentos
        mockMvc.perform(get("/caixa/pedidos/{id}/pagamentos", pedidoId).cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Conciliação manual aprovando exige CAIXA_RESOLVER_DIVERGENCIA (operador sem permissão -> 403)
        mockMvc.perform(post("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/conciliacao", pedidoId, pagamentoManualId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aprovado": true,
                                  "motivo": "Comprovante POS conferido"
                                }
                                """))
                .andExpect(status().isForbidden());

        // Conciliação manual aprovada por gerente -> Pedido passa para CONCLUIDO e baixa estoque!
        mockMvc.perform(post("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/conciliacao", pedidoId, pagamentoManualId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aprovado": true,
                                  "motivo": "Comprovante POS conferido",
                                  "autorizadorEmail": "gerente.estorno@meraki.com",
                                  "autorizadorSenha": "%s"
                                }
                                """.formatted(SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.totalPago").value(80.0))
                .andExpect(jsonPath("$.saldoRestante").value(0.0))
                .andExpect(jsonPath("$.concluidoEm").isNotEmpty());

        // Verifica baixa no estoque: IPA (10 - 2 = 8), Vodka (1000ml - 60ml = 940ml)
        EstoqueEntity estoqueCerveja = estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow();
        EstoqueEntity estoqueVodka = estoqueRepository.findByProdutoId(vodka.getId()).orElseThrow();
        assertThat(estoqueCerveja.getQuantidadeAtual()).isEqualByComparingTo("8.000");
        assertThat(estoqueVodka.getQuantidadeAtual()).isEqualByComparingTo("940.000");

        // Estorno total do pedido concluído (autorizado pelo gerente) -> devolve estoque e marca ESTORNADO
        mockMvc.perform(post("/caixa/pedidos/{id}/estorno", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "Cancelamento solicitado após conclusão",
                                  "autorizadorEmail": "gerente.estorno@meraki.com",
                                  "autorizadorSenha": "%s"
                                }
                                """.formatted(SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"))
                .andExpect(jsonPath("$.estornadoEm").isNotEmpty())
                .andExpect(jsonPath("$.pagamentos[0].status").value("ESTORNADO"))
                .andExpect(jsonPath("$.pagamentos[1].status").value("ESTORNADO"));

        EstoqueEntity estoqueCervejaPosEstorno = estoqueRepository.findByProdutoId(cerveja.getId()).orElseThrow();
        EstoqueEntity estoqueVodkaPosEstorno = estoqueRepository.findByProdutoId(vodka.getId()).orElseThrow();
        assertThat(estoqueCervejaPosEstorno.getQuantidadeAtual()).isEqualByComparingTo("10.000");
        assertThat(estoqueVodkaPosEstorno.getQuantidadeAtual()).isEqualByComparingTo("1000.000");

        List<MovimentacaoEstoqueEntity> movimentacoes = movimentacaoEstoqueRepository.findAll();
        assertThat(movimentacoes)
                .anyMatch(m -> m.getTipo() == TipoMovimentacaoEstoque.SAIDA && "PEDIDO".equals(m.getOrigem()))
                .anyMatch(m -> m.getTipo() == TipoMovimentacaoEstoque.SAIDA_DRINK && "PEDIDO".equals(m.getOrigem()))
                .anyMatch(m -> m.getTipo() == TipoMovimentacaoEstoque.AJUSTE_POSITIVO && "PEDIDO".equals(m.getOrigem()));
    }

    @Test
    void cancelamentoDePedidoVazioLivreEComItensExigePermissaoCaixaCancelar() throws Exception {
        CaixaEntity caixa = caixa(estabelecimento, "Caixa Cancelamento");
        UsuarioAutenticado operadorSemCancelar = autenticarComPermissoes(
                "operador.sem-cancelar@meraki.com",
                "CAIXA_OPERAR", "CAIXA_VISUALIZAR"
        );
        UsuarioAutenticado gerente = autenticarGerente("gerente.cancelar@meraki.com");

        abrirSessao(operadorSemCancelar, caixa.getId(), "50.00");

        // 1) Pedido vazio pode ser descartado sem CAIXA_CANCELAR e sem motivo obrigatório
        MvcResult pedidoVazio = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operadorSemCancelar.accessToken())
                        .with(csrf(operadorSemCancelar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"BALCAO\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String pedidoVazioId = JsonPath.read(pedidoVazio.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/caixa/pedidos/{id}/cancelamento", pedidoVazioId)
                        .cookie(operadorSemCancelar.accessToken())
                        .with(csrf(operadorSemCancelar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        // 2) Pedido com itens exige motivo e CAIXA_CANCELAR (ou credenciais de autorizador)
        CategoriaEntity cat = categoria("Petiscos");
        ProdutoEntity batata = produtoPadrao(
                "Batata Rústica", cat, false, "12.00", "28.00", null, "10.000"
        );

        MvcResult pedidoComItem = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operadorSemCancelar.accessToken())
                        .with(csrf(operadorSemCancelar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "itens": [{"produtoId": "%s", "quantidade": 1}]
                                }
                                """.formatted(batata.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String pedidoComItemId = JsonPath.read(pedidoComItem.getResponse().getContentAsString(), "$.id");

        // Sem permissão CAIXA_CANCELAR e sem autorizador -> 403 Forbidden
        mockMvc.perform(post("/caixa/pedidos/{id}/cancelamento", pedidoComItemId)
                        .cookie(operadorSemCancelar.accessToken())
                        .with(csrf(operadorSemCancelar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente desistiu\"}"))
                .andExpect(status().isForbidden());

        assertThat(JsonPath.<String>read(
                mockMvc.perform(post("/caixa/pedidos/{id}/cancelamento", pedidoComItemId)
                                .cookie(operadorSemCancelar.accessToken())
                                .with(csrf(operadorSemCancelar))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "motivo": "Cliente desistiu",
                                          "autorizadorEmail": "gerente.cancelar@meraki.com",
                                          "autorizadorSenha": "%s"
                                        }
                                        """.formatted(SENHA)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CANCELADO"))
                        .andReturn().getResponse().getContentAsString(),
                "$.canceladoPorUsuarioId"
        )).isEqualTo(gerente.usuarioId().toString());
    }

    @Test
    void vendaPorCaixaConsomeLotesPorFefoBloqueiaVencidoEEstornaParaLotesOriginais() throws Exception {
        CaixaEntity caixa = caixa(estabelecimento, "Caixa FEFO");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.fefo@meraki.com", PERMISSOES_ATENDENTE);
        autenticarGerente("gerente.fefo@meraki.com");
        abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity cat = categoria("Cervejas FEFO");
        ProdutoEntity corona = produtoPadrao(
                "Corona Long Neck", cat, false, "7.00", "16.00", null, "0.000"
        );
        LocalDate hoje = LocalDate.now();
        lote(corona, "LOTE-VENCIDO", hoje.minusDays(2), "3.000");
        lote(corona, "LOTE-PROXIMO", hoje.plusDays(4), "2.000");
        lote(corona, "LOTE-DISTANTE", hoje.plusDays(30), "5.000");

        // Saldo físico = 10, mas dentro da validade = 7 -> pedido de 8 deve ser bloqueado (409)
        mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "itens": [{"produtoId": "%s", "quantidade": 8}]
                                }
                                """.formatted(corona.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.estoque").exists());

        // Pedido de 4 unidades -> consome 2 de LOTE-PROXIMO e 2 de LOTE-DISTANTE; LOTE-VENCIDO fica intacto
        MvcResult criado = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "itens": [{"produtoId": "%s", "quantidade": 4}]
                                }
                                """.formatted(corona.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String pedidoId = JsonPath.read(criado.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DINHEIRO",
                                  "valor": 64.00,
                                  "valorRecebido": 64.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        EstoqueEntity estoqueAposVenda = estoqueRepository.findByProdutoId(corona.getId()).orElseThrow();
        assertThat(estoqueAposVenda.getQuantidadeAtual()).isEqualByComparingTo("6.000");

        List<LoteEstoqueEntity> lotesAposVenda = loteEstoqueRepository.findAllByEstoqueId(estoqueAposVenda.getId());
        assertThat(lotesAposVenda).extracting(LoteEstoqueEntity::getCodigo, LoteEstoqueEntity::getQuantidadeAtual)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("LOTE-VENCIDO", new BigDecimal("3.000")),
                        org.assertj.core.groups.Tuple.tuple("LOTE-PROXIMO", new BigDecimal("0.000")),
                        org.assertj.core.groups.Tuple.tuple("LOTE-DISTANTE", new BigDecimal("3.000"))
                );

        MovimentacaoEstoqueEntity saidaVenda = movimentacaoEstoqueRepository
                .findAllByEstoqueProdutoEstabelecimentoIdOrderByOcorridoEmDesc(estabelecimento.getId())
                .stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoEstoque.SAIDA)
                .findFirst()
                .orElseThrow();
        assertThat(saidaVenda.getLote()).isEqualTo("LOTE-PROXIMO, LOTE-DISTANTE");

        List<MovimentacaoLoteEntity> vinculosSaida = movimentacaoLoteRepository
                .findAllByMovimentacaoIdInOrderByIdAsc(List.of(saidaVenda.getId()));
        assertThat(vinculosSaida).extracting(v -> v.getLote().getCodigo(), MovimentacaoLoteEntity::getQuantidade)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("LOTE-PROXIMO", new BigDecimal("2.000")),
                        org.assertj.core.groups.Tuple.tuple("LOTE-DISTANTE", new BigDecimal("2.000"))
                );

        // Estorno devolve exatamente 2 para LOTE-PROXIMO e 2 para LOTE-DISTANTE (não vai para SEM-LOTE)
        mockMvc.perform(post("/caixa/pedidos/{id}/estorno", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "Teste estorno FEFO",
                                  "autorizadorEmail": "gerente.fefo@meraki.com",
                                  "autorizadorSenha": "%s"
                                }
                                """.formatted(SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"));

        List<LoteEstoqueEntity> lotesAposEstorno = loteEstoqueRepository.findAllByEstoqueId(estoqueAposVenda.getId());
        assertThat(lotesAposEstorno).extracting(LoteEstoqueEntity::getCodigo, LoteEstoqueEntity::getQuantidadeAtual)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("LOTE-VENCIDO", new BigDecimal("3.000")),
                        org.assertj.core.groups.Tuple.tuple("LOTE-PROXIMO", new BigDecimal("2.000")),
                        org.assertj.core.groups.Tuple.tuple("LOTE-DISTANTE", new BigDecimal("5.000"))
                );
    }

    @Test
    void descontoEmPagamentoParcialConcluiPedidoEstornoIndividualDestravaEEstornoEmDinheiroProtegeGaveta()
            throws Exception {
        CaixaEntity caixa = caixa(estabelecimento, "Caixa Regressao Onda2");
        ConfiguracaoCaixaEntity config = configuracao(estabelecimento);
        config.setLimiteDescontoPercentual(new BigDecimal("10.00"));
        config.setLimiteDescontoValor(new BigDecimal("10.00"));
        configuracaoCaixaRepository.saveAndFlush(config);

        UsuarioAutenticado gerente = autenticarGerente("gerente.onda2@meraki.com");
        UsuarioAutenticado operador = autenticarComPermissoes("operador.onda2@meraki.com", PERMISSOES_ATENDENTE);

        String sessao1Id = abrirSessao(operador, caixa.getId(), "20.00");

        CategoriaEntity cat = categoria("Destilados Onda2");
        ProdutoEntity gin = produtoPadrao("Gin 750ml", cat, false, "40.00", "50.00", null, "10.000");

        // 1) Desconto acima do limite em PAGAMENTO_PARCIAL zera o saldo restante e conclui o pedido automaticamente
        MvcResult p1Res = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "itens": [{"produtoId": "%s", "quantidade": 1}]
                                }
                                """.formatted(gin.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String pedido1Id = JsonPath.read(p1Res.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedido1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forma\":\"DINHEIRO\",\"valor\":30.00,\"valorRecebido\":30.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGAMENTO_PARCIAL"));

        mockMvc.perform(put("/caixa/pedidos/{id}/desconto", pedido1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDesconto\":\"VALOR\",\"valorDescontoInformado\":20.00,\"motivo\":\"Acordo cliente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDesconto").value("PENDENTE_AUTORIZACAO"));

        mockMvc.perform(post("/caixa/pedidos/{id}/desconto/decisao", pedido1Id)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aprovado": true,
                                  "observacao": "Autorizado pelo gerente"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusDesconto").value("APROVADO"))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.saldoRestante").value(0.0));

        assertThat(estoqueRepository.findByProdutoId(gin.getId()).orElseThrow().getQuantidadeAtual())
                .isEqualByComparingTo("9.000");

        // 2) Pedido em PAGAMENTO_PARCIAL destravado por estorno individual de pagamento + posterior cancelamento
        MvcResult p2Res = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "itens": [{"produtoId": "%s", "quantidade": 1}]
                                }
                                """.formatted(gin.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String pedido2Id = JsonPath.read(p2Res.getResponse().getContentAsString(), "$.id");

        MvcResult pag2Res = mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedido2Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forma\":\"DINHEIRO\",\"valor\":25.00,\"valorRecebido\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGAMENTO_PARCIAL"))
                .andReturn();
        String pag2Id = JsonPath.read(pag2Res.getResponse().getContentAsString(), "$.pagamentos[0].id");

        mockMvc.perform(post("/caixa/pedidos/{pedidoId}/pagamentos/{pagamentoId}/estorno", pedido2Id, pag2Id)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente mudou forma de pagamento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"))
                .andExpect(jsonPath("$.totalPago").value(0.0))
                .andExpect(jsonPath("$.pagamentos[0].status").value("ESTORNADO"));

        mockMvc.perform(post("/caixa/pedidos/{id}/cancelamento", pedido2Id)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Pedido descartado após estorno parcial\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        // 3) Estorno em dinheiro da sessão 1 após sangria deixando caixa abaixo do valor -> bloqueado (409)
        // Sessão 1 tem fundo 20 + venda p1 (30) = 50. Sangria de 40 deixa apenas 10 -> estorno de 30 falha com 409!
        mockMvc.perform(post("/caixa/sessoes/{id}/movimentacoes", sessao1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"SANGRIA\",\"valor\":40.00,\"motivo\":\"Retirada parcial\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/caixa/pedidos/{id}/estorno", pedido1Id)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Tentativa sem saldo na gaveta\"}"))
                .andExpect(status().isConflict());

        // 4) Fecha sessão 1 (com 10 contado), abre sessão 2 com fundo 100 e estorna pedido 1 -> grava SANGRIA na sessão 2!
        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessao1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dinheiroContado\":10.00}"))
                .andExpect(status().isOk());

        String sessao2Id = abrirSessao(operador, caixa.getId(), "100.00");
        mockMvc.perform(post("/caixa/pedidos/{id}/estorno", pedido1Id)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "Estorno em sessão seguinte",
                                  "autorizadorEmail": "gerente.onda2@meraki.com",
                                  "autorizadorSenha": "%s"
                                }
                                """.formatted(SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"));

        List<MovimentacaoCaixaEntity> movsSessao2 = movimentacaoCaixaRepository
                .findBySessaoCaixaIdOrderByOcorridoEmAsc(UUID.fromString(sessao2Id));
        assertThat(movsSessao2).anyMatch(m ->
                m.getTipo() == TipoMovimentacaoCaixa.SANGRIA
                        && m.getValor().compareTo(new BigDecimal("30.00")) == 0
        );
    }

    private String abrirSessao(UsuarioAutenticado usuario, UUID caixaId, String fundoTroco) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/caixa/sessoes")
                        .cookie(usuario.accessToken())
                        .with(csrf(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caixaId\":\"%s\",\"fundoTroco\":%s}".formatted(caixaId, fundoTroco)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }
}
