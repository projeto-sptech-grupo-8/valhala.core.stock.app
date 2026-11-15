package valhalla.core.stock.app.modules.estoque.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.persistence.EntityNotFoundException;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.exception.ProdutoPossuiMovimentacoesException;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Mock private BusinessEventLogger businessEventLogger;
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
                new BigDecimal("89.90"), ProdutoTipo.PADRAO,
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
                null, null, null, null
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
                ProdutoTipo.DRINK, null, null,
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
                ProdutoTipo.PADRAO, true, null, null, null
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
                null, null, null, null, null, true, new BigDecimal("1000"), null, null
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

    @Test
    void deveBuscarProdutoPorIdComSucesso() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        ProdutoEntity produto = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Vodka Absolut", ProdutoTipo.PADRAO, true);
        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(new BigDecimal("10"));

        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoId(produto.getId())).thenReturn(Optional.of(estoque));

        ProdutoResponseDto resposta = produtoService.buscarProduto(produto.getId());

        assertThat(resposta.id()).isEqualTo(produto.getId());
        assertThat(resposta.nome()).isEqualTo("Vodka Absolut");
        assertThat(resposta.quantidadeEstoque()).isEqualByComparingTo("10");
    }

    @Test
    void deveNegarAcessoAoBuscarProdutoDeOutroEstabelecimento() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID outroEstabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(outroEstabelecimentoId, true);
        ProdutoEntity produto = produto(outroEstabelecimentoId, categoria, "PRD-AAAA0001", "Vodka Absolut", ProdutoTipo.PADRAO, true);

        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));

        assertThatThrownBy(() -> produtoService.buscarProduto(produto.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deveLancarEntityNotFoundExceptionQuandoProdutoNaoExiste() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();

        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.buscarProduto(produtoId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deveExcluirProdutoComSucesso() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        ProdutoEntity produto = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Vodka Absolut", ProdutoTipo.PADRAO, true);

        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));

        produtoService.excluirProduto(produto.getId());

        verify(estoqueRepository).deleteByProdutoId(produto.getId());
        verify(produtoRepository).delete(produto);
        verify(produtoRepository).flush();
    }

    @Test
    void deveLancarProdutoPossuiMovimentacoesExceptionAoExcluirComVinculos() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        ProdutoEntity produto = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Vodka Absolut", ProdutoTipo.PADRAO, true);

        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("FK constraint"))
                .when(produtoRepository).flush();

        assertThatThrownBy(() -> produtoService.excluirProduto(produto.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class)
                .hasMessage("Produto não pode ser excluído porque possui movimentações ou vínculos registrados");
    }

    @Test
    void criaProdutoComFardoEConverteQuantidadeInicial() {
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
                "Brahma Lata 350ml", 1, "un", new BigDecimal("5.50"),
                new BigDecimal("3.00"), ProdutoTipo.PADRAO,
                false, null, new BigDecimal("12"), new BigDecimal("2"),
                true, 12, true
        ));

        ArgumentCaptor<EstoqueEntity> estoqueCaptor = ArgumentCaptor.forClass(EstoqueEntity.class);
        verify(estoqueRepository).saveAndFlush(estoqueCaptor.capture());

        assertThat(estoqueCaptor.getValue().getQuantidadeAtual()).isEqualByComparingTo("24");
        assertThat(resposta.fardo()).isTrue();
        assertThat(resposta.unidadesPorFardo()).isEqualTo(12);
    }

    @Test
    void criaProdutoPadraoComLoteEValidadeERetornaNaResposta() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        LocalDate validade = LocalDate.of(2027, 6, 30);
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
                "Heineken 600ml", 1, "un", new BigDecimal("14.00"),
                new BigDecimal("8.50"), ProdutoTipo.PADRAO,
                false, null, new BigDecimal("6"), new BigDecimal("24"),
                false, null, false, " L2611A ", validade
        ));

        ArgumentCaptor<EstoqueEntity> estoqueCaptor = ArgumentCaptor.forClass(EstoqueEntity.class);
        verify(estoqueRepository).saveAndFlush(estoqueCaptor.capture());
        verify(movimentacaoEstoqueService).registrarEntradaInicial(
                estoqueCaptor.getValue(), new BigDecimal("24"), "L2611A", validade
        );
        assertThat(resposta.lote()).isEqualTo("L2611A");
        assertThat(resposta.validade()).isEqualTo(validade);
    }

    @Test
    void impedeCriarDrinkComLoteOuValidade() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticarNoEstabelecimento(estabelecimentoId);

        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Gin Tônica", 1, "copo", new BigDecimal("28.00"), BigDecimal.ZERO,
                ProdutoTipo.DRINK, false, null, null, null,
                false, null, false, "LOTE-DRINK", LocalDate.of(2027, 1, 1)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui estoque próprio; informe esses dados nos produtos ingredientes");
    }

    @Test
    void rejeitaConfiguracaoInvalidaDeFardoNaCriacaoEAtualizacao() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        autenticarNoEstabelecimento(estabelecimentoId);

        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Cerveja", 1, "un", new BigDecimal("5.00"), null, ProdutoTipo.PADRAO,
                false, null, null, null, true, null, false
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Um fardo deve possuir ao menos 2 unidades");

        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Cerveja", 1, "un", new BigDecimal("5.00"), null, ProdutoTipo.PADRAO,
                false, null, null, null, false, 12, false
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unidades por fardo só pode ser informado quando fardo estiver ativo");

        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Gin Tônica", 1, "copo", new BigDecimal("25.00"), null, ProdutoTipo.DRINK,
                false, null, null, null, true, 6, false
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui estoque próprio; informe esses dados nos produtos ingredientes");

        ProdutoEntity produto = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Cerveja", ProdutoTipo.PADRAO, true);
        produto.setFardo(true);
        produto.setUnidadesPorFardo(12);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(produtoRepository.saveAndFlush(any(ProdutoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(estoqueRepository.findByProdutoId(produto.getId())).thenReturn(Optional.empty());

        ProdutoResponseDto atualizado = produtoService.atualizarProduto(produto.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, false, null, null
        ));
        assertThat(atualizado.fardo()).isFalse();
        assertThat(atualizado.unidadesPorFardo()).isNull();
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
