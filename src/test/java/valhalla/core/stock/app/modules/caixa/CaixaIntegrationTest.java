package valhalla.core.stock.app.modules.caixa;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.entity.AuditoriaCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.entity.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaixaIntegrationTest extends CaixaIntegrationTestSupport {

    private static final String[] PERMISSOES_OPERADOR = {
            "CAIXA_OPERAR", "CAIXA_VISUALIZAR", "CAIXA_FECHAR"
    };

    @Test
    void configuraCaixaComPutCompletoEGravaAuditoria() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.config@meraki.com");

        mockMvc.perform(put("/caixa/configuracao")
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoJson("TERM-001", "Caixa principal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.terminalId").value("TERM-001"))
                .andExpect(jsonPath("$.terminalNome").value("Caixa principal"))
                .andExpect(jsonPath("$.pointHabilitado").value(false))
                .andExpect(jsonPath("$.pixHabilitado").value(true))
                .andExpect(jsonPath("$.limiteDescontoPercentual").value(10.0))
                .andExpect(jsonPath("$.atualizadoPorUsuarioId").value(gerente.usuarioId().toString()));

        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.config@meraki.com",
                "CAIXA_VISUALIZAR"
        );

        mockMvc.perform(get("/caixa/configuracao").cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalId").value("TERM-001"))
                .andExpect(jsonPath("$.parcelasMaximas").value(5));

        List<AuditoriaCaixaEntity> eventos = auditorias();
        assertThat(eventos)
                .anyMatch(evento -> "CONFIGURACAO_CAIXA_ALTERADA".equals(evento.getTipoEvento())
                        && evento.getDadosAnteriores() != null
                        && evento.getDadosNovos() != null);
    }

    @Test
    void persisteConfiguracaoPadraoQuandoPutNaoAlteraValores() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.padrao@meraki.com");

        assertThat(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimento.getId()))
                .isEmpty();

        mockMvc.perform(put("/caixa/configuracao")
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoPadraoJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.atualizadoPorUsuarioId").value(gerente.usuarioId().toString()));

        ConfiguracaoCaixaEntity configuracaoSalva = configuracaoCaixaRepository
                .findByEstabelecimentoId(estabelecimento.getId())
                .orElseThrow();

        assertThat(configuracaoSalva.getAtualizadoPorUsuarioId()).isEqualTo(gerente.usuarioId());
        assertThat(auditorias())
                .noneMatch(evento -> "CONFIGURACAO_CAIXA_ALTERADA".equals(evento.getTipoEvento()));
    }

    @Test
    void rejeitaParcelasMaximasAcimaDe12() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.parcelas@meraki.com");

        mockMvc.perform(put("/caixa/configuracao")
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoJson("TERM-001", "Caixa principal")
                                .replace("\"parcelasMaximas\": 5", "\"parcelasMaximas\": 13")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados de entrada inválidos"))
                .andExpect(jsonPath("$.fieldErrors.parcelasMaximas").value(
                        "parcelasMaximas deve ser no máximo 12"
                ));
    }

    @Test
    void rejeitaConfiguracaoSemFormaDePagamentoESemTerminalQuandoPointHabilitada() throws Exception {
        UsuarioAutenticado gerente = autenticarGerente("gerente.invalida@meraki.com");

        mockMvc.perform(put("/caixa/configuracao")
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoJson("TERM-001", "Caixa principal")
                                .replace("\"dinheiroHabilitado\": true", "\"dinheiroHabilitado\": false")
                                .replace("\"debitoHabilitado\": true", "\"debitoHabilitado\": false")
                                .replace("\"creditoHabilitado\": true", "\"creditoHabilitado\": false")
                                .replace("\"pixHabilitado\": true", "\"pixHabilitado\": false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Pelo menos uma forma de pagamento deve estar habilitada"
                ));

        mockMvc.perform(put("/caixa/configuracao")
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoJson(null, null)
                                .replace("\"pointHabilitado\": false", "\"pointHabilitado\": true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "O terminal Point é obrigatório quando a Point está habilitada"
                ));
    }

    @Test
    void operadorSemPermissaoNaoConfiguraCaixa() throws Exception {
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.sem-config@meraki.com",
                "CAIXA_VISUALIZAR"
        );

        mockMvc.perform(put("/caixa/configuracao")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(configuracaoJson("TERM-001", "Caixa principal")))
                .andExpect(status().isForbidden());
    }

    @Test
    void abreSessaoEConsultaAtualComDadosDoTerminal() throws Exception {
        configuracao(estabelecimento, "TERM-001");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.abertura@meraki.com",
                PERMISSOES_OPERADOR
        );

        MvcResult abertura = mockMvc.perform(post("/caixa/sessoes")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundoTroco":100.00,"observacao":"Abertura do turno"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTA"))
                .andExpect(jsonPath("$.terminalId").value("TERM-001"))
                .andExpect(jsonPath("$.fundoTroco").value(100.0))
                .andExpect(jsonPath("$.operadorUsuarioId").value(operador.usuarioId().toString()))
                .andExpect(jsonPath("$.resumoAtual.dinheiroEsperado").value(100.0))
                .andReturn();

        String sessaoId = JsonPath.read(abertura.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/caixa/sessoes/atual").cookie(operador.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessaoId))
                .andExpect(jsonPath("$.status").value("ABERTA"));

        assertThat(auditorias())
                .anyMatch(evento -> "ABERTURA_CAIXA".equals(evento.getTipoEvento())
                        && evento.getValor().compareTo(new java.math.BigDecimal("100.00")) == 0);
    }

    @Test
    void bloqueiaSegundaSessaoAbertaParaMesmoOperadorOuTerminal() throws Exception {
        configuracao(estabelecimento, "TERM-001");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.duplicado@meraki.com",
                PERMISSOES_OPERADOR
        );

        abrir(operador, "100.00");

        mockMvc.perform(post("/caixa/sessoes")
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundoTroco\":50.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "O operador já possui uma sessão de caixa aberta"
                ));

        UsuarioAutenticado outroOperador = autenticarComPermissoes(
                "operador.duplicado2@meraki.com",
                PERMISSOES_OPERADOR
        );

        mockMvc.perform(post("/caixa/sessoes")
                        .cookie(outroOperador.accessToken())
                        .with(csrf(outroOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundoTroco\":50.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Já existe uma sessão de caixa aberta para este terminal"
                ));
    }

    @Test
    void retorna204QuandoOperadorNaoTemSessaoAberta() throws Exception {
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.sem-sessao@meraki.com",
                PERMISSOES_OPERADOR
        );

        mockMvc.perform(get("/caixa/sessoes/atual").cookie(operador.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void sangriaRespeitaDinheiroDisponivelEOutroOperadorNaoMovimenta() throws Exception {
        configuracao(estabelecimento, "TERM-002");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.sangria@meraki.com",
                PERMISSOES_OPERADOR
        );
        String sessaoId = abrir(operador, "100.00");

        mockMvc.perform(post("/caixa/sessoes/{id}/movimentacoes", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"REFORCO","valor":50.00,"motivo":"Troco adicional"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumoAtual.dinheiroEsperado").value(150.0))
                .andExpect(jsonPath("$.movimentacoes.length()").value(1));

        mockMvc.perform(post("/caixa/sessoes/{id}/movimentacoes", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"SANGRIA","valor":200.00,"motivo":"Depósito"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A sangria não pode ser maior que o dinheiro disponível no caixa"
                ))
                .andExpect(jsonPath("$.fieldErrors.valor").value(
                        "Disponível para sangria: R$ 150.00"
                ));

        mockMvc.perform(post("/caixa/sessoes/{id}/movimentacoes", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"SANGRIA","valor":120.00,"motivo":"Depósito"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumoAtual.dinheiroEsperado").value(30.0));

        UsuarioAutenticado outroOperador = autenticarComPermissoes(
                "operador.sangria2@meraki.com",
                PERMISSOES_OPERADOR
        );

        mockMvc.perform(post("/caixa/sessoes/{id}/movimentacoes", sessaoId)
                        .cookie(outroOperador.accessToken())
                        .with(csrf(outroOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"SANGRIA","valor":10.00,"motivo":"Retirada"}
                                """))
                .andExpect(status().isForbidden());

        UsuarioAutenticado gerente = autenticarGerente("gerente.sangria@meraki.com");
        mockMvc.perform(post("/caixa/sessoes/{id}/movimentacoes", sessaoId)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"SANGRIA","valor":10.00,"motivo":"Retirada gerencial"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumoAtual.dinheiroEsperado").value(20.0));
    }

    @Test
    void fechaSemDiferencaEDepoisRejeitaNovoFechamento() throws Exception {
        configuracao(estabelecimento, "TERM-003");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.fechamento@meraki.com",
                PERMISSOES_OPERADOR
        );
        String sessaoId = abrir(operador, "100.00");

        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dinheiroContado":100.00,"observacao":"Turno encerrado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.dinheiroEsperado").value(100.0))
                .andExpect(jsonPath("$.dinheiroContado").value(100.0))
                .andExpect(jsonPath("$.diferenca").value(0.0))
                .andExpect(jsonPath("$.possuiDivergenciaPendente").value(false))
                .andExpect(jsonPath("$.resumoAtual").doesNotExist())
                .andExpect(jsonPath("$.resumoFechamento.totalVendas").value(0.0))
                .andExpect(jsonPath("$.fechadaPorUsuarioId").value(
                        operador.usuarioId().toString()
                ));

        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dinheiroContado":100.00}
                                """))
                .andExpect(status().isConflict());

        assertThat(auditorias())
                .anyMatch(evento -> "FECHAMENTO_CAIXA".equals(evento.getTipoEvento()));
    }

    @Test
    void fechamentoComDiferencaExigeJustificativaELiberaTerminalParaOutroOperador() throws Exception {
        configuracao(estabelecimento, "TERM-004");
        UsuarioAutenticado operadorA = autenticarComPermissoes(
                "operador.divergencia@meraki.com",
                PERMISSOES_OPERADOR
        );
        String sessaoId = abrir(operadorA, "100.00");

        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessaoId)
                        .cookie(operadorA.accessToken())
                        .with(csrf(operadorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dinheiroContado":90.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A justificativa é obrigatória quando o fechamento apresenta diferença"
                ));

        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessaoId)
                        .cookie(operadorA.accessToken())
                        .with(csrf(operadorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dinheiroContado":90.00,"justificativaDivergencia":"Faltou troco"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_FECHAMENTO"))
                .andExpect(jsonPath("$.diferenca").value(-10.0))
                .andExpect(jsonPath("$.possuiDivergenciaPendente").value(true));

        UsuarioAutenticado operadorB = autenticarComPermissoes(
                "operador.turno2@meraki.com",
                PERMISSOES_OPERADOR
        );
        String novaSessaoId = abrir(operadorB, "50.00");
        assertThat(novaSessaoId).isNotEqualTo(sessaoId);

        UsuarioAutenticado gerente = autenticarGerente("gerente.divergencia@meraki.com");
        mockMvc.perform(post("/caixa/sessoes/{id}/resolucao-divergencia", sessaoId)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"observacao":"Divergência conferida"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.possuiDivergenciaPendente").value(false))
                .andExpect(jsonPath("$.divergenciaResolvidaPorUsuarioId").value(
                        gerente.usuarioId().toString()
                ))
                .andExpect(jsonPath("$.fechadaPorUsuarioId").value(
                        operadorA.usuarioId().toString()
                ));

        mockMvc.perform(post("/caixa/sessoes/{id}/resolucao-divergencia", sessaoId)
                        .cookie(gerente.accessToken())
                        .with(csrf(gerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        assertThat(auditorias())
                .anyMatch(evento -> "DIVERGENCIA_CAIXA".equals(evento.getTipoEvento()))
                .anyMatch(evento -> "DIVERGENCIA_CAIXA_RESOLVIDA".equals(evento.getTipoEvento()));
    }

    @Test
    void usuarioComPermissaoDeDivergenciaSemPapelGerentePodeResolver() throws Exception {
        configuracao(estabelecimento, "TERM-007");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.divergencia.permissao@meraki.com",
                PERMISSOES_OPERADOR
        );
        String sessaoId = abrir(operador, "100.00");

        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dinheiroContado":90.00,"justificativaDivergencia":"Faltou troco"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_FECHAMENTO"));

        UsuarioAutenticado aprovador = autenticarComPermissoes(
                "aprovador.divergencia@meraki.com",
                "CAIXA_RESOLVER_DIVERGENCIA"
        );

        mockMvc.perform(post("/caixa/sessoes/{id}/resolucao-divergencia", sessaoId)
                        .cookie(aprovador.accessToken())
                        .with(csrf(aprovador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"observacao":"Divergência aprovada"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.divergenciaResolvidaPorUsuarioId").value(
                        aprovador.usuarioId().toString()
                ))
                .andExpect(jsonPath("$.fechadaPorUsuarioId").value(
                        operador.usuarioId().toString()
                ));
    }

    @Test
    void fechamentoEhBloqueadoPorPagamentoPendente() throws Exception {
        configuracao(estabelecimento, "TERM-005");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.pendente@meraki.com",
                PERMISSOES_OPERADOR
        );
        String sessaoId = abrir(operador, "100.00");

        PagamentoPedidoEntity pagamento = pagamento(
                UUID.fromString(sessaoId),
                "PIX",
                "PENDENTE",
                "25.00"
        );

        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dinheiroContado":100.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Não é possível fechar o caixa enquanto houver pagamentos pendentes"
                ))
                .andExpect(jsonPath("$.fieldErrors.pagamentosPendentes").value("PENDENTE: 1"));

        pagamento.setStatus(StatusPagamentoPedido.CANCELADO);
        pagamentoPedidoRepository.saveAndFlush(pagamento);

        mockMvc.perform(post("/caixa/sessoes/{id}/fechamento", sessaoId)
                        .cookie(operador.accessToken())
                        .with(csrf(operador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dinheiroContado":100.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"));
    }

    @Test
    void detalheEDistribuicaoDeSessoesRespeitamEstabelecimento() throws Exception {
        EstablishmentEntity outroEstabelecimento = criarEstabelecimento(
                "Outra LTDA",
                "Outra",
                "98765432000199"
        );
        UsuarioAutenticado gerenteOutro = autenticarGerenteEm(
                outroEstabelecimento,
                "gerente.outro@outra.com"
        );
        SessaoCaixaEntity sessaoExterna = sessaoAberta(
                outroEstabelecimento,
                gerenteOutro.usuarioId(),
                "TERM-EXTERNO",
                "25.00"
        );

        UsuarioAutenticado gerente = autenticarGerente("gerente.isolamento@meraki.com");

        mockMvc.perform(get("/caixa/sessoes/{id}", sessaoExterna.getId())
                        .cookie(gerente.accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/caixa/sessoes").cookie(gerente.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(0));
    }

    @Test
    void auditoriaFiltraPorTipoDeEventoEExigePermissaoDeVisualizacao() throws Exception {
        configuracao(estabelecimento, "TERM-006");
        UsuarioAutenticado operador = autenticarComPermissoes(
                "operador.auditoria@meraki.com",
                PERMISSOES_OPERADOR
        );
        abrir(operador, "100.00");

        UsuarioAutenticado gerente = autenticarGerente("gerente.auditoria@meraki.com");

        mockMvc.perform(get("/caixa/auditoria")
                        .cookie(gerente.accessToken())
                        .param("tipoEvento", "ABERTURA_CAIXA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens[0].tipoEvento").value("ABERTURA_CAIXA"))
                .andExpect(jsonPath("$.itens[0].usuarioNome").isNotEmpty());

        mockMvc.perform(get("/caixa/auditoria")
                        .cookie(gerente.accessToken())
                        .param("dataInicio", "2026-11-03")
                        .param("dataFim", "2026-11-02"))
                .andExpect(status().isBadRequest());

        UsuarioAutenticado semVisualizacao = autenticarComPermissoes(
                "operador.sem-visualizar@meraki.com",
                "CAIXA_OPERAR"
        );

        mockMvc.perform(get("/caixa/auditoria").cookie(semVisualizacao.accessToken()))
                .andExpect(status().isForbidden());
    }

    private String abrir(UsuarioAutenticado usuario, String fundoTroco) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/caixa/sessoes")
                        .cookie(usuario.accessToken())
                        .with(csrf(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundoTroco\":%s}".formatted(fundoTroco)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
    }

    private String configuracaoPadraoJson() {
        return configuracaoJson(null, null)
                .replace("\"limiteDescontoPercentual\": 10.00", "\"limiteDescontoPercentual\": 0.00")
                .replace("\"limiteDescontoValor\": 20.00", "\"limiteDescontoValor\": 0.00");
    }

    private String configuracaoJson(String terminalId, String terminalNome) {
        String terminal = terminalId == null ? "null" : "\"" + terminalId + "\"";
        String nome = terminalNome == null ? "null" : "\"" + terminalNome + "\"";
        return """
                {
                  "terminalId": %s,
                  "terminalNome": %s,
                  "pointHabilitado": false,
                  "dinheiroHabilitado": true,
                  "debitoHabilitado": true,
                  "creditoHabilitado": true,
                  "pixHabilitado": true,
                  "parcelamentoHabilitado": false,
                  "parcelasMinimas": 1,
                  "parcelasMaximas": 5,
                  "valorMinimoParcela": null,
                  "politicaJuros": "ABSORVIDA_PELA_LOJA",
                  "validadeCobrancaSegundos": 300,
                  "limiteDescontoPercentual": 10.00,
                  "limiteDescontoValor": 20.00,
                  "imprimirComprovante": true,
                  "permitirPagamentoManual": false
                }
                """.formatted(terminal, nome);
    }
}
