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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import valhalla.core.stock.app.modules.caixa.fiscal.repository.ItemNotaFiscalRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ReservaEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.exception.ProdutoPossuiMovimentacoesException;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private MovimentacaoEstoqueService movimentacaoEstoqueService;
    @Mock private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @Mock private ComposicaoDrinkRepository composicaoDrinkRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private ItemNotaFiscalRepository itemNotaFiscalRepository;
    @Mock private ReservaEstoqueRepository reservaEstoqueRepository;
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
    void deveInativarProdutoLogicamenteQuandoNaoPossuiHistoricoOuSaldo() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        ProdutoEntity produto = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Vodka Absolut", ProdutoTipo.PADRAO, true);
        EstoqueEntity estoqueZerado = new EstoqueEntity();
        estoqueZerado.setProduto(produto);
        estoqueZerado.setQuantidadeAtual(BigDecimal.ZERO);
        estoqueZerado.setQuantidadeReservada(BigDecimal.ZERO);

        autenticarNoEstabelecimento(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoId(produto.getId())).thenReturn(Optional.of(estoqueZerado));

        produtoService.excluirProduto(produto.getId());

        assertThat(produto.isAtivo()).isFalse();
        verify(produtoRepository).saveAndFlush(produto);
        verify(estoqueRepository, never()).deleteByProdutoId(any());
        verify(produtoRepository, never()).delete(any(ProdutoEntity.class));

        // Idempotente quando já está inativo
        produtoService.excluirProduto(produto.getId());
        verify(produtoRepository, org.mockito.Mockito.times(1)).saveAndFlush(produto);
    }

    @Test
    void deveLancarProdutoPossuiMovimentacoesExceptionAoInativarComHistoricoOuVinculos() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        autenticarNoEstabelecimento(estabelecimentoId);

        ProdutoEntity comMovimentacao = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Vodka", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(comMovimentacao.getId())).thenReturn(Optional.of(comMovimentacao));
        when(movimentacaoEstoqueRepository.existsByEstoqueProdutoId(comMovimentacao.getId())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.excluirProduto(comMovimentacao.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class)
                .hasMessage("Produto não pode ser inativado porque possui movimentações de estoque registradas");

        ProdutoEntity drinkComSaida = produto(estabelecimentoId, categoria, "PRD-DRK0001", "Gin Tônica", ProdutoTipo.DRINK, true);
        when(produtoRepository.findById(drinkComSaida.getId())).thenReturn(Optional.of(drinkComSaida));
        when(movimentacaoEstoqueRepository.existsByOrigemDrinkId(drinkComSaida.getId())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.excluirProduto(drinkComSaida.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class);

        ProdutoEntity emPedido = produto(estabelecimentoId, categoria, "PRD-PED0001", "Cerveja", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(emPedido.getId())).thenReturn(Optional.of(emPedido));
        when(itemPedidoRepository.existsByProdutoId(emPedido.getId())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.excluirProduto(emPedido.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class)
                .hasMessage("Produto não pode ser inativado porque possui histórico de pedidos, notas fiscais ou reservas");

        ProdutoEntity emNota = produto(estabelecimentoId, categoria, "PRD-NF0001", "Vinho", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(emNota.getId())).thenReturn(Optional.of(emNota));
        when(itemNotaFiscalRepository.existsByProdutoId(emNota.getId())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.excluirProduto(emNota.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class);

        ProdutoEntity emReserva = produto(estabelecimentoId, categoria, "PRD-RES0001", "Gin", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(emReserva.getId())).thenReturn(Optional.of(emReserva));
        when(reservaEstoqueRepository.existsByProdutoIdOrOrigemDrinkId(emReserva.getId(), emReserva.getId())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.excluirProduto(emReserva.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class);

        ProdutoEntity ingrediente = produto(estabelecimentoId, categoria, "PRD-ING0001", "Água Tônica", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(ingrediente.getId())).thenReturn(Optional.of(ingrediente));
        when(composicaoDrinkRepository.existsByProdutoId(ingrediente.getId())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.excluirProduto(ingrediente.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class)
                .hasMessage("Produto não pode ser inativado porque é utilizado como ingrediente em receita de drink");

        ProdutoEntity comSaldo = produto(estabelecimentoId, categoria, "PRD-SLD0001", "Red Bull", ProdutoTipo.PADRAO, true);
        EstoqueEntity estoquePositivo = new EstoqueEntity();
        estoquePositivo.setProduto(comSaldo);
        estoquePositivo.setQuantidadeAtual(BigDecimal.ONE);
        estoquePositivo.setQuantidadeReservada(BigDecimal.ZERO);
        when(produtoRepository.findById(comSaldo.getId())).thenReturn(Optional.of(comSaldo));
        when(estoqueRepository.findByProdutoId(comSaldo.getId())).thenReturn(Optional.of(estoquePositivo));
        assertThatThrownBy(() -> produtoService.excluirProduto(comSaldo.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class)
                .hasMessage("Produto não pode ser inativado porque possui saldo em estoque");

        ProdutoEntity comReservaEstoque = produto(estabelecimentoId, categoria, "PRD-RSV0001", "Suco", ProdutoTipo.PADRAO, true);
        EstoqueEntity estoqueComReserva = new EstoqueEntity();
        estoqueComReserva.setProduto(comReservaEstoque);
        estoqueComReserva.setQuantidadeAtual(BigDecimal.ZERO);
        estoqueComReserva.setQuantidadeReservada(BigDecimal.ONE);
        when(produtoRepository.findById(comReservaEstoque.getId())).thenReturn(Optional.of(comReservaEstoque));
        when(estoqueRepository.findByProdutoId(comReservaEstoque.getId())).thenReturn(Optional.of(estoqueComReserva));
        assertThatThrownBy(() -> produtoService.excluirProduto(comReservaEstoque.getId()))
                .isInstanceOf(ProdutoPossuiMovimentacoesException.class);
    }

    @Test
    void validaInativacaoEReativacaoViaAtualizacaoDeProduto() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        autenticarNoEstabelecimento(estabelecimentoId);

        ProdutoEntity produto = produto(estabelecimentoId, categoria, "PRD-AT0001", "Whisky", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(movimentacaoEstoqueRepository.existsByEstoqueProdutoId(produto.getId())).thenReturn(true);

        assertThatThrownBy(() -> produtoService.atualizarProduto(produto.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, false
        ))).isInstanceOf(ProdutoPossuiMovimentacoesException.class);

        ProdutoEntity semHistorico = produto(estabelecimentoId, categoria, "PRD-AT0002", "Energético", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(semHistorico.getId())).thenReturn(Optional.of(semHistorico));
        when(estoqueRepository.findByProdutoId(semHistorico.getId())).thenReturn(Optional.of(new EstoqueEntity()));
        when(produtoRepository.saveAndFlush(any(ProdutoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProdutoResponseDto inativado = produtoService.atualizarProduto(semHistorico.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, false
        ));
        assertThat(inativado.ativo()).isFalse();

        ProdutoResponseDto reativado = produtoService.atualizarProduto(semHistorico.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, true
        ));
        assertThat(reativado.ativo()).isTrue();
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void avaliaEspecificacaoDeProdutosEExportacao() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticarNoEstabelecimento(estabelecimentoId);
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        ProdutoEntity vodka = produto(estabelecimentoId, categoria, "PRD-AAAA0001", "Vodka", ProdutoTipo.PADRAO, true);
        UUID idFiltro = vodka.getId();

        when(produtoRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(vodka));
        when(estoqueRepository.findAllByProdutoIdIn(anyCollection())).thenReturn(List.of());

        List<ProdutoResponseDto> exportados = produtoService.listarProdutosParaExportacao(
                "  VODKA  ", List.of(1), ProdutoTipo.PADRAO, true, List.of(idFiltro), "nome", DirecaoOrdenacao.DESC
        );
        assertThat(exportados).hasSize(1);

        ArgumentCaptor<Specification<ProdutoEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(produtoRepository).findAll(specCaptor.capture(), any(org.springframework.data.domain.Sort.class));

        Predicate predicate = org.mockito.Mockito.mock(Predicate.class);
        Expression expr = org.mockito.Mockito.mock(Expression.class);
        Path path = org.mockito.Mockito.mock(Path.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Path.class.isAssignableFrom(rt)) return inv.getMock();
            if (Predicate.class.isAssignableFrom(rt)) return predicate;
            return null;
        });
        Root<ProdutoEntity> root = org.mockito.Mockito.mock(Root.class, inv ->
                Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? path : null);
        CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder builder = org.mockito.Mockito.mock(CriteriaBuilder.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Predicate.class.isAssignableFrom(rt)) return predicate;
            if (Expression.class.isAssignableFrom(rt)) return expr;
            return null;
        });

        assertThat(specCaptor.getValue().toPredicate(root, query, builder)).isNotNull();
    }

    @Test
    void validaRegrasDeAlteracaoDeFracionamentoFardoVolumeEEstoqueMinimo() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity categoria = categoria(estabelecimentoId, true);
        autenticarNoEstabelecimento(estabelecimentoId);

        ProdutoEntity drink = produto(estabelecimentoId, categoria, "PRD-DRINK01", "Gin Tônica", ProdutoTipo.DRINK, true);
        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));

        assertThatThrownBy(() -> produtoService.atualizarProduto(drink.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, true, null, null, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui características de fracionamento próprias");

        assertThatThrownBy(() -> produtoService.atualizarProduto(drink.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, true, 6, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui configuração de fardo");

        assertThatThrownBy(() -> produtoService.atualizarProduto(drink.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, new BigDecimal("750"), null, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui características de fracionamento próprias");

        assertThatThrownBy(() -> produtoService.atualizarProduto(drink.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, new BigDecimal("5"), null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui estoque mínimo próprio");

        ProdutoEntity padraoSemEstoque = produto(estabelecimentoId, categoria, "PRD-PAD01", "Whisky", ProdutoTipo.PADRAO, true);
        when(produtoRepository.findById(padraoSemEstoque.getId())).thenReturn(Optional.of(padraoSemEstoque));
        when(estoqueRepository.findByProdutoId(padraoSemEstoque.getId())).thenReturn(Optional.empty());
        when(movimentacaoEstoqueRepository.existsByEstoqueProdutoId(padraoSemEstoque.getId())).thenReturn(false);

        assertThatThrownBy(() -> produtoService.atualizarProduto(padraoSemEstoque.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, true, null, null, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto fracionado deve informar o volume da embalagem em mililitros");

        assertThatThrownBy(() -> produtoService.atualizarProduto(padraoSemEstoque.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, false, 6, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unidades por fardo só pode ser informado quando fardo estiver ativo");

        assertThatThrownBy(() -> produtoService.atualizarProduto(padraoSemEstoque.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, null, null, true, 1, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Um fardo deve possuir ao menos 2 unidades");

        // Produto já possui movimentações mesmo sem estoque encontrado
        when(movimentacaoEstoqueRepository.existsByEstoqueProdutoId(padraoSemEstoque.getId())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.atualizarProduto(padraoSemEstoque.getId(), new ProdutoAtualizacaoRequestDto(
                null, null, null, null, null, null, new BigDecimal("1000"), null, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não é permitido alterar fracionamento ou volume de produto que já possui estoque ou movimentações");

        // Atualização de estoque mínimo e categoria válida
        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setProduto(padraoSemEstoque);
        estoque.setQuantidadeAtual(BigDecimal.TEN);
        when(estoqueRepository.findByProdutoId(padraoSemEstoque.getId())).thenReturn(Optional.of(estoque));
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(produtoRepository.saveAndFlush(any(ProdutoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProdutoResponseDto atualizado = produtoService.atualizarProduto(padraoSemEstoque.getId(), new ProdutoAtualizacaoRequestDto(
                "Whisky 12 Anos", 1, "un", new BigDecimal("150.00"), new BigDecimal("90.00"),
                false, null, new BigDecimal("4"), true, 6, true
        ));
        assertThat(atualizado.estoqueMinimo()).isEqualByComparingTo("4");
        verify(estoqueRepository).save(estoque);
    }

    @Test
    void lancaErroAoFalharGeracaoDeSkuUnicoOuCategoriaInativaEAutenticacaoInvalida() {
        UUID estabelecimentoId = UUID.randomUUID();
        CategoriaEntity inativa = categoria(estabelecimentoId, false);
        autenticarNoEstabelecimento(estabelecimentoId);

        when(categoriaRepository.findById(1)).thenReturn(Optional.of(inativa));
        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Cerveja", 1, "un", BigDecimal.TEN, BigDecimal.ONE, ProdutoTipo.PADRAO,
                false, null, null, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A categoria informada está inativa");

        CategoriaEntity ativa = categoria(estabelecimentoId, true);
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(ativa));
        when(produtoRepository.existsByEstabelecimentoIdAndSku(any(), anyString())).thenReturn(true);
        assertThatThrownBy(() -> produtoService.criarProduto(new ProdutoRequestDto(
                "Cerveja", 1, "un", BigDecimal.TEN, BigDecimal.ONE, ProdutoTipo.PADRAO,
                false, null, null, null
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Não foi possível gerar um SKU único para o produto");

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> produtoService.listarProdutos(null, (Integer) null, null, null, 0, 20, "nome", DirecaoOrdenacao.ASC))
                .isInstanceOf(AccessDeniedException.class);
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
