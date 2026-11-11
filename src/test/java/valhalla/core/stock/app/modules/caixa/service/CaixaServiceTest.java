package valhalla.core.stock.app.modules.caixa.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import valhalla.core.stock.app.modules.caixa.dto.CaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.CaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.CaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.TerminalCaixaEntity;
import valhalla.core.stock.app.modules.caixa.exception.CaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.CaixaJaExisteException;
import valhalla.core.stock.app.modules.caixa.exception.TerminalCaixaJaExisteException;
import valhalla.core.stock.app.modules.caixa.repository.CaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.TerminalCaixaRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;
import valhalla.core.stock.app.support.AutenticacaoTeste;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaixaServiceTest {

    @Mock private CaixaRepository caixaRepository;
    @Mock private TerminalCaixaRepository terminalCaixaRepository;
    @Mock private SessaoCaixaRepository sessaoCaixaRepository;
    @Mock private ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    @Mock private AuditoriaCaixaService auditoriaCaixaService;

    private CaixaService caixaService;
    private UUID estabelecimentoId;
    private UUID usuarioId;

    @BeforeEach
    void preparar() {
        DisponibilidadePoint disponibilidadePoint =
                new DisponibilidadePoint(terminalCaixaRepository, configuracaoCaixaRepository);
        caixaService = new CaixaService(caixaRepository, terminalCaixaRepository, sessaoCaixaRepository,
                disponibilidadePoint, auditoriaCaixaService, new ContextoCaixa(), new BusinessEventLogger());
        estabelecimentoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        AutenticacaoTeste.autenticar(estabelecimentoId, usuarioId);
    }

    @AfterEach
    void limparContextoDeSeguranca() {
        AutenticacaoTeste.limpar();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveListarCaixasComTerminaisEIndicadorDeUso() {
        CaixaEntity emUso = caixa("Caixa 1", true);
        CaixaEntity livre = caixa("Caixa 2", true);
        TerminalCaixaEntity terminal = terminal(emUso, "POS-1", "Maquininha", true);
        Page<CaixaEntity> pagina = new PageImpl<>(List.of(emUso, livre), PageRequest.of(0, 20), 2);
        when(caixaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagina);
        when(terminalCaixaRepository.findAllByCaixaIdInOrderByNomeAsc(List.of(emUso.getId(), livre.getId())))
                .thenReturn(List.of(terminal));
        when(sessaoCaixaRepository.buscarCaixaIdsPorStatus(estabelecimentoId, StatusSessaoCaixa.ABERTA))
                .thenReturn(List.of(emUso.getId()));

        RespostaPaginadaDto<CaixaResponseDto> resposta = caixaService.listarCaixas(
                "  CAIXA ", true, 0, 20, "nome", DirecaoOrdenacao.ASC);

        assertThat(resposta.itens()).hasSize(2);
        CaixaResponseDto primeiro = resposta.itens().get(0);
        assertThat(primeiro.emUso()).isTrue();
        assertThat(primeiro.terminais()).extracting(TerminalCaixaResponseDto::terminalId).containsExactly("POS-1");
        CaixaResponseDto segundo = resposta.itens().get(1);
        assertThat(segundo.emUso()).isFalse();
        assertThat(segundo.terminais()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deveListarSemConsultarTerminaisQuandoNaoHaCaixas() {
        when(caixaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(sessaoCaixaRepository.buscarCaixaIdsPorStatus(estabelecimentoId, StatusSessaoCaixa.ABERTA))
                .thenReturn(List.of());

        RespostaPaginadaDto<CaixaResponseDto> resposta = caixaService.listarCaixas(
                "   ", null, 0, 20, "criadoEm", DirecaoOrdenacao.DESC);

        assertThat(resposta.itens()).isEmpty();
        verify(terminalCaixaRepository, never()).findAllByCaixaIdInOrderByNomeAsc(any());
    }

    @Test
    void deveCriarCaixaAtivoComNomeNormalizadoERegistrarAuditoria() {
        when(caixaRepository.existsByEstabelecimentoIdAndNomeIgnoreCase(estabelecimentoId, "Caixa 1"))
                .thenReturn(false);
        when(caixaRepository.saveAndFlush(any(CaixaEntity.class))).thenAnswer(invocacao -> {
            CaixaEntity caixa = invocacao.getArgument(0);
            caixa.setId(UUID.randomUUID());
            return caixa;
        });

        CaixaResponseDto resposta = caixaService.criarCaixa(new CaixaRequestDto("  Caixa 1  "));

        assertThat(resposta.nome()).isEqualTo("Caixa 1");
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.emUso()).isFalse();
        verify(auditoriaCaixaService).registrar(eq(estabelecimentoId), eq(usuarioId), isNull(),
                eq(EventoAuditoriaCaixa.CAIXA_CRIADO), eq(EventoAuditoriaCaixa.ENTIDADE_CAIXA),
                eq(resposta.id().toString()), anyString(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void naoDeveCriarCaixaComNomeJaExistenteNoEstabelecimento() {
        when(caixaRepository.existsByEstabelecimentoIdAndNomeIgnoreCase(estabelecimentoId, "Caixa 1"))
                .thenReturn(true);

        assertThatThrownBy(() -> caixaService.criarCaixa(new CaixaRequestDto("Caixa 1")))
                .isInstanceOf(CaixaJaExisteException.class);

        verify(caixaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaCaixaService);
    }

    @Test
    void deveTraduzirViolacaoDeUnicidadeConcorrenteAoCriarCaixa() {
        when(caixaRepository.existsByEstabelecimentoIdAndNomeIgnoreCase(estabelecimentoId, "Caixa 1"))
                .thenReturn(false);
        when(caixaRepository.saveAndFlush(any(CaixaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_caixa_nome"));

        assertThatThrownBy(() -> caixaService.criarCaixa(new CaixaRequestDto("Caixa 1")))
                .isInstanceOf(CaixaJaExisteException.class);
    }

    @Test
    void deveBuscarCaixaDoEstabelecimentoComTerminaisEStatusDeUso() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.findAllByCaixaIdOrderByNomeAsc(caixa.getId()))
                .thenReturn(List.of(terminal(caixa, "POS-1", "Balcão", true)));
        when(sessaoCaixaRepository.existsByCaixaIdAndStatus(caixa.getId(), StatusSessaoCaixa.ABERTA))
                .thenReturn(true);

        CaixaResponseDto resposta = caixaService.buscarCaixa(caixa.getId());

        assertThat(resposta.emUso()).isTrue();
        assertThat(resposta.terminais()).hasSize(1);
    }

    @Test
    void deveNegarAcessoACaixaDeOutroEstabelecimento() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        caixa.setEstabelecimentoId(UUID.randomUUID());
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));

        assertThatThrownBy(() -> caixaService.buscarCaixa(caixa.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deveFalharAoBuscarCaixaInexistente() {
        UUID id = UUID.randomUUID();
        when(caixaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caixaService.buscarCaixa(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Caixa não encontrado");
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL", "'   ',NULL", "'',true"}, nullValues = "NULL")
    void deveRejeitarAtualizacaoDeCaixaSemCamposValidos(String nome, Boolean ativo) {
        assertThatThrownBy(() -> caixaService.atualizarCaixa(UUID.randomUUID(),
                new CaixaAtualizacaoRequestDto(nome, ativo)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(caixaRepository);
    }

    @Test
    void deveRenomearEInativarCaixaSemSessaoAbertaERegistrarAuditoria() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(caixaRepository.existsByEstabelecimentoIdAndNomeIgnoreCaseAndIdNot(
                estabelecimentoId, "Caixa Bar", caixa.getId())).thenReturn(false);
        when(sessaoCaixaRepository.existsByCaixaIdAndStatus(caixa.getId(), StatusSessaoCaixa.ABERTA))
                .thenReturn(false);
        when(caixaRepository.saveAndFlush(caixa)).thenReturn(caixa);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId)).thenReturn(Optional.empty());
        when(terminalCaixaRepository.findAllByCaixaIdOrderByNomeAsc(caixa.getId())).thenReturn(List.of());

        CaixaResponseDto resposta = caixaService.atualizarCaixa(caixa.getId(),
                new CaixaAtualizacaoRequestDto(" Caixa Bar ", false));

        assertThat(resposta.nome()).isEqualTo("Caixa Bar");
        assertThat(resposta.ativo()).isFalse();
        verify(auditoriaCaixaService).registrar(eq(estabelecimentoId), eq(usuarioId), isNull(),
                eq(EventoAuditoriaCaixa.CAIXA_ALTERADO), eq(EventoAuditoriaCaixa.ENTIDADE_CAIXA),
                eq(caixa.getId().toString()), anyString(), isNull(), isNull(), any(), any());
    }

    @Test
    void naoDeveRegistrarAuditoriaQuandoAtualizacaoDeCaixaNaoAlteraNada() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(caixaRepository.saveAndFlush(caixa)).thenReturn(caixa);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId)).thenReturn(Optional.empty());
        when(terminalCaixaRepository.findAllByCaixaIdOrderByNomeAsc(caixa.getId())).thenReturn(List.of());

        caixaService.atualizarCaixa(caixa.getId(), new CaixaAtualizacaoRequestDto("Caixa 1", true));

        verifyNoInteractions(auditoriaCaixaService);
    }

    @Test
    void naoDeveRenomearCaixaParaNomeJaUsadoPorOutroCaixa() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(caixaRepository.existsByEstabelecimentoIdAndNomeIgnoreCaseAndIdNot(
                estabelecimentoId, "Caixa 2", caixa.getId())).thenReturn(true);

        assertThatThrownBy(() -> caixaService.atualizarCaixa(caixa.getId(),
                new CaixaAtualizacaoRequestDto("Caixa 2", null)))
                .isInstanceOf(CaixaJaExisteException.class);

        verify(caixaRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveInativarCaixaComSessaoAberta() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(sessaoCaixaRepository.existsByCaixaIdAndStatus(caixa.getId(), StatusSessaoCaixa.ABERTA))
                .thenReturn(true);

        assertThatThrownBy(() -> caixaService.atualizarCaixa(caixa.getId(),
                new CaixaAtualizacaoRequestDto(null, false)))
                .isInstanceOf(CaixaEstadoInvalidoException.class)
                .hasMessageContaining("sessão aberta");

        verify(caixaRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveReativarCaixaSemConsultarSessaoAberta() {
        CaixaEntity caixa = caixa("Caixa 1", false);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(caixaRepository.saveAndFlush(caixa)).thenReturn(caixa);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId)).thenReturn(Optional.empty());
        when(terminalCaixaRepository.findAllByCaixaIdOrderByNomeAsc(caixa.getId())).thenReturn(List.of());
        when(sessaoCaixaRepository.existsByCaixaIdAndStatus(caixa.getId(), StatusSessaoCaixa.ABERTA))
                .thenReturn(false);

        CaixaResponseDto resposta = caixaService.atualizarCaixa(caixa.getId(),
                new CaixaAtualizacaoRequestDto(null, true));

        assertThat(resposta.ativo()).isTrue();
    }

    @Test
    void naoDeveInativarUltimoCaixaComTerminalQuandoPointEstaHabilitada() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        ConfiguracaoCaixaEntity configuracao = new ConfiguracaoCaixaEntity();
        configuracao.setPointHabilitado(true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(sessaoCaixaRepository.existsByCaixaIdAndStatus(caixa.getId(), StatusSessaoCaixa.ABERTA))
                .thenReturn(false);
        when(caixaRepository.saveAndFlush(caixa)).thenReturn(caixa);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId))
                .thenReturn(Optional.of(configuracao));
        when(terminalCaixaRepository.contarAtivosDoEstabelecimento(estabelecimentoId)).thenReturn(0L);

        assertThatThrownBy(() -> caixaService.atualizarCaixa(caixa.getId(),
                new CaixaAtualizacaoRequestDto(null, false)))
                .isInstanceOf(CaixaEstadoInvalidoException.class)
                .hasMessageContaining("Point");

        verifyNoInteractions(auditoriaCaixaService);
    }

    @Test
    void deveCriarTerminalAtivoVinculadoAoCaixa() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.existsByTerminalId("POS-1")).thenReturn(false);
        when(terminalCaixaRepository.existsByCaixaIdAndNomeIgnoreCase(caixa.getId(), "Balcão")).thenReturn(false);
        when(terminalCaixaRepository.saveAndFlush(any(TerminalCaixaEntity.class))).thenAnswer(invocacao -> {
            TerminalCaixaEntity terminal = invocacao.getArgument(0);
            terminal.setId(UUID.randomUUID());
            return terminal;
        });

        TerminalCaixaResponseDto resposta = caixaService.criarTerminal(caixa.getId(),
                new TerminalCaixaRequestDto(" POS-1 ", " Balcão "));

        assertThat(resposta.caixaId()).isEqualTo(caixa.getId());
        assertThat(resposta.terminalId()).isEqualTo("POS-1");
        assertThat(resposta.nome()).isEqualTo("Balcão");
        assertThat(resposta.ativo()).isTrue();
        verify(auditoriaCaixaService).registrar(eq(estabelecimentoId), eq(usuarioId), isNull(),
                eq(EventoAuditoriaCaixa.TERMINAL_CAIXA_CRIADO), eq(EventoAuditoriaCaixa.ENTIDADE_TERMINAL_CAIXA),
                eq(resposta.id().toString()), anyString(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void naoDeveCriarTerminalJaVinculadoAOutroCaixa() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.existsByTerminalId("POS-1")).thenReturn(true);

        assertThatThrownBy(() -> caixaService.criarTerminal(caixa.getId(),
                new TerminalCaixaRequestDto("POS-1", "Balcão")))
                .isInstanceOf(TerminalCaixaJaExisteException.class)
                .hasMessageContaining("vinculado");
    }

    @Test
    void naoDeveCriarTerminalComNomeRepetidoNoMesmoCaixa() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.existsByTerminalId("POS-2")).thenReturn(false);
        when(terminalCaixaRepository.existsByCaixaIdAndNomeIgnoreCase(caixa.getId(), "Balcão")).thenReturn(true);

        assertThatThrownBy(() -> caixaService.criarTerminal(caixa.getId(),
                new TerminalCaixaRequestDto("POS-2", "Balcão")))
                .isInstanceOf(TerminalCaixaJaExisteException.class)
                .hasMessageContaining("nome");
    }

    @Test
    void deveTraduzirViolacaoDeUnicidadeConcorrenteAoSalvarTerminal() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.existsByTerminalId("POS-1")).thenReturn(false);
        when(terminalCaixaRepository.existsByCaixaIdAndNomeIgnoreCase(caixa.getId(), "Balcão")).thenReturn(false);
        when(terminalCaixaRepository.saveAndFlush(any(TerminalCaixaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_terminal"));

        assertThatThrownBy(() -> caixaService.criarTerminal(caixa.getId(),
                new TerminalCaixaRequestDto("POS-1", "Balcão")))
                .isInstanceOf(TerminalCaixaJaExisteException.class)
                .hasMessageContaining("em uso");
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,NULL", "'  ',false"}, nullValues = "NULL")
    void deveRejeitarAtualizacaoDeTerminalSemCamposValidos(String nome, Boolean ativo) {
        assertThatThrownBy(() -> caixaService.atualizarTerminal(UUID.randomUUID(), UUID.randomUUID(),
                new TerminalCaixaAtualizacaoRequestDto(nome, ativo)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(terminalCaixaRepository);
    }

    @Test
    void deveRenomearEInativarTerminalERegistrarAuditoria() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        TerminalCaixaEntity terminal = terminal(caixa, "POS-1", "Balcão", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.findByIdAndCaixaId(terminal.getId(), caixa.getId()))
                .thenReturn(Optional.of(terminal));
        when(terminalCaixaRepository.existsByCaixaIdAndNomeIgnoreCaseAndIdNot(
                caixa.getId(), "Salão", terminal.getId())).thenReturn(false);
        when(terminalCaixaRepository.saveAndFlush(terminal)).thenReturn(terminal);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId)).thenReturn(Optional.empty());

        TerminalCaixaResponseDto resposta = caixaService.atualizarTerminal(caixa.getId(), terminal.getId(),
                new TerminalCaixaAtualizacaoRequestDto(" Salão ", false));

        assertThat(resposta.nome()).isEqualTo("Salão");
        assertThat(resposta.ativo()).isFalse();
        verify(auditoriaCaixaService).registrar(eq(estabelecimentoId), eq(usuarioId), isNull(),
                eq(EventoAuditoriaCaixa.TERMINAL_CAIXA_ALTERADO), eq(EventoAuditoriaCaixa.ENTIDADE_TERMINAL_CAIXA),
                eq(terminal.getId().toString()), anyString(), isNull(), isNull(), any(), any());
    }

    @Test
    void naoDeveRegistrarAuditoriaQuandoTerminalNaoMuda() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        TerminalCaixaEntity terminal = terminal(caixa, "POS-1", "Balcão", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.findByIdAndCaixaId(terminal.getId(), caixa.getId()))
                .thenReturn(Optional.of(terminal));
        when(terminalCaixaRepository.saveAndFlush(terminal)).thenReturn(terminal);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId)).thenReturn(Optional.empty());

        caixaService.atualizarTerminal(caixa.getId(), terminal.getId(),
                new TerminalCaixaAtualizacaoRequestDto("Balcão", true));

        verifyNoInteractions(auditoriaCaixaService);
    }

    @Test
    void naoDeveRenomearTerminalParaNomeJaUsadoNoCaixa() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        TerminalCaixaEntity terminal = terminal(caixa, "POS-1", "Balcão", true);
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.findByIdAndCaixaId(terminal.getId(), caixa.getId()))
                .thenReturn(Optional.of(terminal));
        when(terminalCaixaRepository.existsByCaixaIdAndNomeIgnoreCaseAndIdNot(
                caixa.getId(), "Salão", terminal.getId())).thenReturn(true);

        assertThatThrownBy(() -> caixaService.atualizarTerminal(caixa.getId(), terminal.getId(),
                new TerminalCaixaAtualizacaoRequestDto("Salão", null)))
                .isInstanceOf(TerminalCaixaJaExisteException.class);

        verify(terminalCaixaRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveFalharAoAtualizarTerminalQueNaoPertenceAoCaixa() {
        CaixaEntity caixa = caixa("Caixa 1", true);
        UUID terminalId = UUID.randomUUID();
        when(caixaRepository.findById(caixa.getId())).thenReturn(Optional.of(caixa));
        when(terminalCaixaRepository.findByIdAndCaixaId(terminalId, caixa.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caixaService.atualizarTerminal(caixa.getId(), terminalId,
                new TerminalCaixaAtualizacaoRequestDto(null, false)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Terminal não encontrado");
    }

    @Test
    void deveExigirSessaoJwtValidaParaOperarCaixas() {
        AutenticacaoTeste.autenticar(null, usuarioId);

        assertThatThrownBy(() -> caixaService.criarCaixa(new CaixaRequestDto("Caixa 1")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private CaixaEntity caixa(String nome, boolean ativo) {
        CaixaEntity caixa = new CaixaEntity();
        caixa.setId(UUID.randomUUID());
        caixa.setEstabelecimentoId(estabelecimentoId);
        caixa.setNome(nome);
        caixa.setAtivo(ativo);
        return caixa;
    }

    private TerminalCaixaEntity terminal(CaixaEntity caixa, String terminalId, String nome, boolean ativo) {
        TerminalCaixaEntity terminal = new TerminalCaixaEntity();
        terminal.setId(UUID.randomUUID());
        terminal.setCaixa(caixa);
        terminal.setTerminalId(terminalId);
        terminal.setNome(nome);
        terminal.setAtivo(ativo);
        return terminal;
    }
}
