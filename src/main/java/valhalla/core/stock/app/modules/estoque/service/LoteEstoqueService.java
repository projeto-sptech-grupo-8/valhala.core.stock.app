package valhalla.core.stock.app.modules.estoque.service;

import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.dto.LoteResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.ResumoValidadeLotesResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.SituacaoValidadeLote;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository.ResumoValidadeLotesProjection;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Consulta de lotes e indicadores de validade. */
@Service
public class LoteEstoqueService {

    private static final Set<String> CAMPOS_ORDENACAO = Set.of("validade", "codigo", "quantidadeAtual", "criadoEm");

    private final LoteEstoqueRepository loteEstoqueRepository;
    private final ControleLotesEstoque controleLotes;

    public LoteEstoqueService(
            LoteEstoqueRepository loteEstoqueRepository,
            ControleLotesEstoque controleLotes
    ) {
        this.loteEstoqueRepository = loteEstoqueRepository;
        this.controleLotes = controleLotes;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_ESTOQUE', authentication)")
    public RespostaPaginadaDto<LoteResponseDto> listarLotes(
            UUID produtoId,
            SituacaoValidadeLote situacao,
            Integer venceEmDias,
            boolean incluirZerados,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        if (venceEmDias != null && venceEmDias < 0) {
            throw new IllegalArgumentException("venceEmDias não pode ser negativo");
        }
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        LocalDate hoje = controleLotes.hoje();

        Specification<LoteEstoqueEntity> especificacao = doEstabelecimento(idEstabelecimento);
        if (!incluirZerados) {
            especificacao = especificacao.and((root, query, builder) ->
                    builder.greaterThan(root.get("quantidadeAtual"), BigDecimal.ZERO));
        }
        if (produtoId != null) {
            especificacao = especificacao.and((root, query, builder) ->
                    builder.equal(root.get("estoque").get("produto").get("id"), produtoId));
        }
        if (situacao != null) {
            especificacao = especificacao.and(situacao(situacao, hoje));
        }
        if (venceEmDias != null) {
            LocalDate limite = hoje.plusDays(venceEmDias);
            especificacao = especificacao.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("validade"), limite));
        }

        Page<LoteEstoqueEntity> resultado = loteEstoqueRepository.findAll(
                especificacao, Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO));
        return RespostaPaginadaDto.de(resultado, lote -> paraResposta(lote, hoje));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_ESTOQUE', authentication)")
    public ResumoValidadeLotesResponseDto resumir() {
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        LocalDate hoje = controleLotes.hoje();
        ResumoValidadeLotesProjection resumo = loteEstoqueRepository.resumirValidade(
                idEstabelecimento, hoje, hoje.plusDays(7), hoje.plusDays(30)
        );
        if (resumo == null) {
            return new ResumoValidadeLotesResponseDto(
                    hoje, 0L, 0L, 0L, 0L, 0L,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            );
        }
        return new ResumoValidadeLotesResponseDto(
                hoje,
                zeroSeNulo(resumo.getLotesComSaldo()),
                zeroSeNulo(resumo.getVencidos()),
                zeroSeNulo(resumo.getVencemEm7Dias()),
                zeroSeNulo(resumo.getVencemEm30Dias()),
                zeroSeNulo(resumo.getSemValidade()),
                zeroSeNulo(resumo.getValorVencido()).setScale(2, RoundingMode.HALF_UP),
                zeroSeNulo(resumo.getValorEmRisco()).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private static long zeroSeNulo(Long valor) {
        return valor == null ? 0L : valor;
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private Specification<LoteEstoqueEntity> doEstabelecimento(UUID idEstabelecimento) {
        return (root, query, builder) -> {
            Join<LoteEstoqueEntity, EstoqueEntity> estoque = root.join("estoque");
            Join<EstoqueEntity, ProdutoEntity> produto = estoque.join("produto");
            return builder.equal(produto.get("estabelecimentoId"), idEstabelecimento);
        };
    }

    private Specification<LoteEstoqueEntity> situacao(SituacaoValidadeLote situacao, LocalDate hoje) {
        return (root, query, builder) -> {
            var validade = root.<LocalDate>get("validade");
            return switch (situacao) {
                case SEM_VALIDADE -> builder.isNull(validade);
                case VENCIDO -> builder.lessThan(validade, hoje);
                case VENCE_EM_7_DIAS -> builder.between(validade, hoje, hoje.plusDays(7));
                case VENCE_EM_30_DIAS -> builder.between(validade, hoje.plusDays(8), hoje.plusDays(30));
                case VALIDO -> builder.greaterThan(validade, hoje.plusDays(30));
            };
        };
    }

    private LoteResponseDto paraResposta(LoteEstoqueEntity lote, LocalDate hoje) {
        ProdutoEntity produto = lote.getEstoque().getProduto();
        return new LoteResponseDto(
                lote.getId(), produto.getId(), produto.getNome(), produto.getSku(),
                produto.isFracionado() ? "ML" : produto.getUnidadeMedida(),
                lote.getCodigo(), lote.getValidade(),
                lote.getValidade() == null ? null : ChronoUnit.DAYS.between(hoje, lote.getValidade()),
                SituacaoValidadeLote.de(lote.getValidade(), hoje),
                lote.getQuantidadeAtual(), custoUnitario(produto),
                valorCusto(lote).setScale(2, RoundingMode.HALF_UP), lote.getCriadoEm()
        );
    }

    private BigDecimal valorCusto(LoteEstoqueEntity lote) {
        return lote.getQuantidadeAtual().multiply(custoUnitario(lote.getEstoque().getProduto()));
    }

    // estoque de fracionado é em ML: custo da embalagem ÷ volume
    private BigDecimal custoUnitario(ProdutoEntity produto) {
        BigDecimal custo = produto.getPrecoCusto() == null ? BigDecimal.ZERO : produto.getPrecoCusto();
        if (produto.isFracionado() && produto.getVolumeEmbalagemMl() != null && produto.getVolumeEmbalagemMl().signum() > 0) {
            return custo.divide(produto.getVolumeEmbalagemMl(), 6, RoundingMode.HALF_UP);
        }
        return custo;
    }

    private UUID obterIdEstabelecimentoAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            String valor = token.getToken().getClaimAsString("establishmentId");
            if (valor == null || valor.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }
            return UUID.fromString(valor);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
