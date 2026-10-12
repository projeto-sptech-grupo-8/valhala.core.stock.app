package valhalla.core.stock.app.modules.dashboard.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.dashboard.dto.AlertaEstoqueDashboardResponseDto;
import valhalla.core.stock.app.modules.dashboard.dto.DashboardResumoResponseDto;
import valhalla.core.stock.app.modules.dashboard.dto.DirecaoMovimentacaoDashboard;
import valhalla.core.stock.app.modules.dashboard.dto.DisponibilidadeModuloDashboard;
import valhalla.core.stock.app.modules.dashboard.dto.EstoqueDashboardResponseDto;
import valhalla.core.stock.app.modules.dashboard.dto.ModuloDashboardResponseDto;
import valhalla.core.stock.app.modules.dashboard.dto.MovimentacaoMensalDashboardResponseDto;
import valhalla.core.stock.app.modules.dashboard.dto.MovimentacoesDashboardResponseDto;
import valhalla.core.stock.app.modules.dashboard.dto.ReferenciaDashboardResponseDto;
import valhalla.core.stock.app.modules.dashboard.dto.StatusEstoqueDashboard;
import valhalla.core.stock.app.modules.dashboard.dto.UltimaMovimentacaoDashboardResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ResumoEstoqueDashboardProjection;
import valhalla.core.stock.app.modules.estoque.repository.ResumoMovimentacaoMensalDashboardProjection;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardResumoService {

    private static final int LIMITE_ALERTAS = 5;
    private static final int LIMITE_MOVIMENTACOES_RECENTES = 5;
    private static final ZoneId FUSO_HORARIO_OPERACAO = ZoneId.of("America/Sao_Paulo");
    private static final Set<TipoMovimentacaoEstoque> TIPOS_ENTRADA = EnumSet.of(
            TipoMovimentacaoEstoque.ENTRADA_INICIAL,
            TipoMovimentacaoEstoque.ENTRADA,
            TipoMovimentacaoEstoque.AJUSTE_POSITIVO
    );
    private static final Set<TipoMovimentacaoEstoque> TIPOS_SAIDA = EnumSet.of(
            TipoMovimentacaoEstoque.SAIDA,
            TipoMovimentacaoEstoque.SAIDA_DRINK,
            TipoMovimentacaoEstoque.PERDA,
            TipoMovimentacaoEstoque.AJUSTE_NEGATIVO
    );

    private final EstoqueRepository estoqueRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final UserRepository userRepository;

    public DashboardResumoService(
            EstoqueRepository estoqueRepository,
            MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
            UserRepository userRepository
    ) {
        this.estoqueRepository = estoqueRepository;
        this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_ESTOQUE', authentication)")
    public DashboardResumoResponseDto obterResumo(int meses) {
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        LocalDateTime geradoEm = LocalDateTime.now(FUSO_HORARIO_OPERACAO);
        LocalDate dataFim = geradoEm.toLocalDate();
        LocalDate dataInicio = dataFim.withDayOfMonth(1).minusMonths(meses - 1L);

        return new DashboardResumoResponseDto(
                new ReferenciaDashboardResponseDto(
                        geradoEm,
                        dataInicio,
                        dataFim,
                        FUSO_HORARIO_OPERACAO.getId()
                ),
                obterEstoque(idEstabelecimento),
                obterMovimentacoes(idEstabelecimento, dataInicio, dataFim, meses),
                moduloIndisponivel(),
                moduloIndisponivel()
        );
    }

    private EstoqueDashboardResponseDto obterEstoque(UUID idEstabelecimento) {
        ResumoEstoqueDashboardProjection resumo = estoqueRepository.resumirParaDashboard(idEstabelecimento);
        long criticos = resumo.getCriticos() + resumo.getZerados();
        long produtosComMinimo = resumo.getProdutosComMinimo();
        int saudePercentual = produtosComMinimo == 0
                ? 100
                : Math.toIntExact(Math.round((resumo.getSaudaveis() * 100.0) / produtosComMinimo));

        List<AlertaEstoqueDashboardResponseDto> alertas = estoqueRepository.listarCriticosParaDashboard(
                        idEstabelecimento,
                        PageRequest.of(0, LIMITE_ALERTAS)
                ).stream()
                .map(this::paraAlerta)
                .toList();

        return new EstoqueDashboardResponseDto(
                resumo.getTotalProdutos(),
                produtosComMinimo,
                resumo.getProdutosSemMinimo(),
                criticos,
                resumo.getZerados(),
                resumo.getSaudaveis(),
                saudePercentual,
                alertas
        );
    }

    private MovimentacoesDashboardResponseDto obterMovimentacoes(
            UUID idEstabelecimento,
            LocalDate dataInicio,
            LocalDate dataFim,
            int meses
    ) {
        List<MovimentacaoEstoqueEntity> recentes = movimentacaoEstoqueRepository.listarRecentesParaDashboard(
                idEstabelecimento,
                PageRequest.of(0, LIMITE_MOVIMENTACOES_RECENTES)
        );
        Map<UUID, String> nomesUsuarios = nomesUsuarios(recentes);

        List<ResumoMovimentacaoMensalDashboardProjection> resumoMensal = movimentacaoEstoqueRepository
                .resumirMensalParaDashboard(
                        idEstabelecimento,
                        dataInicio.atStartOfDay(),
                        dataFim.plusDays(1).atStartOfDay(),
                        TIPOS_ENTRADA,
                        TIPOS_SAIDA
                );

        return new MovimentacoesDashboardResponseDto(
                serieMensal(resumoMensal, dataInicio, meses),
                recentes.stream()
                        .map(movimentacao -> paraMovimentacaoRecente(movimentacao, nomesUsuarios))
                        .toList()
        );
    }

    private List<MovimentacaoMensalDashboardResponseDto> serieMensal(
            List<ResumoMovimentacaoMensalDashboardProjection> resumoMensal,
            LocalDate dataInicio,
            int meses
    ) {
        Map<YearMonth, ResumoMovimentacaoMensalDashboardProjection> porMes = new HashMap<>();
        resumoMensal.forEach(item -> porMes.put(YearMonth.of(item.getAno(), item.getMes()), item));

        YearMonth primeiroMes = YearMonth.from(dataInicio);
        return java.util.stream.IntStream.range(0, meses)
                .mapToObj(primeiroMes::plusMonths)
                .map(mes -> {
                    ResumoMovimentacaoMensalDashboardProjection item = porMes.get(mes);
                    return new MovimentacaoMensalDashboardResponseDto(
                            mes.toString(),
                            item == null ? 0 : item.getEntradas(),
                            item == null ? 0 : item.getSaidas()
                    );
                })
                .toList();
    }

    private AlertaEstoqueDashboardResponseDto paraAlerta(EstoqueEntity estoque) {
        BigDecimal quantidadeAtual = estoque.getQuantidadeAtual();
        return new AlertaEstoqueDashboardResponseDto(
                estoque.getProduto().getId(),
                estoque.getProduto().getNome(),
                estoque.getProduto().getCategoria().getNome(),
                estoque.getProduto().getUnidadeMedida(),
                quantidadeAtual,
                estoque.getEstoqueMinimo(),
                quantidadeAtual.signum() <= 0 ? StatusEstoqueDashboard.ZERADO : StatusEstoqueDashboard.CRITICO
        );
    }

    private UltimaMovimentacaoDashboardResponseDto paraMovimentacaoRecente(
            MovimentacaoEstoqueEntity movimentacao,
            Map<UUID, String> nomesUsuarios
    ) {
        return new UltimaMovimentacaoDashboardResponseDto(
                movimentacao.getId(),
                movimentacao.getOcorridoEm(),
                movimentacao.getEstoque().getProduto().getId(),
                movimentacao.getEstoque().getProduto().getNome(),
                movimentacao.getEstoque().getProduto().getUnidadeMedida(),
                movimentacao.getTipo(),
                TIPOS_ENTRADA.contains(movimentacao.getTipo())
                        ? DirecaoMovimentacaoDashboard.ENTRADA
                        : DirecaoMovimentacaoDashboard.SAIDA,
                movimentacao.getQuantidade(),
                nomesUsuarios.getOrDefault(movimentacao.getUsuarioId(), "Usuário não encontrado"),
                movimentacao.getMotivo()
        );
    }

    private Map<UUID, String> nomesUsuarios(List<MovimentacaoEstoqueEntity> movimentacoes) {
        Set<UUID> idsUsuarios = movimentacoes.stream()
                .map(MovimentacaoEstoqueEntity::getUsuarioId)
                .collect(Collectors.toSet());
        if (idsUsuarios.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(idsUsuarios).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    private ModuloDashboardResponseDto moduloIndisponivel() {
        return new ModuloDashboardResponseDto(DisponibilidadeModuloDashboard.INDISPONIVEL);
    }

    private UUID obterIdEstabelecimentoAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }

        try {
            String idEstabelecimento = token.getToken().getClaimAsString("establishmentId");
            if (idEstabelecimento == null || idEstabelecimento.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }
            return UUID.fromString(idEstabelecimento);
        } catch (IllegalArgumentException excecao) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
