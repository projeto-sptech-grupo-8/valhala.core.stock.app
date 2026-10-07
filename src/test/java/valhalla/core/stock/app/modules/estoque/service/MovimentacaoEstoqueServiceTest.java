package valhalla.core.stock.app.modules.estoque.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.SaidaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaAjusteNegativo;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaAjustePositivo;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaEntrada;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaEntradaInicial;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaPerda;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaSaida;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaSaidaDrink;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimentacaoEstoqueServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private ComposicaoDrinkRepository composicaoDrinkRepository;
    @Mock private MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    @Mock private UserRepository userRepository;

    private MovimentacaoEstoqueService movimentacaoEstoqueService;

    @BeforeEach
    void configurarServico() {
        movimentacaoEstoqueService = new MovimentacaoEstoqueService(
                produtoRepository,
                estoqueRepository,
                composicaoDrinkRepository,
                movimentacaoEstoqueRepository,
                userRepository,
                List.of(
                        new EstrategiaEntradaInicial(), new EstrategiaEntrada(), new EstrategiaSaida(),
                        new EstrategiaAjustePositivo(), new EstrategiaAjusteNegativo(),
                        new EstrategiaPerda(), new EstrategiaSaidaDrink()
                )
        );
        lenient().when(movimentacaoEstoqueRepository.save(any(MovimentacaoEstoqueEntity.class)))
                .thenAnswer(invocation -> {
                    MovimentacaoEstoqueEntity movimentacao = invocation.getArgument(0);
                    movimentacao.setId(1L);
                    return movimentacao;
                });
        UserEntity usuario = new UserEntity();
        usuario.setName("Operador de teste");
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(usuario));
    }

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraSaidaDiretaComSaldoAnteriorEPosterior() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        EstoqueEntity estoque = estoque(produto, "24");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoIdForUpdate(produto.getId())).thenReturn(Optional.of(estoque));

        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        produto.getId(), TipoMovimentacaoEstoque.SAIDA, new BigDecimal("2"),
                        "Venda balcão", null, null
                )
        );

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("22");
        assertThat(resposta.saldoAnterior()).isEqualByComparingTo("24");
        assertThat(resposta.saldoPosterior()).isEqualByComparingTo("22");
        assertThat(resposta.unidadeEstoque()).isEqualTo("un");
        verify(estoqueRepository).save(estoque);
    }

    @Test
    void baixaTodosOsIngredientesDeDrinkEmUmaOperacao() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Vodka Energy");
        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka Absolut");
        ProdutoEntity energetico = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        EstoqueEntity estoqueVodka = estoque(vodka, "1000");
        EstoqueEntity estoqueEnergetico = estoque(energetico, "4");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId()))
                .thenReturn(List.of(
                        composicao(drink, vodka, "50", UnidadeConsumoDrink.ML),
                        composicao(drink, energetico, "1", UnidadeConsumoDrink.UN)
                ));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any()))
                .thenReturn(List.of(estoqueEnergetico, estoqueVodka));

        List<MovimentacaoResponseDto> respostas = movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(drink.getId(), new BigDecimal("2"), "Pedido mesa 4")
        );

        assertThat(estoqueVodka.getQuantidadeAtual()).isEqualByComparingTo("900");
        assertThat(estoqueEnergetico.getQuantidadeAtual()).isEqualByComparingTo("2");
        assertThat(respostas).hasSize(2);
        assertThat(respostas).allMatch(r -> r.tipo() == TipoMovimentacaoEstoque.SAIDA_DRINK);
        String nomeDrinkSnapshot = com.jayway.jsonpath.JsonPath.read(
                (String) respostas.getFirst().receitaAplicada().rawValue(), "$.drinkNome"
        );
        assertThat(nomeDrinkSnapshot).isEqualTo("Vodka Energy");
        verify(estoqueRepository).save(estoqueVodka);
        verify(estoqueRepository).save(estoqueEnergetico);
    }

    @Test
    void naoBaixaNenhumIngredienteQuandoUmDelesNaoTemSaldoSuficiente() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Vodka Energy");
        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka Absolut");
        ProdutoEntity energetico = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        EstoqueEntity estoqueVodka = estoque(vodka, "1000");
        EstoqueEntity estoqueEnergetico = estoque(energetico, "1");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId()))
                .thenReturn(List.of(
                        composicao(drink, vodka, "50", UnidadeConsumoDrink.ML),
                        composicao(drink, energetico, "1", UnidadeConsumoDrink.UN)
                ));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any()))
                .thenReturn(List.of(estoqueVodka, estoqueEnergetico));

        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(drink.getId(), new BigDecimal("2"), null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Estoque insuficiente para realizar a movimentação");

        assertThat(estoqueVodka.getQuantidadeAtual()).isEqualByComparingTo("1000");
        assertThat(estoqueEnergetico.getQuantidadeAtual()).isEqualByComparingTo("1");
        verify(estoqueRepository, never()).save(any());
        verify(movimentacaoEstoqueRepository, never()).save(any());
    }

    @Test
    void listaMovimentacoesComPaginacaoEOrdenacaoDelegadasAoRepositorio() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        EstoqueEntity estoque = estoque(produto, "20");
        MovimentacaoEstoqueEntity movimentacao = new MovimentacaoEstoqueEntity();
        movimentacao.setId(10L);
        movimentacao.setEstoque(estoque);
        movimentacao.setUsuarioId(UUID.randomUUID());
        movimentacao.setTipo(TipoMovimentacaoEstoque.ENTRADA);
        movimentacao.setQuantidade(new BigDecimal("5"));
        movimentacao.setSaldoAnterior(new BigDecimal("15"));
        movimentacao.setSaldoPosterior(new BigDecimal("20"));
        movimentacao.setOcorridoEm(LocalDateTime.now());
        autenticar(estabelecimentoId);
        when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(movimentacao), PageRequest.of(1, 5), 11));

        var resultado = movimentacaoEstoqueService.listarMovimentacoes(
                produto.getId(), TipoMovimentacaoEstoque.ENTRADA, null,
                null, null, 1, 5, "ocorridoEm", DirecaoOrdenacao.DESC
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(movimentacaoEstoqueRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("ocorridoEm").isDescending()).isTrue();
        assertThat(resultado.totalItens()).isEqualTo(11);
        assertThat(resultado.totalPaginas()).isEqualTo(3);
        assertThat(resultado.itens()).extracting(MovimentacaoResponseDto::id).containsExactly(10L);
    }

    @Test
    void rejeitaIntervaloDeDatasInvertidoAntesDeConsultarOBanco() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        assertThatThrownBy(() -> movimentacaoEstoqueService.listarMovimentacoes(
                null, null, null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1),
                0, 20, "ocorridoEm", DirecaoOrdenacao.DESC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A data inicial não pode ser posterior à data final");
        verify(movimentacaoEstoqueRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    private ProdutoEntity produto(UUID estabelecimentoId, ProdutoTipo tipo, boolean fracionado, String nome) {
        ProdutoEntity produto = new ProdutoEntity();
        produto.setId(UUID.randomUUID());
        produto.setEstabelecimentoId(estabelecimentoId);
        produto.setTipo(tipo);
        produto.setFracionado(fracionado);
        produto.setNome(nome);
        produto.setUnidadeMedida("un");
        produto.setAtivo(true);
        return produto;
    }

    private EstoqueEntity estoque(ProdutoEntity produto, String quantidade) {
        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(new BigDecimal(quantidade));
        return estoque;
    }

    private ComposicaoDrinkEntity composicao(
            ProdutoEntity drink,
            ProdutoEntity ingrediente,
            String quantidade,
            UnidadeConsumoDrink unidade
    ) {
        ComposicaoDrinkEntity composicao = new ComposicaoDrinkEntity();
        composicao.setDrink(drink);
        composicao.setProduto(ingrediente);
        composicao.setQuantidade(new BigDecimal(quantidade));
        composicao.setUnidadeConsumo(unidade);
        return composicao;
    }

    private void autenticar(UUID idEstabelecimento) {
        Jwt token = Jwt.withTokenValue("token-de-teste")
                .header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString())
                .claim("userId", UUID.randomUUID().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }
}
