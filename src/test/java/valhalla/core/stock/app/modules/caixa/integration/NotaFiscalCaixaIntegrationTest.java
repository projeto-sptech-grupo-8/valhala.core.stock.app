package valhalla.core.stock.app.modules.caixa.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.service.EventoAuditoriaCaixa;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.mercado-pago.point.reconciliacao-ativa-habilitada=false")
class NotaFiscalCaixaIntegrationTest extends CaixaIntegrationTestSupport {

    private static final String[] PERMISSOES_ATENDENTE = {
            "CAIXA_OPERAR", "CAIXA_VISUALIZAR", "CAIXA_FECHAR"
    };

    private static final String[] PERMISSOES_OPERADOR_COM_CANCELAMENTO = {
            "CAIXA_OPERAR", "CAIXA_VISUALIZAR", "CAIXA_FECHAR", "CAIXA_CANCELAR", "CAIXA_ESTORNAR"
    };

    @Test
    void emiteNfcePosVendaComRateioDeDescontoTributacaoAutomaticaBaixaXmlECancelaPermitindoReemissao() throws Exception {
        ConfiguracaoCaixaEntity config = configuracao(estabelecimento);
        config.setLimiteDescontoPercentual(new BigDecimal("20.00"));
        config.setLimiteDescontoValor(new BigDecimal("50.00"));
        configuracaoCaixaRepository.saveAndFlush(config);

        CaixaEntity caixa = caixa(estabelecimento, "Caixa Principal");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.nfce@meraki.com",
                PERMISSOES_ATENDENTE
        );
        UsuarioAutenticado gerente = autenticarGerente("gerente.nfce@meraki.com");

        abrirSessao(operador, caixa.getId(), "100.00");

        CategoriaEntity catCervejas = categoria("Cervejas");
        CategoriaEntity catDrinks = categoria("Drinks");

        ProdutoEntity heineken = produtoPadrao(
                "Heineken 600ml", catCervejas, false, "9.00", "20.00", null, "30.000"
        );
        ProdutoEntity gin = produtoPadrao(
                "Gin Tanqueray 1L", catCervejas, true, "90.00", "200.00", "1000.000", "1000.000"
        );
        ProdutoEntity ginTonica = drinkComReceita(
                "Gin Tônica Especial", catDrinks, "30.00", gin, "50.000", UnidadeConsumoDrink.ML
        );

        // 1. Cria pedido com 2 Heinekens (R$ 40,00) + 1 Gin Tônica (R$ 30,00) = Subtotal R$ 70,00
        MvcResult criacaoPedido = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "identificadorAtendimento": "Balcão NFC-e",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 2},
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(heineken.getId(), ginTonica.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        String pedidoId = JsonPath.read(criacaoPedido.getResponse().getContentAsString(), "$.id");

        // 2. CA02: Tentar emitir NFC-e antes de concluir o pedido retorna 409 Conflict
        mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Somente pedidos com status CONCLUIDO")));

        // 3. Aplica desconto de R$ 7,00 (Subtotal 70,00 -> Total 63,00) e paga R$ 100,00 em dinheiro (Troco R$ 37,00)
        mockMvc.perform(put("/caixa/pedidos/{id}/desconto", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoDesconto": "VALOR",
                                  "valorDescontoInformado": 7.00,
                                  "motivo": "Cliente fidelidade"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(63.00));

        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DINHEIRO",
                                  "valor": 63.00,
                                  "valorRecebido": 100.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        // 4. O pedido concluído sem nota deve aparecer em /caixa/notas-fiscais/pedidos-elegiveis
        mockMvc.perform(get("/caixa/notas-fiscais/pedidos-elegiveis")
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].id").value(pedidoId));

        // 5. Documento inválido (ex.: 5 dígitos) deve retornar 400 Bad Request
        mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s",
                                  "documentoCliente": "12345"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("CPF (11 dígitos) ou CNPJ (14 dígitos)")));

        // 6. CA01, CA04, CA05: Emite a NFC-e pós-venda informando CPF com máscara e nome do consumidor
        MvcResult emissao1 = mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s",
                                  "documentoCliente": "123.456.789-09",
                                  "nomeCliente": "Consumidor Meraki"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pedidoId").value(pedidoId))
                .andExpect(jsonPath("$.pedidoNumero").value(1))
                .andExpect(jsonPath("$.tipo").value("NFCE"))
                .andExpect(jsonPath("$.modelo").value("65"))
                .andExpect(jsonPath("$.status").value("AUTORIZADA"))
                .andExpect(jsonPath("$.numero").value("1"))
                .andExpect(jsonPath("$.serie").value("1"))
                .andExpect(jsonPath("$.contraparteDocumento").value("12345678909"))
                .andExpect(jsonPath("$.contraparteNome").value("Consumidor Meraki"))
                .andExpect(jsonPath("$.valorProdutos").value(70.00))
                .andExpect(jsonPath("$.valorDesconto").value(7.00))
                .andExpect(jsonPath("$.valorTotal").value(63.00))
                .andExpect(jsonPath("$.valorTrocoTotal").value(37.00))
                .andExpect(jsonPath("$.emitente.razaoSocial").value("Meraki LTDA"))
                .andExpect(jsonPath("$.itens", hasSize(2)))
                // Item 1 (Heineken: produto revenda -> CFOP 5405, CSOSN 500, bruto 40.00, desconto 4.00, líquido 36.00)
                .andExpect(jsonPath("$.itens[0].cfop").value("5405"))
                .andExpect(jsonPath("$.itens[0].csosn").value("500"))
                .andExpect(jsonPath("$.itens[0].valorBruto").value(40.00))
                .andExpect(jsonPath("$.itens[0].valorDesconto").value(4.00))
                .andExpect(jsonPath("$.itens[0].valorTotal").value(36.00))
                // Item 2 (Gin Tônica: drink -> CFOP 5102, CSOSN 102, bruto 30.00, desconto 3.00, líquido 27.00)
                .andExpect(jsonPath("$.itens[1].cfop").value("5102"))
                .andExpect(jsonPath("$.itens[1].csosn").value("102"))
                .andExpect(jsonPath("$.itens[1].valorBruto").value(30.00))
                .andExpect(jsonPath("$.itens[1].valorDesconto").value(3.00))
                .andExpect(jsonPath("$.itens[1].valorTotal").value(27.00))
                // Pagamentos para o DANFE 80mm
                .andExpect(jsonPath("$.pagamentos", hasSize(1)))
                .andExpect(jsonPath("$.pagamentos[0].forma").value("DINHEIRO"))
                .andExpect(jsonPath("$.pagamentos[0].codigoSefaz").value("01"))
                .andExpect(jsonPath("$.pagamentos[0].valorRecebido").value(100.00))
                .andExpect(jsonPath("$.pagamentos[0].valorTroco").value(37.00))
                .andReturn();

        String notaId = JsonPath.read(emissao1.getResponse().getContentAsString(), "$.id");
        String chaveAcesso = JsonPath.read(emissao1.getResponse().getContentAsString(), "$.chaveAcesso");
        assertThat(chaveAcesso).hasSize(44).containsOnlyDigits();

        // 7. Pedido não deve mais aparecer em pedidos-elegiveis e deve exibir o vínculo da nota no detalhe do pedido
        mockMvc.perform(get("/caixa/notas-fiscais/pedidos-elegiveis")
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)));

        mockMvc.perform(get("/caixa/pedidos/{id}", pedidoId)
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaFiscal.id").value(notaId))
                .andExpect(jsonPath("$.notaFiscal.status").value("AUTORIZADA"))
                .andExpect(jsonPath("$.notaFiscal.numero").value("1"))
                .andExpect(jsonPath("$.notaFiscal.chaveAcesso").value(chaveAcesso));

        // 8. CA03: Tentar emitir uma segunda NFC-e para o mesmo pedido com nota AUTORIZADA retorna 409 Conflict
        mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("já possui uma NFC-e autorizada ativa")));

        // 9. CA06: Download do XML 4.00 e listagem filtrada de notas fiscais
        mockMvc.perform(get("/caixa/notas-fiscais/{id}/xml", notaId)
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("nfce-1-1-" + chaveAcesso + ".xml")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("<mod>65</mod>")
                        .contains("<chNFe>" + chaveAcesso + "</chNFe>")
                        .contains("<CPF>12345678909</CPF>"));

        mockMvc.perform(get("/caixa/notas-fiscais")
                        .param("status", "AUTORIZADA")
                        .param("documentoCliente", "123.456.789-09")
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].id").value(notaId));

        // 10. CA07: Cancelamento da NFC-e exige motivo >= 15 caracteres e permissão CAIXA_CANCELAR (ou credenciais do gerente)
        mockMvc.perform(post("/caixa/notas-fiscais/{id}/cancelamento", notaId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "Motivo curto"
                                }
                                """))
                .andExpect(status().isBadRequest());

        // Operador sem CAIXA_CANCELAR e sem autorizador recebe 403 Forbidden
        mockMvc.perform(post("/caixa/notas-fiscais/{id}/cancelamento", notaId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "Erro na digitação do CPF do cliente no caixa"
                                }
                                """))
                .andExpect(status().isForbidden());

        // Operador cancela com autorização do gerente
        mockMvc.perform(post("/caixa/notas-fiscais/{id}/cancelamento", notaId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "Erro na digitação do CPF do cliente no caixa",
                                  "autorizadorEmail": "gerente.nfce@meraki.com",
                                  "autorizadorSenha": "%s"
                                }
                                """.formatted(SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Erro na digitação do CPF do cliente no caixa"))
                .andExpect(jsonPath("$.canceladaPorUsuarioId").value(gerente.usuarioId().toString()));

        // Após cancelar a nota, o pedido volta a aparecer como elegível e permite emitir a NFC-e #2 substituta
        mockMvc.perform(get("/caixa/notas-fiscais/pedidos-elegiveis")
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].id").value(pedidoId))
                .andExpect(jsonPath("$.itens[0].notaFiscal.status").value("CANCELADA"));

        mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s",
                                  "documentoCliente": "98765432100",
                                  "nomeCliente": "Consumidor Corrigido"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("2"))
                .andExpect(jsonPath("$.status").value("AUTORIZADA"))
                .andExpect(jsonPath("$.contraparteDocumento").value("98765432100"));

        assertThat(auditorias())
                .extracting(a -> a.getTipoEvento())
                .contains(EventoAuditoriaCaixa.NFCE_EMITIDA, EventoAuditoriaCaixa.NFCE_CANCELADA);
    }

    @Test
    void cancelaAutomaticamenteNfceAutorizadaAoEstornarPedidoConcluido() throws Exception {
        configuracao(estabelecimento);
        CaixaEntity caixa = caixa(estabelecimento, "Caixa Estorno");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.estorno.nfce@meraki.com",
                PERMISSOES_OPERADOR_COM_CANCELAMENTO
        );

        abrirSessao(operador, caixa.getId(), "50.00");

        CategoriaEntity catBebidas = categoria("Destilados");
        ProdutoEntity whisky = produtoPadrao(
                "Whisky 12 Anos", catBebidas, false, "80.00", "150.00", null, "10.000"
        );

        MvcResult criacao = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(whisky.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        String pedidoId = JsonPath.read(criacao.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DINHEIRO",
                                  "valor": 150.00,
                                  "valorRecebido": 150.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        MvcResult emissao = mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTORIZADA"))
                .andReturn();

        String notaId = JsonPath.read(emissao.getResponse().getContentAsString(), "$.id");

        // CA08: Estorna o pedido e verifica que a NFC-e vinculada foi automaticamente cancelada na mesma transação
        mockMvc.perform(post("/caixa/pedidos/{id}/estorno", pedidoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "Devolução imediata por desistência da compra no balcão"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESTORNADO"))
                .andExpect(jsonPath("$.notaFiscal.id").value(notaId))
                .andExpect(jsonPath("$.notaFiscal.status").value("CANCELADA"));

        mockMvc.perform(get("/caixa/notas-fiscais/{id}", notaId)
                        .cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelamento",
                        containsString("Devolução imediata por desistência da compra no balcão")));
    }

    @Test
    void isolaNotasFiscaisPorEstabelecimento() throws Exception {
        configuracao(estabelecimento);
        CaixaEntity caixa = caixa(estabelecimento, "Caixa Loja 1");
        UsuarioAutenticado operadorLoja1 = autenticarComPermissoes(
                "op.loja1@meraki.com",
                PERMISSOES_OPERADOR_COM_CANCELAMENTO
        );
        abrirSessao(operadorLoja1, caixa.getId(), "50.00");

        CategoriaEntity cat = categoria("Vinhos");
        ProdutoEntity vinho = produtoPadrao(
                "Vinho Malbec", cat, false, "35.00", "75.00", null, "10.000"
        );

        MvcResult criacao = mockMvc.perform(post("/caixa/pedidos")
                        .cookie(operadorLoja1.accessToken())
                        .with(csrf(operadorLoja1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canal": "BALCAO",
                                  "itens": [
                                    {"produtoId": "%s", "quantidade": 1}
                                  ]
                                }
                                """.formatted(vinho.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        String pedidoId = JsonPath.read(criacao.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/caixa/pedidos/{id}/pagamentos", pedidoId)
                        .cookie(operadorLoja1.accessToken())
                        .with(csrf(operadorLoja1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forma": "DINHEIRO",
                                  "valor": 75.00,
                                  "valorRecebido": 75.00
                                }
                                """))
                .andExpect(status().isOk());

        EstablishmentEntity outraLoja = criarEstabelecimento("Outra Adega LTDA", "Outra Adega", "98765432000188");
        UsuarioAutenticado operadorLoja2 = autenticarComPermissoesEm(
                outraLoja,
                "op.loja2@outra.com",
                PERMISSOES_OPERADOR_COM_CANCELAMENTO
        );

        // Operador de outro estabelecimento não pode emitir NFC-e para pedido da Loja 1
        mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operadorLoja2.accessToken())
                        .with(csrf(operadorLoja2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isForbidden());

        MvcResult emissaoLoja1 = mockMvc.perform(post("/caixa/notas-fiscais/emitir")
                        .cookie(operadorLoja1.accessToken())
                        .with(csrf(operadorLoja1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pedidoId": "%s"
                                }
                                """.formatted(pedidoId)))
                .andExpect(status().isCreated())
                .andReturn();
        String notaId = JsonPath.read(emissaoLoja1.getResponse().getContentAsString(), "$.id");

        // Operador da Loja 2 não acessa detalhe, XML nem cancelamento da nota da Loja 1
        mockMvc.perform(get("/caixa/notas-fiscais/{id}", notaId)
                        .cookie(operadorLoja2.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/caixa/notas-fiscais/{id}/xml", notaId)
                        .cookie(operadorLoja2.accessToken()))
                .andExpect(status().isForbidden());
    }

    private String abrirSessao(UsuarioAutenticado operador, java.util.UUID caixaId, String fundoTroco) throws Exception {
        MvcResult abertura = mockMvc.perform(post("/caixa/sessoes")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caixaId": "%s",
                                  "fundoTroco": %s
                                }
                                """.formatted(caixaId, fundoTroco)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(abertura.getResponse().getContentAsString(), "$.id");
    }
}
