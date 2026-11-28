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
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository.ResumoValidadeLotesProjection;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoteEstoqueServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 20);

    @Mock private LoteEstoqueRepository loteEstoqueRepository;
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
    void listarLotesConsultaEmModoLeituraEAvaliaTodosOsFiltrosESituacoes() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

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

        verify(controleLotes, never()).reconciliar(any());
        verify(loteEstoqueRepository, never()).flush();

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
    void resumirDelegaAgregacaoAoRepositorioETrataValoresNulos() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        ResumoValidadeLotesProjection projecao = mock(ResumoValidadeLotesProjection.class);
        when(projecao.getLotesComSaldo()).thenReturn(5L);
        when(projecao.getVencidos()).thenReturn(1L);
        when(projecao.getVencemEm7Dias()).thenReturn(1L);
        when(projecao.getVencemEm30Dias()).thenReturn(1L);
        when(projecao.getSemValidade()).thenReturn(1L);
        when(projecao.getValorVencido()).thenReturn(new BigDecimal("20.004"));
        when(projecao.getValorEmRisco()).thenReturn(new BigDecimal("90.005"));

        when(loteEstoqueRepository.resumirValidade(estabelecimentoId, HOJE, HOJE.plusDays(7), HOJE.plusDays(30)))
                .thenReturn(projecao);

        ResumoValidadeLotesResponseDto resumo = loteEstoqueService.resumir();

        assertThat(resumo.referencia()).isEqualTo(HOJE);
        assertThat(resumo.lotesComSaldo()).isEqualTo(5);
        assertThat(resumo.vencidos()).isEqualTo(1);
        assertThat(resumo.vencemEm7Dias()).isEqualTo(1);
        assertThat(resumo.vencemEm30Dias()).isEqualTo(1);
        assertThat(resumo.semValidade()).isEqualTo(1);
        assertThat(resumo.valorVencido()).isEqualByComparingTo("20.00");
        assertThat(resumo.valorEmRisco()).isEqualByComparingTo("90.01");

        ResumoValidadeLotesProjection projecaoNula = mock(ResumoValidadeLotesProjection.class);
        when(loteEstoqueRepository.resumirValidade(estabelecimentoId, HOJE, HOJE.plusDays(7), HOJE.plusDays(30)))
                .thenReturn(projecaoNula);
        ResumoValidadeLotesResponseDto vazioComNulos = loteEstoqueService.resumir();
        assertThat(vazioComNulos.lotesComSaldo()).isZero();
        assertThat(vazioComNulos.valorVencido()).isEqualByComparingTo("0.00");
        assertThat(vazioComNulos.valorEmRisco()).isEqualByComparingTo("0.00");

        when(loteEstoqueRepository.resumirValidade(estabelecimentoId, HOJE, HOJE.plusDays(7), HOJE.plusDays(30)))
                .thenReturn(null);
        ResumoValidadeLotesResponseDto nulo = loteEstoqueService.resumir();
        assertThat(nulo.lotesComSaldo()).isZero();
        assertThat(nulo.valorEmRisco()).isEqualByComparingTo("0.00");
        verify(controleLotes, never()).reconciliar(any());
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

    private void autenticar(UUID estabelecimentoId) {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("establishmentId", estabelecimentoId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }
}
