package valhalla.core.stock.app.modules.estoque.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.estoque.dto.LoteResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.ResumoValidadeLotesResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.SituacaoValidadeLote;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoteEstoqueServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 20);

    @Mock private LoteEstoqueRepository loteEstoqueRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private ControleLotesEstoque controleLotes;

    @InjectMocks
    private LoteEstoqueService loteEstoqueService;

    @BeforeEach
    void configurarHoje() {
        lenient().when(controleLotes.hoje()).thenReturn(HOJE);
    }

    @AfterEach
    void limparSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listarLotesReconciliaDivergentesEEvaluaTodosOsFiltrosESituacoes() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        EstoqueEntity estoqueReconciliar = new EstoqueEntity();
        when(loteEstoqueRepository.idsProdutosComLotesDivergentes(estabelecimentoId))
                .thenReturn(List.of(produtoId));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(anyList()))
                .thenReturn(List.of(estoqueReconciliar));

        ProdutoEntity produtoFracionado = new ProdutoEntity();
        produtoFracionado.setId(produtoId);
        produtoFracionado.setNome("Vodka 1L");
        produtoFracionado.setSku("PRD-VODKA");
        produtoFracionado.setUnidadeMedida("garrafa");
        produtoFracionado.setFracionado(true);
        produtoFracionado.setVolumeEmbalagemMl(new BigDecimal("1000"));
        produtoFracionado.setPrecoCusto(new BigDecimal("100.00"));

        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setProduto(produtoFracionado);

        LoteEstoqueEntity lote = new LoteEstoqueEntity();
        lote.setId(1L);
        lote.setEstoque(estoque);
        lote.setCodigo("L-01");
        lote.setValidade(HOJE.plusDays(5));
        lote.setQuantidadeAtual(new BigDecimal("500"));

        when(loteEstoqueRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lote), PageRequest.of(0, 20), 1));

        for (SituacaoValidadeLote situacao : SituacaoValidadeLote.values()) {
            RespostaPaginadaDto<LoteResponseDto> pagina = loteEstoqueService.listarLotes(
                    produtoId, situacao, 30, false, 0, 20, "validade", DirecaoOrdenacao.ASC
            );
            assertThat(pagina.itens()).hasSize(1);
            assertThat(pagina.itens().getFirst().unidadeEstoque()).isEqualTo("ML");
            assertThat(pagina.itens().getFirst().custoUnitario()).isEqualByComparingTo("0.100000");
            assertThat(pagina.itens().getFirst().valorCusto()).isEqualByComparingTo("50.00");
        }

        verify(controleLotes, org.mockito.Mockito.atLeastOnce()).reconciliar(estoqueReconciliar);
        verify(loteEstoqueRepository, org.mockito.Mockito.atLeastOnce()).flush();

        ArgumentCaptor<Specification<LoteEstoqueEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(loteEstoqueRepository, org.mockito.Mockito.atLeastOnce())
                .findAll(specCaptor.capture(), any(Pageable.class));

        Predicate predicate = mock(Predicate.class);
        Path path = mock(Path.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? inv.getMock() : null);
        Join join = mock(Join.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Join.class.isAssignableFrom(rt)) return inv.getMock();
            if (Path.class.isAssignableFrom(rt)) return path;
            return null;
        });
        Root<LoteEstoqueEntity> root = mock(Root.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Join.class.isAssignableFrom(rt)) return join;
            if (Path.class.isAssignableFrom(rt)) return path;
            return null;
        });
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, inv ->
                Predicate.class.isAssignableFrom(inv.getMethod().getReturnType()) ? predicate : null);

        for (Specification<LoteEstoqueEntity> spec : specCaptor.getAllValues()) {
            assertThat(spec.toPredicate(root, query, builder)).isNotNull();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void resumirAgrupaTodosOsTiposDeValidadeECalculaValoresEmRisco() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);
        when(loteEstoqueRepository.idsProdutosComLotesDivergentes(estabelecimentoId)).thenReturn(List.of());

        ProdutoEntity produtoPadrao = new ProdutoEntity();
        produtoPadrao.setPrecoCusto(new BigDecimal("10.00"));
        EstoqueEntity estoquePadrao = new EstoqueEntity();
        estoquePadrao.setProduto(produtoPadrao);

        ProdutoEntity produtoSemCusto = new ProdutoEntity();
        produtoSemCusto.setPrecoCusto(null);
        EstoqueEntity estoqueSemCusto = new EstoqueEntity();
        estoqueSemCusto.setProduto(produtoSemCusto);

        LoteEstoqueEntity vencido = criarLote(estoquePadrao, HOJE.minusDays(1), "2");
        LoteEstoqueEntity vence7 = criarLote(estoquePadrao, HOJE.plusDays(3), "3");
        LoteEstoqueEntity vence30 = criarLote(estoquePadrao, HOJE.plusDays(15), "4");
        LoteEstoqueEntity semValidade = criarLote(estoqueSemCusto, null, "5");
        LoteEstoqueEntity valido = criarLote(estoquePadrao, HOJE.plusDays(60), "10");

        when(loteEstoqueRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(vencido, vence7, vence30, semValidade, valido));

        ResumoValidadeLotesResponseDto resumo = loteEstoqueService.resumir();

        assertThat(resumo.lotesComSaldo()).isEqualTo(5);
        assertThat(resumo.vencidos()).isEqualTo(1);
        assertThat(resumo.vencemEm7Dias()).isEqualTo(1);
        assertThat(resumo.vencemEm30Dias()).isEqualTo(1);
        assertThat(resumo.semValidade()).isEqualTo(1);
        assertThat(resumo.valorVencido()).isEqualByComparingTo("20.00");
        assertThat(resumo.valorEmRisco()).isEqualByComparingTo("90.00");

        ArgumentCaptor<Specification<LoteEstoqueEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(loteEstoqueRepository).findAll(specCaptor.capture());

        Predicate predicate = mock(Predicate.class);
        Path path = mock(Path.class, inv -> Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? inv.getMock() : null);
        Join join = mock(Join.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Join.class.isAssignableFrom(rt)) return inv.getMock();
            if (Path.class.isAssignableFrom(rt)) return path;
            return null;
        });
        Root<LoteEstoqueEntity> root = mock(Root.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Join.class.isAssignableFrom(rt)) return join;
            if (Path.class.isAssignableFrom(rt)) return path;
            return null;
        });
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, inv ->
                Predicate.class.isAssignableFrom(inv.getMethod().getReturnType()) ? predicate : null);

        assertThat(specCaptor.getValue().toPredicate(root, query, builder)).isNotNull();
    }

    @Test
    void rejeitaVenceEmDiasNegativoESessaoInvalida() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        assertThatThrownBy(() -> loteEstoqueService.listarLotes(
                null, null, -1, true, 0, 20, "validade", DirecaoOrdenacao.ASC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("venceEmDias não pode ser negativo");

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> loteEstoqueService.resumir())
                .isInstanceOf(AccessDeniedException.class);

        Jwt semEstabelecimento = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(semEstabelecimento));
        assertThatThrownBy(() -> loteEstoqueService.resumir())
                .isInstanceOf(AccessDeniedException.class);

        Jwt estabelecimentoInvalido = Jwt.withTokenValue("t").header("alg", "none").claim("establishmentId", "nao-uuid").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(estabelecimentoInvalido));
        assertThatThrownBy(() -> loteEstoqueService.resumir())
                .isInstanceOf(AccessDeniedException.class);
    }

    private LoteEstoqueEntity criarLote(EstoqueEntity estoque, LocalDate validade, String qtd) {
        LoteEstoqueEntity lote = new LoteEstoqueEntity();
        lote.setEstoque(estoque);
        lote.setValidade(validade);
        lote.setQuantidadeAtual(new BigDecimal(qtd));
        return lote;
    }

    private void autenticar(UUID estabelecimentoId) {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("establishmentId", estabelecimentoId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }
}
