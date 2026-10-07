package valhalla.core.stock.app.modules.estoque.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private MovimentacaoEstoqueService movimentacaoEstoqueService;
    @Mock private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @InjectMocks private ProdutoService produtoService;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criaProdutoPadraoComSkuGeradoEEstoqueInicial() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        autenticarNoEstabelecimento(estabelecimentoId);
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(produtoRepository.existsByEstabelecimentoIdAndSku(any(), anyString())).thenReturn(false);
        when(produtoRepository.saveAndFlush(any(ProdutoEntity.class))).thenAnswer(invocation -> {
            ProdutoEntity produto = invocation.getArgument(0);
            produto.setId(UUID.randomUUID());
            return produto;
        });
        when(estoqueRepository.saveAndFlush(any(EstoqueEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProdutoResponseDto resposta = produtoService.criarProduto(new ProdutoRequestDto(
                " Vodka Absolut 1L ", 1, "garrafa", new BigDecimal("129.90"),
                new BigDecimal("89.90"), ProdutoTipo.PADRAO, " 7891234567890 ", null,
                true, new BigDecimal("1000"), new BigDecimal("6"), new BigDecimal("12")
        ));

        ArgumentCaptor<ProdutoEntity> produtoCaptor = ArgumentCaptor.forClass(ProdutoEntity.class);
        ArgumentCaptor<EstoqueEntity> estoqueCaptor = ArgumentCaptor.forClass(EstoqueEntity.class);
        verify(produtoRepository).saveAndFlush(produtoCaptor.capture());
        verify(estoqueRepository).saveAndFlush(estoqueCaptor.capture());

        assertThat(produtoCaptor.getValue().getSku()).matches("PRD-[A-F0-9]{8}");
        assertThat(produtoCaptor.getValue().getNome()).isEqualTo("Vodka Absolut 1L");
        assertThat(estoqueCaptor.getValue().getQuantidadeAtual()).isEqualByComparingTo("12");
        assertThat(estoqueCaptor.getValue().getEstoqueMinimo()).isEqualByComparingTo("6");
        assertThat(resposta.precoCusto()).isEqualByComparingTo("89.90");
    }

    @Test
    void criaProdutoComCustoZeroQuandoNaoInformado() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        autenticarNoEstabelecimento(estabelecimentoId);
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(produtoRepository.existsByEstabelecimentoIdAndSku(any(), anyString())).thenReturn(false);
        when(produtoRepository.saveAndFlush(any(ProdutoEntity.class))).thenAnswer(invocation -> {
            ProdutoEntity produto = invocation.getArgument(0);
            produto.setId(UUID.randomUUID());
            return produto;
        });
        when(estoqueRepository.saveAndFlush(any(EstoqueEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        produtoService.criarProduto(new ProdutoRequestDto(
                "Água", 1, "un", new BigDecimal("5.00"), null, null,
                null, null, null, null, null, null
        ));

        ArgumentCaptor<ProdutoEntity> produtoCaptor = ArgumentCaptor.forClass(ProdutoEntity.class);
        verify(produtoRepository).saveAndFlush(produtoCaptor.capture());
        assertThat(produtoCaptor.getValue().getPrecoCusto()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(produtoCaptor.getValue().getTipo()).isEqualTo(ProdutoTipo.PADRAO);
    }

    @Test
    void impedeCriarDrinkComSaldoOuEstoqueMinimoProprio() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticarNoEstabelecimento(estabelecimentoId);

        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Gin tônica", 1, "copo", new BigDecimal("30"), BigDecimal.ZERO,
                ProdutoTipo.DRINK, null, null, null, null,
                new BigDecimal("3"), null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui estoque próprio; informe esses dados nos produtos ingredientes");
    }

    @Test
    void exigeVolumeAoCriarProdutoFracionado() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticarNoEstabelecimento(estabelecimentoId);

        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Vodka Absolut", 1, "garrafa", new BigDecimal("120"), BigDecimal.ZERO,
                ProdutoTipo.PADRAO, null, null, true, null, null, null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto fracionado deve informar o volume da embalagem em mililitros");
    }

    @Test
    void impedeAlterarFracionamentoQuandoProdutoJaPossuiEstoque() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        ProdutoEntity produto = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Vodka Absolut", ProdutoTipo.PADRAO, true);
        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoId(produto.getId())).thenReturn(Optional.of(new EstoqueEntity()));

        assertThatThrownBy(() -> produtoService.atualizarProduto(produto.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, true, new BigDecimal("1000"), null, null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não é permitido alterar fracionamento ou volume de produto que já possui estoque ou movimentações");
    }

    @Test
    void filtraProdutosNoBackendPorBuscaCategoriaTipoEAtivo() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity bebidas = categoria(estabelecimentoId, true);
        bebidas.setId(1);
        CategoriaEntity alimentos = categoria(estabelecimentoId, true);
        alimentos.setId(2);
        ProdutoEntity vodka = produto(estabelecimentoId, bebidas, "PRD-AAAA0001", "Vodka Absolut", ProdutoTipo.PADRAO, true);
        vodka.setCodigoBarras("789123");
        ProdutoEntity drink = produto(estabelecimentoId, bebidas, "PRD-BBBB0002", "Moscow Mule", ProdutoTipo.DRINK, true);
        ProdutoEntity inativo = produto(estabelecimentoId, alimentos, "PRD-CCCC0003", "Amendoim", ProdutoTipo.PADRAO, false);
        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(vodka), PageRequest.of(1, 5), 6));
        when(estoqueRepository.findAllByProdutoIdIn(anyCollection())).thenReturn(List.of());

        RespostaPaginadaDto<ProdutoResponseDto> resultado = produtoService.listarProdutos(
                "absolut", 1, ProdutoTipo.PADRAO, true,
                1, 5, "nome", DirecaoOrdenacao.ASC
        );

        ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(produtoRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("nome").isAscending()).isTrue();
        assertThat(resultado.itens()).extracting(ProdutoResponseDto::sku).containsExactly("PRD-AAAA0001");
        assertThat(resultado.totalItens()).isEqualTo(6);
        assertThat(resultado.totalPaginas()).isEqualTo(2);
    }

    private CategoriaEntity categoria(UUID estabelecimentoId, boolean ativa) {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setId(1);
        categoria.setEstabelecimentoId(estabelecimentoId);
        categoria.setNome("Bebidas");
        categoria.setAtivo(ativa);
        return categoria;
    }

    private ProdutoEntity produto(
            UUID estabelecimentoId,
            CategoriaEntity categoria,
            String sku,
            String nome,
            ProdutoTipo tipo,
            boolean ativo
    ) {
        ProdutoEntity produto = new ProdutoEntity();
        produto.setId(UUID.randomUUID());
        produto.setEstabelecimentoId(estabelecimentoId);
        produto.setCategoria(categoria);
        produto.setSku(sku);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setUnidadeMedida("un");
        produto.setPrecoCusto(BigDecimal.ONE);
        produto.setPrecoVenda(BigDecimal.TEN);
        produto.setAtivo(ativo);
        return produto;
    }

    private void autenticarNoEstabelecimento(UUID idEstabelecimento) {
        Jwt token = Jwt.withTokenValue("token-de-teste")
                .header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }
}
