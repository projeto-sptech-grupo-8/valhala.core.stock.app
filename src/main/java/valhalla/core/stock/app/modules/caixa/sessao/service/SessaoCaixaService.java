package valhalla.core.stock.app.modules.caixa.sessao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.sessao.dto.AbrirSessaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.FecharSessaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.MovimentacaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.MovimentacaoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.ResolverDivergenciaCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.ResumoFechamentoDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.SessaoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.sessao.dto.SessaoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.MovimentacaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.enums.TipoMovimentacaoCaixa;
import valhalla.core.stock.app.modules.caixa.exception.CaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.PagamentoPendenteFechamentoException;
import valhalla.core.stock.app.modules.caixa.exception.SaldoInsuficienteCaixaException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaJaAbertaException;
import valhalla.core.stock.app.modules.caixa.sessao.mapper.MovimentacaoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.sessao.mapper.SessaoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.CaixaRepository;
import valhalla.core.stock.app.modules.caixa.sessao.repository.MovimentacaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.sessao.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;

@Service
public class SessaoCaixaService {

    private static final Set<String> CAMPOS_ORDENACAO =
            Set.of("abertaEm", "fechadaEm", "atualizadoEm", "status", "diferenca");

    private final SessaoCaixaRepository sessaoCaixaRepository;
    private final CaixaRepository caixaRepository;
    private final MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    private final PagamentoPedidoRepository pagamentoPedidoRepository;
    private final UserRepository userRepository;
    private final ContextoCaixa contextoCaixa;
    private final CalculadoraFechamentoCaixa calculadoraFechamentoCaixa;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SessaoCaixaService(
            SessaoCaixaRepository sessaoCaixaRepository,
            CaixaRepository caixaRepository,
            MovimentacaoCaixaRepository movimentacaoCaixaRepository,
            PagamentoPedidoRepository pagamentoPedidoRepository,
            UserRepository userRepository,
            ContextoCaixa contextoCaixa,
            CalculadoraFechamentoCaixa calculadoraFechamentoCaixa,
            AuditoriaCaixaService auditoriaCaixaService,
            BusinessEventLogger businessEventLogger
    ) {
        this.sessaoCaixaRepository = sessaoCaixaRepository;
        this.caixaRepository = caixaRepository;
        this.movimentacaoCaixaRepository = movimentacaoCaixaRepository;
        this.pagamentoPedidoRepository = pagamentoPedidoRepository;
        this.userRepository = userRepository;
        this.contextoCaixa = contextoCaixa;
        this.calculadoraFechamentoCaixa = calculadoraFechamentoCaixa;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public SessaoCaixaDetalheResponseDto abrirSessao(AbrirSessaoCaixaRequestDto dto) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        if (sessaoCaixaRepository.existsByEstabelecimentoIdAndOperadorUsuarioIdAndStatus(
                estabelecimentoId,
                usuarioId,
                StatusSessaoCaixa.ABERTA
        )) {
            throw new SessaoCaixaJaAbertaException(
                    "O operador já possui uma sessão de caixa aberta"
            );
        }

        CaixaEntity caixa = caixaRepository
                .buscarParaAtualizacao(dto.caixaId(), estabelecimentoId)
                .orElseThrow(() -> new EntityNotFoundException("Caixa não encontrado"));

        if (!caixa.isAtivo()) {
            throw new CaixaEstadoInvalidoException("Não é possível abrir sessão em um caixa inativo");
        }

        if (sessaoCaixaRepository.existsByCaixaIdAndStatus(
                caixa.getId(),
                StatusSessaoCaixa.ABERTA
        )) {
            throw new SessaoCaixaJaAbertaException(
                    "Já existe uma sessão de caixa aberta para este caixa"
            );
        }

        SessaoCaixaEntity sessao = new SessaoCaixaEntity();
        sessao.setEstabelecimentoId(estabelecimentoId);
        sessao.setCaixa(caixa);
        sessao.setOperadorUsuarioId(usuarioId);
        sessao.setStatus(StatusSessaoCaixa.ABERTA);
        sessao.setFundoTroco(escala(dto.fundoTroco()));
        sessao.setObservacaoAbertura(normalizarTexto(dto.observacao()));
        sessao.setAbertaEm(LocalDateTime.now());
        sessao.setAtualizadoEm(LocalDateTime.now());

        SessaoCaixaEntity sessaoSalva = salvarSessao(sessao);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                sessaoSalva.getId(),
                EventoAuditoriaCaixa.ABERTURA_CAIXA,
                EventoAuditoriaCaixa.ENTIDADE_SESSAO_CAIXA,
                sessaoSalva.getId().toString(),
                "Sessão de caixa aberta",
                sessaoSalva.getFundoTroco(),
                null,
                null,
                objectMapper.valueToTree(snapshotSessao(sessaoSalva))
        );

        businessEventLogger.success("cash.session.opened", "cash_session", sessaoSalva.getId());

        return montarDetalhe(sessaoSalva);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public Optional<SessaoCaixaDetalheResponseDto> buscarSessaoAtual() {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        return sessaoCaixaRepository
                .findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                        estabelecimentoId,
                        usuarioId,
                        StatusSessaoCaixa.ABERTA
                )
                .map(this::montarDetalhe);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public RespostaPaginadaDto<SessaoCaixaResumoResponseDto> listarSessoes(
            StatusSessaoCaixa status,
            UUID caixaId,
            UUID operadorUsuarioId,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        validarPeriodo(dataInicio, dataFim);

        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();

        Specification<SessaoCaixaEntity> especificacao =
                (root, query, builder) -> builder.equal(root.get("estabelecimentoId"), estabelecimentoId);

        if (status != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("status"), status)
            );
        }
        if (caixaId != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("caixa").get("id"), caixaId)
            );
        }
        if (operadorUsuarioId != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("operadorUsuarioId"), operadorUsuarioId)
            );
        }
        if (dataInicio != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.greaterThanOrEqualTo(
                            root.get("abertaEm"),
                            dataInicio.atStartOfDay()
                    )
            );
        }
        if (dataFim != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.lessThan(
                            root.get("abertaEm"),
                            dataFim.plusDays(1).atStartOfDay()
                    )
            );
        }

        Page<SessaoCaixaEntity> resultado = sessaoCaixaRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO)
        );

        Map<UUID, String> nomes = carregarNomes(resultado.getContent());

        return RespostaPaginadaDto.de(
                resultado,
                sessao -> SessaoCaixaMapper.paraResumo(sessao, nomes)
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public SessaoCaixaDetalheResponseDto buscarSessao(UUID id) {
        SessaoCaixaEntity sessao = buscarDoEstabelecimento(id);
        return montarDetalhe(sessao);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public SessaoCaixaDetalheResponseDto registrarMovimentacao(
            UUID id,
            MovimentacaoCaixaRequestDto dto
    ) {
        UUID usuarioId = contextoCaixa.usuarioId();
        SessaoCaixaEntity sessao = buscarParaAtualizacaoDoEstabelecimento(id);

        validarOperadorOuGerente(sessao, usuarioId);
        exigirStatusAberta(sessao, "registrar sangria ou reforço");

        List<PagamentoPedidoEntity> pagamentos = pagamentosDaSessao(sessao.getId());
        List<MovimentacaoCaixaEntity> movimentacoes = movimentacoesDaSessao(sessao.getId());

        if (dto.tipo() == TipoMovimentacaoCaixa.SANGRIA) {
            BigDecimal disponivel = calculadoraFechamentoCaixa.calcularDinheiroDisponivel(
                    sessao,
                    pagamentos,
                    movimentacoes
            );

            if (dto.valor().compareTo(disponivel) > 0) {
                throw new SaldoInsuficienteCaixaException(
                        "A sangria não pode ser maior que o dinheiro disponível no caixa",
                        disponivel
                );
            }
        }

        MovimentacaoCaixaEntity movimentacao = new MovimentacaoCaixaEntity();
        movimentacao.setSessaoCaixaId(sessao.getId());
        movimentacao.setTipo(dto.tipo());
        movimentacao.setValor(escala(dto.valor()));
        movimentacao.setMotivo(dto.motivo().trim());
        movimentacao.setUsuarioId(usuarioId);
        movimentacao.setOcorridoEm(LocalDateTime.now());

        MovimentacaoCaixaEntity movimentacaoSalva =
                movimentacaoCaixaRepository.saveAndFlush(movimentacao);

        auditoriaCaixaService.registrar(
                sessao.getEstabelecimentoId(),
                usuarioId,
                sessao.getId(),
                dto.tipo() == TipoMovimentacaoCaixa.SANGRIA
                        ? EventoAuditoriaCaixa.SANGRIA
                        : EventoAuditoriaCaixa.REFORCO,
                "MOVIMENTACAO_CAIXA",
                movimentacaoSalva.getId().toString(),
                dto.tipo() == TipoMovimentacaoCaixa.SANGRIA
                        ? "Sangria registrada no caixa"
                        : "Reforço registrado no caixa",
                movimentacaoSalva.getValor(),
                movimentacaoSalva.getMotivo(),
                null,
                objectMapper.valueToTree(snapshotMovimentacao(movimentacaoSalva))
        );

        businessEventLogger.success(
                dto.tipo() == TipoMovimentacaoCaixa.SANGRIA
                        ? "cash.session.withdrawn"
                        : "cash.session.supplied",
                "cash_session",
                sessao.getId()
        );

        return montarDetalhe(sessao);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_FECHAR',
                authentication
            )
            """)
    public SessaoCaixaDetalheResponseDto fecharSessao(
            UUID id,
            FecharSessaoCaixaRequestDto dto
    ) {
        UUID usuarioId = contextoCaixa.usuarioId();
        SessaoCaixaEntity sessao = buscarParaAtualizacaoDoEstabelecimento(id);

        validarOperadorOuGerente(sessao, usuarioId);
        exigirStatusAberta(sessao, "fechar");

        List<PagamentoPedidoEntity> pagamentos = pagamentosDaSessao(sessao.getId());
        List<MovimentacaoCaixaEntity> movimentacoes = movimentacoesDaSessao(sessao.getId());

        Map<String, Long> pendencias = calculadoraFechamentoCaixa.pendenciasPorStatus(pagamentos);
        if (!pendencias.isEmpty()) {
            throw new PagamentoPendenteFechamentoException(
                    "Não é possível fechar o caixa enquanto houver pagamentos pendentes",
                    pendencias
            );
        }

        ResumoFechamentoDto resumo = calculadoraFechamentoCaixa.calcularResumo(
                sessao,
                pagamentos,
                movimentacoes,
                dto.dinheiroContado()
        );

        BigDecimal diferenca = resumo.diferenca();
        boolean possuiDiferenca = diferenca != null && diferenca.compareTo(BigDecimal.ZERO) != 0;
        String justificativa = normalizarTexto(dto.justificativaDivergencia());

        if (possuiDiferenca && justificativa == null) {
            throw new SessaoCaixaEstadoInvalidoException(
                    "A justificativa é obrigatória quando o fechamento apresenta diferença"
            );
        }

        sessao.setDinheiroEsperado(resumo.dinheiroEsperado());
        sessao.setDinheiroContado(resumo.dinheiroContado());
        sessao.setDiferenca(diferenca);
        sessao.setResumoFechamento(serializarResumo(resumo));
        sessao.setObservacaoFechamento(normalizarTexto(dto.observacao()));
        sessao.setJustificativaDivergencia(justificativa);
        sessao.setFechadaEm(LocalDateTime.now());
        sessao.setFechadaPorUsuarioId(usuarioId);
        sessao.setStatus(
                possuiDiferenca ? StatusSessaoCaixa.EM_FECHAMENTO : StatusSessaoCaixa.FECHADA
        );

        SessaoCaixaEntity sessaoSalva = sessaoCaixaRepository.saveAndFlush(sessao);

        auditoriaCaixaService.registrar(
                sessaoSalva.getEstabelecimentoId(),
                usuarioId,
                sessaoSalva.getId(),
                EventoAuditoriaCaixa.FECHAMENTO_CAIXA,
                EventoAuditoriaCaixa.ENTIDADE_SESSAO_CAIXA,
                sessaoSalva.getId().toString(),
                possuiDiferenca
                        ? "Fechamento submetido com divergência de caixa"
                        : "Sessão de caixa fechada sem divergência",
                sessaoSalva.getDinheiroContado(),
                justificativa,
                null,
                objectMapper.valueToTree(snapshotSessao(sessaoSalva))
        );

        if (possuiDiferenca) {
            auditoriaCaixaService.registrar(
                    sessaoSalva.getEstabelecimentoId(),
                    usuarioId,
                    sessaoSalva.getId(),
                    EventoAuditoriaCaixa.DIVERGENCIA_CAIXA,
                    EventoAuditoriaCaixa.ENTIDADE_SESSAO_CAIXA,
                    sessaoSalva.getId().toString(),
                    "Divergência de caixa registrada no fechamento",
                    diferenca,
                    justificativa,
                    null,
                    objectMapper.valueToTree(Map.of(
                            "dinheiroEsperado", sessaoSalva.getDinheiroEsperado(),
                            "dinheiroContado", sessaoSalva.getDinheiroContado(),
                            "diferenca", sessaoSalva.getDiferenca()
                    ))
            );
        }

        businessEventLogger.success(
                possuiDiferenca ? "cash.session.closed-with-divergence" : "cash.session.closed",
                "cash_session",
                sessaoSalva.getId()
        );

        return montarDetalhe(sessaoSalva);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_RESOLVER_DIVERGENCIA',
                authentication
            )
            """)
    public SessaoCaixaDetalheResponseDto resolverDivergencia(
            UUID id,
            ResolverDivergenciaCaixaRequestDto dto
    ) {
        UUID usuarioId = contextoCaixa.usuarioId();
        SessaoCaixaEntity sessao = buscarParaAtualizacaoDoEstabelecimento(id);

        if (!sessao.isEmFechamento()) {
            throw new SessaoCaixaEstadoInvalidoException(
                    "Somente sessões em fechamento podem ter a divergência resolvida"
            );
        }
        if (sessao.getDivergenciaResolvidaEm() != null) {
            throw new SessaoCaixaEstadoInvalidoException(
                    "A divergência desta sessão já foi resolvida"
            );
        }

        sessao.setStatus(StatusSessaoCaixa.FECHADA);
        sessao.setDivergenciaResolvidaPorUsuarioId(usuarioId);
        sessao.setDivergenciaResolvidaEm(LocalDateTime.now());

        String observacao = normalizarTexto(dto == null ? null : dto.observacao());
        if (observacao != null) {
            String observacaoAtual = normalizarTexto(sessao.getObservacaoFechamento());
            sessao.setObservacaoFechamento(
                    observacaoAtual == null ? observacao : observacaoAtual + "\n" + observacao
            );
        }

        SessaoCaixaEntity sessaoSalva = sessaoCaixaRepository.saveAndFlush(sessao);

        Map<String, Object> dadosAnteriores = new LinkedHashMap<>();
        dadosAnteriores.put("status", StatusSessaoCaixa.EM_FECHAMENTO.name());
        dadosAnteriores.put("divergenciaResolvidaPorUsuarioId", null);
        dadosAnteriores.put("divergenciaResolvidaEm", null);

        auditoriaCaixaService.registrar(
                sessaoSalva.getEstabelecimentoId(),
                usuarioId,
                sessaoSalva.getId(),
                EventoAuditoriaCaixa.DIVERGENCIA_CAIXA_RESOLVIDA,
                EventoAuditoriaCaixa.ENTIDADE_SESSAO_CAIXA,
                sessaoSalva.getId().toString(),
                "Divergência de caixa aprovada pelo gerente",
                sessaoSalva.getDiferenca(),
                observacao,
                objectMapper.valueToTree(dadosAnteriores),
                objectMapper.valueToTree(snapshotSessao(sessaoSalva))
        );

        businessEventLogger.success(
                "cash.session.divergence-resolved",
                "cash_session",
                sessaoSalva.getId()
        );

        return montarDetalhe(sessaoSalva);
    }

    private SessaoCaixaEntity salvarSessao(SessaoCaixaEntity sessao) {
        try {
            return sessaoCaixaRepository.saveAndFlush(sessao);
        } catch (DataIntegrityViolationException excecao) {
            throw new SessaoCaixaJaAbertaException(
                    "Já existe uma sessão de caixa aberta para este operador ou caixa"
            );
        }
    }

    private SessaoCaixaEntity buscarDoEstabelecimento(UUID id) {
        SessaoCaixaEntity sessao = sessaoCaixaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sessão de caixa não encontrada"));

        validarEstabelecimento(sessao);
        return sessao;
    }

    private SessaoCaixaEntity buscarParaAtualizacaoDoEstabelecimento(UUID id) {
        SessaoCaixaEntity sessao = sessaoCaixaRepository.buscarParaAtualizacao(id)
                .orElseThrow(() -> new EntityNotFoundException("Sessão de caixa não encontrada"));

        validarEstabelecimento(sessao);
        return sessao;
    }

    private void validarEstabelecimento(SessaoCaixaEntity sessao) {
        if (!sessao.getEstabelecimentoId().equals(contextoCaixa.estabelecimentoId())) {
            throw new AccessDeniedException("Acesso negado");
        }
    }

    private void validarOperadorOuGerente(SessaoCaixaEntity sessao, UUID usuarioId) {
        if (!sessao.getOperadorUsuarioId().equals(usuarioId) && !contextoCaixa.isGerente()) {
            throw new AccessDeniedException(
                    "Somente o operador da sessão ou um gerente pode executar esta ação"
            );
        }
    }

    private void exigirStatusAberta(SessaoCaixaEntity sessao, String acao) {
        if (!sessao.isAberta()) {
            throw new SessaoCaixaEstadoInvalidoException(
                    "Não é possível " + acao + " uma sessão com status " + sessao.getStatus()
            );
        }
    }

    private List<PagamentoPedidoEntity> pagamentosDaSessao(UUID sessaoId) {
        return pagamentoPedidoRepository.findBySessaoCaixaIdOrderByCriadoEmAsc(sessaoId);
    }

    private List<MovimentacaoCaixaEntity> movimentacoesDaSessao(UUID sessaoId) {
        return movimentacaoCaixaRepository.findBySessaoCaixaIdOrderByOcorridoEmAsc(sessaoId);
    }

    private SessaoCaixaDetalheResponseDto montarDetalhe(SessaoCaixaEntity sessao) {
        List<PagamentoPedidoEntity> pagamentos = pagamentosDaSessao(sessao.getId());
        List<MovimentacaoCaixaEntity> movimentacoes = movimentacoesDaSessao(sessao.getId());

        ResumoFechamentoDto resumoAtual = sessao.isAberta()
                ? calculadoraFechamentoCaixa.calcularResumo(sessao, pagamentos, movimentacoes, null)
                : null;

        ResumoFechamentoDto resumoFechamento = desserializarResumo(sessao.getResumoFechamento());
        if (resumoFechamento == null && !sessao.isAberta()) {
            resumoFechamento = calculadoraFechamentoCaixa.calcularResumo(
                    sessao,
                    pagamentos,
                    movimentacoes,
                    sessao.getDinheiroContado()
            );
        }

        Map<UUID, String> nomes = carregarNomesSessao(sessao, movimentacoes);

        List<MovimentacaoCaixaResponseDto> movimentacoesResposta = movimentacoes.stream()
                .map(movimentacao -> MovimentacaoCaixaMapper.paraResposta(
                        movimentacao,
                        nomes.get(movimentacao.getUsuarioId())
                ))
                .toList();

        return SessaoCaixaMapper.paraDetalhe(
                sessao,
                nomes,
                resumoAtual,
                resumoFechamento,
                movimentacoesResposta
        );
    }

    private Map<UUID, String> carregarNomes(Collection<SessaoCaixaEntity> sessoes) {
        Set<UUID> ids = sessoes.stream()
                .flatMap(sessao -> java.util.stream.Stream.of(
                        sessao.getOperadorUsuarioId(),
                        sessao.getFechadaPorUsuarioId(),
                        sessao.getDivergenciaResolvidaPorUsuarioId()
                ))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        return carregarNomesPorIds(ids);
    }

    private Map<UUID, String> carregarNomesSessao(
            SessaoCaixaEntity sessao,
            List<MovimentacaoCaixaEntity> movimentacoes
    ) {
        Set<UUID> ids = movimentacoes.stream()
                .map(MovimentacaoCaixaEntity::getUsuarioId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        java.util.stream.Stream.of(
                        sessao.getOperadorUsuarioId(),
                        sessao.getFechadaPorUsuarioId(),
                        sessao.getDivergenciaResolvidaPorUsuarioId()
                )
                .filter(java.util.Objects::nonNull)
                .forEach(ids::add);

        return carregarNomesPorIds(ids);
    }

    private Map<UUID, String> carregarNomesPorIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    private JsonNode serializarResumo(ResumoFechamentoDto resumo) {
        return objectMapper.valueToTree(resumo);
    }

    private ResumoFechamentoDto desserializarResumo(JsonNode json) {
        if (json == null || json.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(json, ResumoFechamentoDto.class);
        } catch (JsonProcessingException excecao) {
            throw new IllegalStateException("Resumo de fechamento inválido na sessão de caixa", excecao);
        }
    }

    private Map<String, Object> snapshotSessao(SessaoCaixaEntity sessao) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("caixaId", sessao.getCaixa().getId().toString());
        dados.put("caixaNome", sessao.getCaixa().getNome());
        dados.put("operadorUsuarioId", texto(sessao.getOperadorUsuarioId()));
        dados.put("status", sessao.getStatus().name());
        dados.put("fundoTroco", sessao.getFundoTroco());
        dados.put("dinheiroEsperado", sessao.getDinheiroEsperado());
        dados.put("dinheiroContado", sessao.getDinheiroContado());
        dados.put("diferenca", sessao.getDiferenca());
        dados.put("observacaoAbertura", sessao.getObservacaoAbertura());
        dados.put("observacaoFechamento", sessao.getObservacaoFechamento());
        dados.put("justificativaDivergencia", sessao.getJustificativaDivergencia());
        dados.put("abertaEm", texto(sessao.getAbertaEm()));
        dados.put("fechadaEm", texto(sessao.getFechadaEm()));
        dados.put("fechadaPorUsuarioId", texto(sessao.getFechadaPorUsuarioId()));
        dados.put("divergenciaResolvidaPorUsuarioId",
                texto(sessao.getDivergenciaResolvidaPorUsuarioId()));
        dados.put("divergenciaResolvidaEm", texto(sessao.getDivergenciaResolvidaEm()));
        return dados;
    }

    private Map<String, Object> snapshotMovimentacao(MovimentacaoCaixaEntity movimentacao) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("sessaoCaixaId", movimentacao.getSessaoCaixaId().toString());
        dados.put("tipo", movimentacao.getTipo().name());
        dados.put("valor", movimentacao.getValor());
        dados.put("motivo", movimentacao.getMotivo());
        dados.put("usuarioId", movimentacao.getUsuarioId().toString());
        dados.put("ocorridoEm", texto(movimentacao.getOcorridoEm()));
        return dados;
    }

    private void validarPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
    }

    private String normalizarTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private BigDecimal escala(BigDecimal valor) {
        return valor == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
