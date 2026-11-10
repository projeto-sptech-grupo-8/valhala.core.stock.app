package valhalla.core.stock.app.modules.caixa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.dto.CaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.CaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.CaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.TerminalCaixaEntity;
import valhalla.core.stock.app.modules.caixa.exception.CaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.CaixaJaExisteException;
import valhalla.core.stock.app.modules.caixa.exception.TerminalCaixaJaExisteException;
import valhalla.core.stock.app.modules.caixa.mapper.CaixaMapper;
import valhalla.core.stock.app.modules.caixa.mapper.TerminalCaixaMapper;
import valhalla.core.stock.app.modules.caixa.repository.CaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.TerminalCaixaRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaixaService {

    private static final Set<String> CAMPOS_ORDENACAO =
            Set.of("nome", "criadoEm", "atualizadoEm");

    private final CaixaRepository caixaRepository;
    private final TerminalCaixaRepository terminalCaixaRepository;
    private final SessaoCaixaRepository sessaoCaixaRepository;
    private final DisponibilidadePoint disponibilidadePoint;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final ContextoCaixa contextoCaixa;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CaixaService(
            CaixaRepository caixaRepository,
            TerminalCaixaRepository terminalCaixaRepository,
            SessaoCaixaRepository sessaoCaixaRepository,
            DisponibilidadePoint disponibilidadePoint,
            AuditoriaCaixaService auditoriaCaixaService,
            ContextoCaixa contextoCaixa,
            BusinessEventLogger businessEventLogger
    ) {
        this.caixaRepository = caixaRepository;
        this.terminalCaixaRepository = terminalCaixaRepository;
        this.sessaoCaixaRepository = sessaoCaixaRepository;
        this.disponibilidadePoint = disponibilidadePoint;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.contextoCaixa = contextoCaixa;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public RespostaPaginadaDto<CaixaResponseDto> listarCaixas(
            String busca,
            Boolean ativo,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        String buscaNormalizada = busca == null || busca.isBlank()
                ? null
                : busca.trim().toLowerCase(Locale.ROOT);

        Specification<CaixaEntity> especificacao = (root, query, builder) ->
                builder.equal(root.get("estabelecimentoId"), estabelecimentoId);

        if (buscaNormalizada != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.like(
                    builder.lower(root.get("nome")),
                    "%" + buscaNormalizada + "%"
            ));
        }
        if (ativo != null) {
            especificacao = especificacao.and((root, query, builder) ->
                    builder.equal(root.get("ativo"), ativo));
        }

        Page<CaixaEntity> resultado = caixaRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO)
        );

        Map<UUID, List<TerminalCaixaResponseDto>> terminaisPorCaixa =
                carregarTerminais(resultado.getContent());
        Set<UUID> caixasEmUso = new HashSet<>(sessaoCaixaRepository.buscarCaixaIdsPorStatus(
                estabelecimentoId,
                StatusSessaoCaixa.ABERTA
        ));

        return RespostaPaginadaDto.de(
                resultado,
                caixa -> CaixaMapper.paraResposta(
                        caixa,
                        caixasEmUso.contains(caixa.getId()),
                        terminaisPorCaixa.getOrDefault(caixa.getId(), List.of())
                )
        );
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public CaixaResponseDto criarCaixa(CaixaRequestDto dto) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();
        String nome = dto.nome().trim();

        if (caixaRepository.existsByEstabelecimentoIdAndNomeIgnoreCase(estabelecimentoId, nome)) {
            throw new CaixaJaExisteException("Já existe um caixa com este nome no estabelecimento");
        }

        CaixaEntity caixa = new CaixaEntity();
        caixa.setEstabelecimentoId(estabelecimentoId);
        caixa.setNome(nome);
        caixa.setAtivo(true);

        CaixaEntity caixaSalvo = salvarCaixa(caixa);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                null,
                EventoAuditoriaCaixa.CAIXA_CRIADO,
                EventoAuditoriaCaixa.ENTIDADE_CAIXA,
                caixaSalvo.getId().toString(),
                "Caixa criado: " + caixaSalvo.getNome(),
                null,
                null,
                null,
                objectMapper.valueToTree(CaixaMapper.snapshot(caixaSalvo))
        );

        businessEventLogger.success("cash.register.created", "cash_register", caixaSalvo.getId());
        return CaixaMapper.paraResposta(caixaSalvo, false, List.of());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public CaixaResponseDto buscarCaixa(UUID id) {
        CaixaEntity caixa = buscarDoEstabelecimento(id);
        return montarResposta(caixa);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public CaixaResponseDto atualizarCaixa(UUID id, CaixaAtualizacaoRequestDto dto) {
        validarAtualizacaoCaixa(dto);

        UUID usuarioId = contextoCaixa.usuarioId();
        CaixaEntity caixa = buscarDoEstabelecimento(id);
        Map<String, Object> dadosAnteriores = CaixaMapper.snapshot(caixa);
        boolean alterado = false;

        if (dto.nome() != null) {
            String nome = dto.nome().trim();
            if (!caixa.getNome().equals(nome)) {
                if (caixaRepository.existsByEstabelecimentoIdAndNomeIgnoreCaseAndIdNot(
                        caixa.getEstabelecimentoId(),
                        nome,
                        caixa.getId()
                )) {
                    throw new CaixaJaExisteException(
                            "Já existe um caixa com este nome no estabelecimento"
                    );
                }
                caixa.setNome(nome);
                alterado = true;
            }
        }

        if (dto.ativo() != null && caixa.isAtivo() != dto.ativo()) {
            if (!dto.ativo() && sessaoCaixaRepository.existsByCaixaIdAndStatus(
                    caixa.getId(),
                    StatusSessaoCaixa.ABERTA
            )) {
                throw new CaixaEstadoInvalidoException(
                        "Não é possível inativar um caixa com sessão aberta"
                );
            }
            caixa.setAtivo(dto.ativo());
            alterado = true;
        }

        CaixaEntity caixaSalvo = salvarCaixa(caixa);
        disponibilidadePoint.exigirTerminalAtivoSePointHabilitada(caixaSalvo.getEstabelecimentoId());

        if (alterado) {
            auditoriaCaixaService.registrar(
                    caixaSalvo.getEstabelecimentoId(),
                    usuarioId,
                    null,
                    EventoAuditoriaCaixa.CAIXA_ALTERADO,
                    EventoAuditoriaCaixa.ENTIDADE_CAIXA,
                    caixaSalvo.getId().toString(),
                    "Caixa atualizado: " + caixaSalvo.getNome(),
                    null,
                    null,
                    objectMapper.valueToTree(dadosAnteriores),
                    objectMapper.valueToTree(CaixaMapper.snapshot(caixaSalvo))
            );

            businessEventLogger.success("cash.register.updated", "cash_register", caixaSalvo.getId());
        }

        return montarResposta(caixaSalvo);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public TerminalCaixaResponseDto criarTerminal(UUID caixaId, TerminalCaixaRequestDto dto) {
        UUID usuarioId = contextoCaixa.usuarioId();
        CaixaEntity caixa = buscarDoEstabelecimento(caixaId);
        String terminalId = dto.terminalId().trim();
        String nome = dto.nome().trim();

        if (terminalCaixaRepository.existsByTerminalId(terminalId)) {
            throw new TerminalCaixaJaExisteException(
                    "Este terminal já está vinculado a um caixa"
            );
        }
        if (terminalCaixaRepository.existsByCaixaIdAndNomeIgnoreCase(caixa.getId(), nome)) {
            throw new TerminalCaixaJaExisteException(
                    "Já existe um terminal com este nome neste caixa"
            );
        }

        TerminalCaixaEntity terminal = new TerminalCaixaEntity();
        terminal.setCaixa(caixa);
        terminal.setTerminalId(terminalId);
        terminal.setNome(nome);
        terminal.setAtivo(true);

        TerminalCaixaEntity terminalSalvo = salvarTerminal(terminal);

        auditoriaCaixaService.registrar(
                caixa.getEstabelecimentoId(),
                usuarioId,
                null,
                EventoAuditoriaCaixa.TERMINAL_CAIXA_CRIADO,
                EventoAuditoriaCaixa.ENTIDADE_TERMINAL_CAIXA,
                terminalSalvo.getId().toString(),
                "Terminal vinculado ao caixa " + caixa.getNome(),
                null,
                null,
                null,
                objectMapper.valueToTree(TerminalCaixaMapper.snapshot(terminalSalvo))
        );

        businessEventLogger.success("cash.terminal.created", "cash_terminal", terminalSalvo.getId());
        return TerminalCaixaMapper.paraResposta(terminalSalvo);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public TerminalCaixaResponseDto atualizarTerminal(
            UUID caixaId,
            UUID terminalId,
            TerminalCaixaAtualizacaoRequestDto dto
    ) {
        validarAtualizacaoTerminal(dto);

        UUID usuarioId = contextoCaixa.usuarioId();
        CaixaEntity caixa = buscarDoEstabelecimento(caixaId);
        TerminalCaixaEntity terminal = terminalCaixaRepository
                .findByIdAndCaixaId(terminalId, caixa.getId())
                .orElseThrow(() -> new EntityNotFoundException("Terminal não encontrado"));

        Map<String, Object> dadosAnteriores = TerminalCaixaMapper.snapshot(terminal);
        boolean alterado = false;

        if (dto.nome() != null) {
            String nome = dto.nome().trim();
            if (!terminal.getNome().equals(nome)) {
                if (terminalCaixaRepository.existsByCaixaIdAndNomeIgnoreCaseAndIdNot(
                        caixa.getId(),
                        nome,
                        terminal.getId()
                )) {
                    throw new TerminalCaixaJaExisteException(
                            "Já existe um terminal com este nome neste caixa"
                    );
                }
                terminal.setNome(nome);
                alterado = true;
            }
        }

        if (dto.ativo() != null && terminal.isAtivo() != dto.ativo()) {
            terminal.setAtivo(dto.ativo());
            alterado = true;
        }

        TerminalCaixaEntity terminalSalvo = salvarTerminal(terminal);
        disponibilidadePoint.exigirTerminalAtivoSePointHabilitada(caixa.getEstabelecimentoId());

        if (alterado) {
            auditoriaCaixaService.registrar(
                    caixa.getEstabelecimentoId(),
                    usuarioId,
                    null,
                    EventoAuditoriaCaixa.TERMINAL_CAIXA_ALTERADO,
                    EventoAuditoriaCaixa.ENTIDADE_TERMINAL_CAIXA,
                    terminalSalvo.getId().toString(),
                    "Terminal atualizado: " + terminalSalvo.getNome(),
                    null,
                    null,
                    objectMapper.valueToTree(dadosAnteriores),
                    objectMapper.valueToTree(TerminalCaixaMapper.snapshot(terminalSalvo))
            );

            businessEventLogger.success(
                    "cash.terminal.updated",
                    "cash_terminal",
                    terminalSalvo.getId()
            );
        }

        return TerminalCaixaMapper.paraResposta(terminalSalvo);
    }

    private CaixaEntity buscarDoEstabelecimento(UUID id) {
        CaixaEntity caixa = caixaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Caixa não encontrado"));

        if (!caixa.getEstabelecimentoId().equals(contextoCaixa.estabelecimentoId())) {
            throw new AccessDeniedException("Acesso negado");
        }
        return caixa;
    }

    private CaixaResponseDto montarResposta(CaixaEntity caixa) {
        List<TerminalCaixaResponseDto> terminais = terminalCaixaRepository
                .findAllByCaixaIdOrderByNomeAsc(caixa.getId())
                .stream()
                .map(TerminalCaixaMapper::paraResposta)
                .toList();

        boolean emUso = sessaoCaixaRepository.existsByCaixaIdAndStatus(
                caixa.getId(),
                StatusSessaoCaixa.ABERTA
        );

        return CaixaMapper.paraResposta(caixa, emUso, terminais);
    }

    private Map<UUID, List<TerminalCaixaResponseDto>> carregarTerminais(Collection<CaixaEntity> caixas) {
        if (caixas.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = caixas.stream().map(CaixaEntity::getId).toList();
        return terminalCaixaRepository.findAllByCaixaIdInOrderByNomeAsc(ids).stream()
                .map(TerminalCaixaMapper::paraResposta)
                .collect(Collectors.groupingBy(TerminalCaixaResponseDto::caixaId));
    }

    private CaixaEntity salvarCaixa(CaixaEntity caixa) {
        try {
            return caixaRepository.saveAndFlush(caixa);
        } catch (DataIntegrityViolationException excecao) {
            throw new CaixaJaExisteException("Já existe um caixa com este nome no estabelecimento");
        }
    }

    private TerminalCaixaEntity salvarTerminal(TerminalCaixaEntity terminal) {
        try {
            return terminalCaixaRepository.saveAndFlush(terminal);
        } catch (DataIntegrityViolationException excecao) {
            throw new TerminalCaixaJaExisteException(
                    "Identificador ou nome do terminal já está em uso"
            );
        }
    }

    private void validarAtualizacaoCaixa(CaixaAtualizacaoRequestDto dto) {
        if (dto.nome() == null && dto.ativo() == null) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualização");
        }
        if (dto.nome() != null && dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome do caixa não pode ficar vazio");
        }
    }

    private void validarAtualizacaoTerminal(TerminalCaixaAtualizacaoRequestDto dto) {
        if (dto.nome() == null && dto.ativo() == null) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualização");
        }
        if (dto.nome() != null && dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome do terminal não pode ficar vazio");
        }
    }
}
