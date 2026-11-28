package valhalla.core.stock.app.modules.estoque.service;

import jakarta.persistence.EntityNotFoundException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.caixa.exception.EstoqueInsuficientePedidoException;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.SaidaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.exception.EstoqueReservadoConflitoException;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.LoteEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoLoteRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock private LoteEstoqueRepository loteEstoqueRepository;
    @Mock private MovimentacaoLoteRepository movimentacaoLoteRepository;

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
                ),
                new BusinessEventLogger(),
                new ControleLotesEstoque(loteEstoqueRepository, movimentacaoLoteRepository),
                movimentacaoLoteRepository
        );
        lenient().when(movimentacaoEstoqueRepository.save(any(MovimentacaoEstoqueEntity.class)))
                .thenAnswer(invocation -> {
                    MovimentacaoEstoqueEntity movimentacao = invocation.getArgument(0);
                    movimentacao.setId(1L);
                    return movimentacao;
                });
        lenient().when(movimentacaoEstoqueRepository.saveAndFlush(any(MovimentacaoEstoqueEntity.class)))
                .thenAnswer(invocation -> {
                    MovimentacaoEstoqueEntity movimentacao = invocation.getArgument(0);
                    if (movimentacao.getId() == null) {
                        movimentacao.setId(1L);
                    }
                    return movimentacao;
                });
        lenient().when(loteEstoqueRepository.save(any(LoteEstoqueEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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

    @Test
    void registraEntradaComSucesso() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        EstoqueEntity estoque = estoque(produto, "10");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoIdForUpdate(produto.getId())).thenReturn(Optional.of(estoque));

        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        produto.getId(), TipoMovimentacaoEstoque.ENTRADA, new BigDecimal("5"),
                        "Compra fornecedor", "L123", "NF999"
                )
        );

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("15");
        assertThat(resposta.saldoAnterior()).isEqualByComparingTo("10");
        assertThat(resposta.saldoPosterior()).isEqualByComparingTo("15");
        verify(estoqueRepository).save(estoque);
    }

    @Test
    void registraAjustePositivoComSucesso() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Cerveja Heineken");
        EstoqueEntity estoque = estoque(produto, "20");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoIdForUpdate(produto.getId())).thenReturn(Optional.of(estoque));

        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        produto.getId(), TipoMovimentacaoEstoque.AJUSTE_POSITIVO, new BigDecimal("4"),
                        "Contagem de inventário", null, null
                )
        );

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("24");
        assertThat(resposta.saldoAnterior()).isEqualByComparingTo("20");
        assertThat(resposta.saldoPosterior()).isEqualByComparingTo("24");
        verify(estoqueRepository).save(estoque);
    }

    @Test
    void registraAjusteNegativoComSucesso() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Cerveja Heineken");
        EstoqueEntity estoque = estoque(produto, "20");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoIdForUpdate(produto.getId())).thenReturn(Optional.of(estoque));

        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        produto.getId(), TipoMovimentacaoEstoque.AJUSTE_NEGATIVO, new BigDecimal("3"),
                        "Contagem de inventário", null, null
                )
        );

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("17");
        assertThat(resposta.saldoAnterior()).isEqualByComparingTo("20");
        assertThat(resposta.saldoPosterior()).isEqualByComparingTo("17");
        verify(estoqueRepository).save(estoque);
    }

    @Test
    void registraPerdaComSucesso() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Vinho Tinto");
        EstoqueEntity estoque = estoque(produto, "10");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(estoqueRepository.findByProdutoIdForUpdate(produto.getId())).thenReturn(Optional.of(estoque));

        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        produto.getId(), TipoMovimentacaoEstoque.PERDA, new BigDecimal("2"),
                        "Garrafa quebrada no transporte", null, null
                )
        );

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("8");
        assertThat(resposta.saldoAnterior()).isEqualByComparingTo("10");
        assertThat(resposta.saldoPosterior()).isEqualByComparingTo("8");
        verify(estoqueRepository).save(estoque);
    }

    @Test
    void impedeMovimentacaoDiretaEmDrink() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Caipirinha");
        autenticar(estabelecimentoId);
        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));

        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        drink.getId(), TipoMovimentacaoEstoque.ENTRADA, new BigDecimal("5"),
                        null, null, null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Drink não possui estoque próprio; registre a saída de drink");
    }

    @Test
    void impedeTipoNaoDireto() {
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        UUID.randomUUID(), TipoMovimentacaoEstoque.SAIDA_DRINK, new BigDecimal("1"),
                        null, null, null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O tipo informado não pode ser registrado diretamente");
    }

    @Test
    void registraEntradaInicialComSucesso() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Gin");
        EstoqueEntity estoque = estoque(produto, "15");
        autenticar(estabelecimentoId);

        movimentacaoEstoqueService.registrarEntradaInicial(estoque, new BigDecimal("15"));

        verify(movimentacaoEstoqueRepository).save(any(MovimentacaoEstoqueEntity.class));
    }

    @Test
    void registraEntradaInicialComLoteEValidade() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Gin");
        EstoqueEntity estoque = estoque(produto, "15");
        LocalDate validade = LocalDate.of(2027, 6, 30);
        autenticar(estabelecimentoId);

        movimentacaoEstoqueService.registrarEntradaInicial(estoque, new BigDecimal("15"), "L2611A", validade);

        ArgumentCaptor<valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity> loteCaptor =
                ArgumentCaptor.forClass(valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity.class);
        verify(loteEstoqueRepository).save(loteCaptor.capture());
        assertThat(loteCaptor.getValue().getCodigo()).isEqualTo("L2611A");
        assertThat(loteCaptor.getValue().getValidade()).isEqualTo(validade);
        assertThat(loteCaptor.getValue().getQuantidadeAtual()).isEqualByComparingTo("15");
        verify(movimentacaoEstoqueRepository).save(any(MovimentacaoEstoqueEntity.class));
    }

    @Test
    void ignoraEntradaInicialSeZeroOuNula() {
        EstoqueEntity estoque = new EstoqueEntity();
        movimentacaoEstoqueService.registrarEntradaInicial(estoque, null);
        movimentacaoEstoqueService.registrarEntradaInicial(estoque, BigDecimal.ZERO);

        verify(movimentacaoEstoqueRepository, never()).save(any());
    }

    @Test
    void lancaExcecaoSeEntradaInicialInconsistente() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Gin");
        EstoqueEntity estoque = estoque(produto, "10"); // saldo atual é 10
        autenticar(estabelecimentoId);

        // quantidadeInicial é 20, mas estoque atual é 10 -> inconsistente
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarEntradaInicial(estoque, new BigDecimal("20")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("O saldo inicial informado é inconsistente");
    }

    @Test
    void calculaCustoUnitarioProdutoPadraoEDrink() {
        UUID estabelecimentoId = UUID.randomUUID();

        // 1. Produto padrão com custo
        ProdutoEntity cerveja = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Cerveja");
        cerveja.setPrecoCusto(new BigDecimal("7.50"));
        assertThat(movimentacaoEstoqueService.calcularCustoUnitario(cerveja)).isEqualByComparingTo("7.50");

        // 2. Produto padrão sem custo
        ProdutoEntity agua = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Água");
        agua.setPrecoCusto(null);
        assertThat(movimentacaoEstoqueService.calcularCustoUnitario(agua)).isEqualByComparingTo("0.00");

        // 3. Drink composto por produto fracionado e produto unitário
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Vodka Energy");
        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka Absolut");
        vodka.setPrecoCusto(new BigDecimal("100.00"));
        vodka.setVolumeEmbalagemMl(new BigDecimal("1000")); // 1000 ml = 100.00 -> 0.10 por ml

        ProdutoEntity energetico = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        energetico.setPrecoCusto(new BigDecimal("8.00")); // 8.00 cada

        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId()))
                .thenReturn(List.of(
                        composicao(drink, vodka, "50", UnidadeConsumoDrink.ML),      // 50 ml * 0.10 = 5.00
                        composicao(drink, energetico, "1", UnidadeConsumoDrink.UN)   // 1 un * 8.00 = 8.00
                ));

        BigDecimal custoDrink = movimentacaoEstoqueService.calcularCustoUnitario(drink);
        assertThat(custoDrink).isEqualByComparingTo("13.00");
    }

    @Test
    void converteEntradaEmFardoParaUnidades() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);
        ProdutoEntity cerveja = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Skol Lata");
        cerveja.setFardo(true);
        cerveja.setUnidadesPorFardo(12);
        EstoqueEntity estoque = estoque(cerveja, "10");

        when(produtoRepository.findById(cerveja.getId())).thenReturn(Optional.of(cerveja));
        when(estoqueRepository.findByProdutoIdForUpdate(cerveja.getId())).thenReturn(Optional.of(estoque));

        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        cerveja.getId(),
                        TipoMovimentacaoEstoque.ENTRADA,
                        new BigDecimal("2"),
                        "Reposição",
                        null,
                        null,
                        true
                )
        );

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("34");
        assertThat(resposta.quantidade()).isEqualByComparingTo("24");
        assertThat(resposta.motivo()).isEqualTo("Reposição (2 fardo(s))");
    }

    @Test
    void rejeitaMovimentacaoEmFardoQuandoProdutoNaoPossuiConfiguracaoDeFardo() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);
        ProdutoEntity agua = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Água");
        EstoqueEntity estoque = estoque(agua, "10");

        when(produtoRepository.findById(agua.getId())).thenReturn(Optional.of(agua));
        when(estoqueRepository.findByProdutoIdForUpdate(agua.getId())).thenReturn(Optional.of(estoque));

        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        agua.getId(),
                        TipoMovimentacaoEstoque.ENTRADA,
                        new BigDecimal("1"),
                        null,
                        null,
                        null,
                        true
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O produto informado não possui configuração de fardo");
    }

    @Test
    void validarDisponibilidadeItensBloqueiaQuandoRestamApenasLotesVencidos() {
        UUID estabelecimentoId = UUID.randomUUID();
        ProdutoEntity cerveja = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Heineken");
        EstoqueEntity estoque = estoque(cerveja, "10");
        estoque.setId(10);

        LoteEstoqueEntity vencido = lote(estoque, 1L, "L-VENC", LocalDate.now().minusDays(2), "6");
        LoteEstoqueEntity valido = lote(estoque, 2L, "L-OK", LocalDate.now().plusDays(15), "4");

        when(produtoRepository.findById(cerveja.getId())).thenReturn(Optional.of(cerveja));
        when(estoqueRepository.findAllByProdutoIdIn(any())).thenReturn(List.of(estoque));
        when(loteEstoqueRepository.findAllByEstoqueId(10)).thenReturn(List.of(vencido, valido));

        assertThatThrownBy(() -> movimentacaoEstoqueService.validarDisponibilidadeItens(
                estabelecimentoId,
                List.of(new MovimentacaoEstoqueService.ItemConsumoValidacao(cerveja.getId(), new BigDecimal("5")))
        )).isInstanceOf(EstoqueInsuficientePedidoException.class)
                .hasMessageContaining("Saldo dentro da validade insuficiente");
    }

    @Test
    void baixarEEstornarEstoquePedidoUsaFefoEDevolveParaLotesOriginais() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        ProdutoEntity cerveja = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Corona");
        EstoqueEntity estoque = estoque(cerveja, "10");
        estoque.setId(20);

        LoteEstoqueEntity vencido = lote(estoque, 100L, "L-VENCIDO", LocalDate.now().minusDays(1), "3");
        LoteEstoqueEntity proximo = lote(estoque, 101L, "L-PROX", LocalDate.now().plusDays(5), "2");
        LoteEstoqueEntity distante = lote(estoque, 102L, "L-DIST", LocalDate.now().plusDays(30), "5");

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(UUID.randomUUID());
        pedido.setEstabelecimentoId(estabelecimentoId);
        pedido.setNumero(42L);

        ItemPedidoEntity item = new ItemPedidoEntity();
        item.setId(77L);
        item.setPedidoId(pedido.getId());
        item.setProdutoId(cerveja.getId());
        item.setQuantidade(new BigDecimal("4"));

        when(produtoRepository.findById(cerveja.getId())).thenReturn(Optional.of(cerveja));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any())).thenReturn(List.of(estoque));
        when(loteEstoqueRepository.findAllByEstoqueId(20)).thenReturn(List.of(vencido, proximo, distante));

        movimentacaoEstoqueService.baixarEstoquePedido(pedido, List.of(item), operadorId);

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("6");
        assertThat(vencido.getQuantidadeAtual()).isEqualByComparingTo("3");
        assertThat(proximo.getQuantidadeAtual()).isEqualByComparingTo("0");
        assertThat(distante.getQuantidadeAtual()).isEqualByComparingTo("3");

        ArgumentCaptor<MovimentacaoLoteEntity> vinculoCaptor = ArgumentCaptor.forClass(MovimentacaoLoteEntity.class);
        verify(movimentacaoLoteRepository, org.mockito.Mockito.times(2)).save(vinculoCaptor.capture());
        List<MovimentacaoLoteEntity> vinculosSaida = vinculoCaptor.getAllValues();
        assertThat(vinculosSaida).extracting(v -> v.getLote().getCodigo(), MovimentacaoLoteEntity::getQuantidade)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("L-PROX", new BigDecimal("2")),
                        org.assertj.core.groups.Tuple.tuple("L-DIST", new BigDecimal("2"))
                );

        MovimentacaoEstoqueEntity saidaRegistrada = new MovimentacaoEstoqueEntity();
        saidaRegistrada.setId(500L);
        saidaRegistrada.setEstoque(estoque);
        saidaRegistrada.setItemPedidoId(77L);
        saidaRegistrada.setTipo(TipoMovimentacaoEstoque.SAIDA);
        saidaRegistrada.setQuantidade(new BigDecimal("4"));
        vinculosSaida.forEach(v -> v.setMovimentacao(saidaRegistrada));

        when(movimentacaoEstoqueRepository.findAllByItemPedidoIdInAndTipoInOrderByIdAsc(any(), any()))
                .thenReturn(List.of(saidaRegistrada));
        when(movimentacaoLoteRepository.findAllByMovimentacaoIdInOrderByIdAsc(List.of(500L)))
                .thenReturn(vinculosSaida);

        movimentacaoEstoqueService.estornarEstoquePedido(pedido, List.of(item), operadorId);

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("10");
        assertThat(proximo.getQuantidadeAtual()).isEqualByComparingTo("2");
        assertThat(distante.getQuantidadeAtual()).isEqualByComparingTo("5");
        assertThat(vencido.getQuantidadeAtual()).isEqualByComparingTo("3");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void avaliaTodosOsFiltrosDaEspecificacaoDeMovimentacoes() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        movimentacaoEstoqueService.listarMovimentacoes(
                produtoId, TipoMovimentacaoEstoque.ENTRADA, usuarioId,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20),
                0, 20, "ocorridoEm", DirecaoOrdenacao.DESC
        );

        ArgumentCaptor<Specification<MovimentacaoEstoqueEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(movimentacaoEstoqueRepository).findAll(specCaptor.capture(), any(Pageable.class));

        Predicate predicate = org.mockito.Mockito.mock(Predicate.class);
        Path path = org.mockito.Mockito.mock(Path.class, inv ->
                Path.class.isAssignableFrom(inv.getMethod().getReturnType()) ? inv.getMock() : null);
        Join join = org.mockito.Mockito.mock(Join.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Join.class.isAssignableFrom(rt)) return inv.getMock();
            if (Path.class.isAssignableFrom(rt)) return path;
            return null;
        });
        Root<MovimentacaoEstoqueEntity> root = org.mockito.Mockito.mock(Root.class, inv -> {
            Class<?> rt = inv.getMethod().getReturnType();
            if (Join.class.isAssignableFrom(rt)) return join;
            if (Path.class.isAssignableFrom(rt)) return path;
            return null;
        });
        CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder builder = org.mockito.Mockito.mock(CriteriaBuilder.class, inv ->
                Predicate.class.isAssignableFrom(inv.getMethod().getReturnType()) ? predicate : null);

        assertThat(specCaptor.getValue().toPredicate(root, query, builder)).isNotNull();
    }

    @Test
    void validaErrosEmSaidaDrinkDisponibilidadeEBaixaPedidoComFefo() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        ProdutoEntity padrao = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Cerveja");
        when(produtoRepository.findById(padrao.getId())).thenReturn(Optional.of(padrao));
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(padrao.getId(), BigDecimal.ONE, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O produto informado não é um drink");

        ProdutoEntity drinkInativo = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Negroni");
        drinkInativo.setAtivo(false);
        when(produtoRepository.findById(drinkInativo.getId())).thenReturn(Optional.of(drinkInativo));
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(drinkInativo.getId(), BigDecimal.ONE, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O drink informado está inativo");

        ProdutoEntity drinkSemReceita = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Mojito");
        when(produtoRepository.findById(drinkSemReceita.getId())).thenReturn(Optional.of(drinkSemReceita));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drinkSemReceita.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(drinkSemReceita.getId(), BigDecimal.ONE, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O drink não possui ingredientes cadastrados");

        ProdutoEntity ingrediente = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Rum");
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drinkSemReceita.getId()))
                .thenReturn(List.of(composicao(drinkSemReceita, ingrediente, "1", UnidadeConsumoDrink.UN)));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any())).thenReturn(List.of());
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(drinkSemReceita.getId(), BigDecimal.ONE, null)
        )).isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Estoque de um ingrediente não encontrado");

        // validarDisponibilidadeItens com produto inativo e drink sem receita
        padrao.setAtivo(false);
        assertThatThrownBy(() -> movimentacaoEstoqueService.validarDisponibilidadeItens(
                estabelecimentoId,
                List.of(new MovimentacaoEstoqueService.ItemConsumoValidacao(padrao.getId(), BigDecimal.ONE))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("está inativo");

        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drinkSemReceita.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> movimentacaoEstoqueService.validarDisponibilidadeItens(
                estabelecimentoId,
                List.of(new MovimentacaoEstoqueService.ItemConsumoValidacao(drinkSemReceita.getId(), BigDecimal.ONE))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não possui ingredientes cadastrados");

        // registrarBaixaPedidoComFefo: tipo inválido, saldo negativo e falha no controle de lotes
        EstoqueEntity estoquePadrao = estoque(padrao, "5");
        estoquePadrao.setId(55);
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarBaixaPedidoComFefo(
                estoquePadrao, TipoMovimentacaoEstoque.ENTRADA, BigDecimal.ONE,
                "Teste", "PEDIDO", UUID.randomUUID(), 1L, null, null, LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de movimentação inválido");

        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarBaixaPedidoComFefo(
                estoquePadrao, TipoMovimentacaoEstoque.SAIDA, new BigDecimal("6"),
                "Teste", "PEDIDO", UUID.randomUUID(), 1L, null, null, LocalDateTime.now()
        )).isInstanceOf(EstoqueInsuficientePedidoException.class);

        LoteEstoqueEntity loteVencido = lote(estoquePadrao, 10L, "L-VENC", LocalDate.now().minusDays(1), "5");
        when(loteEstoqueRepository.findAllByEstoqueId(55)).thenReturn(List.of(loteVencido));
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarBaixaPedidoComFefo(
                estoquePadrao, TipoMovimentacaoEstoque.SAIDA, BigDecimal.ONE,
                "Teste", "PEDIDO", UUID.randomUUID(), 1L, null, null, LocalDateTime.now()
        )).isInstanceOf(EstoqueInsuficientePedidoException.class);

        estoquePadrao.setQuantidadeReservada(new BigDecimal("3"));
        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarBaixaPedidoComFefo(
                estoquePadrao, TipoMovimentacaoEstoque.SAIDA, new BigDecimal("3"),
                "Teste", "PEDIDO", UUID.randomUUID(), 1L, null, null, LocalDateTime.now()
        )).isInstanceOf(EstoqueInsuficientePedidoException.class)
                .satisfies(ex -> assertThat(((EstoqueInsuficientePedidoException) ex).getDisponivel())
                        .isEqualByComparingTo("2"));
    }

    @Test
    void bloqueiaMovimentacaoEDrinkQuandoAtingeQuantidadeReservadaEmPedidos() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        ProdutoEntity cerveja = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Heineken");
        EstoqueEntity estoque = estoque(cerveja, "10");
        estoque.setQuantidadeReservada(new BigDecimal("6"));
        when(produtoRepository.findById(cerveja.getId())).thenReturn(Optional.of(cerveja));
        when(estoqueRepository.findByProdutoIdForUpdate(cerveja.getId())).thenReturn(Optional.of(estoque));

        for (TipoMovimentacaoEstoque tipo : List.of(
                TipoMovimentacaoEstoque.SAIDA,
                TipoMovimentacaoEstoque.PERDA,
                TipoMovimentacaoEstoque.AJUSTE_NEGATIVO
        )) {
            assertThatThrownBy(() -> movimentacaoEstoqueService.registrarMovimentacao(
                    new MovimentacaoRequestDto(
                            cerveja.getId(), tipo, new BigDecimal("5"),
                            "Motivo operacional", null, null
                    )
            )).isInstanceOf(EstoqueReservadoConflitoException.class)
                    .hasMessageContaining("6 reservado(s)")
                    .satisfies(ex -> {
                        var conflito = (EstoqueReservadoConflitoException) ex;
                        assertThat(conflito.getProdutoNome()).isEqualTo("Heineken");
                        assertThat(conflito.getDisponivelLivre()).isEqualByComparingTo("4");
                        assertThat(conflito.getReservado()).isEqualByComparingTo("6");
                    });
        }

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("10");
        verify(estoqueRepository, never()).save(any());
        verify(movimentacaoEstoqueRepository, never()).save(any());

        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Gin Tônica");
        ProdutoEntity gin = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Gin Tanqueray");
        EstoqueEntity estoqueGin = estoque(gin, "500");
        estoqueGin.setQuantidadeReservada(BigDecimal.ZERO);
        EstoqueEntity estoqueTonic = estoque(cerveja, "4");
        estoqueTonic.setQuantidadeReservada(new BigDecimal("3"));

        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId()))
                .thenReturn(List.of(
                        composicao(drink, gin, "50", UnidadeConsumoDrink.ML),
                        composicao(drink, cerveja, "1", UnidadeConsumoDrink.UN)
                ));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any()))
                .thenReturn(List.of(estoqueGin, estoqueTonic));

        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(drink.getId(), new BigDecimal("2"), "Consumo")
        )).isInstanceOf(EstoqueReservadoConflitoException.class)
                .hasMessageContaining("Heineken");

        assertThat(estoqueGin.getQuantidadeAtual()).isEqualByComparingTo("500");
        assertThat(estoqueTonic.getQuantidadeAtual()).isEqualByComparingTo("4");
        verify(estoqueRepository, never()).save(any());
    }

    @Test
    void bloqueiaMovimentacaoEDrinkQuandoConsomeLotesValidosAbaixoDaReservaMesmoComLoteVencido() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);

        ProdutoEntity cerveja = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Heineken");
        EstoqueEntity estoque = estoque(cerveja, "15");
        estoque.setId(50);
        estoque.setQuantidadeReservada(new BigDecimal("6"));
        LoteEstoqueEntity vencido = lote(estoque, 1L, "L-VENC", LocalDate.now().minusDays(2), "5");
        LoteEstoqueEntity valido = lote(estoque, 2L, "L-OK", LocalDate.now().plusDays(15), "10");

        when(produtoRepository.findById(cerveja.getId())).thenReturn(Optional.of(cerveja));
        when(estoqueRepository.findByProdutoIdForUpdate(cerveja.getId())).thenReturn(Optional.of(estoque));
        when(loteEstoqueRepository.findAllByEstoqueId(50)).thenReturn(List.of(vencido, valido));

        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        cerveja.getId(), TipoMovimentacaoEstoque.SAIDA, new BigDecimal("5"),
                        "Saída operacional", null, null
                )
        )).isInstanceOf(EstoqueReservadoConflitoException.class)
                .hasMessageContaining("6 reservado(s)")
                .satisfies(ex -> {
                    var conflito = (EstoqueReservadoConflitoException) ex;
                    assertThat(conflito.getDisponivelLivre()).isEqualByComparingTo("4");
                    assertThat(conflito.getReservado()).isEqualByComparingTo("6");
                });

        verify(estoqueRepository, never()).save(any());
        verify(movimentacaoEstoqueRepository, never()).save(any());

        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Gin Tônica");
        ProdutoEntity tonica = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Tônica");
        EstoqueEntity estoqueTonica = estoque(tonica, "9");
        estoqueTonica.setId(51);
        estoqueTonica.setQuantidadeReservada(new BigDecimal("3"));
        LoteEstoqueEntity tonicaVencida = lote(estoqueTonica, 3L, "T-VENC", LocalDate.now().minusDays(1), "5");
        LoteEstoqueEntity tonicaValida = lote(estoqueTonica, 4L, "T-OK", LocalDate.now().plusDays(10), "4");

        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId()))
                .thenReturn(List.of(composicao(drink, tonica, "1", UnidadeConsumoDrink.UN)));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any())).thenReturn(List.of(estoqueTonica));
        when(loteEstoqueRepository.findAllByEstoqueId(51)).thenReturn(List.of(tonicaVencida, tonicaValida));

        assertThatThrownBy(() -> movimentacaoEstoqueService.registrarSaidaDrink(
                new SaidaDrinkRequestDto(drink.getId(), new BigDecimal("2"), "Consumo")
        )).isInstanceOf(EstoqueReservadoConflitoException.class)
                .hasMessageContaining("Tônica");

        verify(estoqueRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exibeRotuloWebhookOuSistemaQuandoMovimentacaoNaoTemUsuario() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);
        ProdutoEntity produto = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Red Bull");
        EstoqueEntity estoque = estoque(produto, "10");

        MovimentacaoEstoqueEntity movWebhook = new MovimentacaoEstoqueEntity();
        movWebhook.setId(101L);
        movWebhook.setEstoque(estoque);
        movWebhook.setUsuarioId(null);
        movWebhook.setOrigem("WEBHOOK");
        movWebhook.setTipo(TipoMovimentacaoEstoque.SAIDA);
        movWebhook.setQuantidade(BigDecimal.ONE);
        movWebhook.setSaldoAnterior(new BigDecimal("11"));
        movWebhook.setSaldoPosterior(BigDecimal.TEN);
        movWebhook.setOcorridoEm(LocalDateTime.now());

        MovimentacaoEstoqueEntity movSistema = new MovimentacaoEstoqueEntity();
        movSistema.setId(102L);
        movSistema.setEstoque(estoque);
        movSistema.setUsuarioId(null);
        movSistema.setOrigem("SISTEMA");
        movSistema.setTipo(TipoMovimentacaoEstoque.ENTRADA);
        movSistema.setQuantidade(BigDecimal.ONE);
        movSistema.setSaldoAnterior(BigDecimal.TEN);
        movSistema.setSaldoPosterior(new BigDecimal("11"));
        movSistema.setOcorridoEm(LocalDateTime.now());

        when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(movWebhook, movSistema), PageRequest.of(0, 20), 2));

        var resultado = movimentacaoEstoqueService.listarMovimentacoes(
                null, null, null, null, null, 0, 20, "ocorridoEm", DirecaoOrdenacao.DESC
        );

        assertThat(resultado.itens()).extracting(MovimentacaoResponseDto::usuarioNome)
                .containsExactly("Webhook / Point", "Sistema");
    }

    @Test
    void converteEntradaEmFardoParaMlQuandoProdutoEhFracionado() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);
        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka Absolut");
        vodka.setUnidadeMedida("garrafa");
        vodka.setVolumeEmbalagemMl(new BigDecimal("1000"));
        vodka.setFardo(true);
        vodka.setUnidadesPorFardo(6);
        EstoqueEntity estoque = estoque(vodka, "1000");

        when(produtoRepository.findById(vodka.getId())).thenReturn(Optional.of(vodka));
        when(estoqueRepository.findByProdutoIdForUpdate(vodka.getId())).thenReturn(Optional.of(estoque));

        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(
                new MovimentacaoRequestDto(
                        vodka.getId(),
                        TipoMovimentacaoEstoque.ENTRADA,
                        new BigDecimal("2"),
                        "Reposição caixa",
                        null,
                        null,
                        true
                )
        );

        assertThat(estoque.getQuantidadeAtual()).isEqualByComparingTo("13000");
        assertThat(resposta.quantidade()).isEqualByComparingTo("12000");
        assertThat(resposta.unidadeEstoque()).isEqualTo("ML");
        assertThat(resposta.motivo()).isEqualTo("Reposição caixa (2 fardo(s))");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportacaoDeMovimentacoesNaoConsultaVinculosDeLoteERespeitaLimiteMaximo() {
        UUID estabelecimentoId = UUID.randomUUID();
        autenticar(estabelecimentoId);
        ProdutoEntity cerveja = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Cerveja");
        EstoqueEntity estoque = estoque(cerveja, "10");

        MovimentacaoEstoqueEntity movimento = new MovimentacaoEstoqueEntity();
        movimento.setId(99L);
        movimento.setEstoque(estoque);
        movimento.setUsuarioId(UUID.randomUUID());
        movimento.setTipo(TipoMovimentacaoEstoque.ENTRADA);
        movimento.setQuantidade(BigDecimal.ONE);
        movimento.setSaldoAnterior(new BigDecimal("9"));
        movimento.setSaldoPosterior(BigDecimal.TEN);
        movimento.setLote("LOTE-01");
        movimento.setOcorridoEm(LocalDateTime.now());

        when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(movimento)));

        List<MovimentacaoResponseDto> exportadas = movimentacaoEstoqueService.listarMovimentacoesParaExportacao(
                null, null, null, null, null, "ocorridoEm", DirecaoOrdenacao.DESC
        );

        assertThat(exportadas).hasSize(1);
        assertThat(exportadas.getFirst().lote()).isEqualTo("LOTE-01");
        assertThat(exportadas.getFirst().lotes()).isEmpty();
        verify(movimentacaoLoteRepository, never()).findAllByMovimentacaoIdInOrderByIdAsc(any());

        when(movimentacaoEstoqueRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(movimento),
                        PageRequest.of(0, MovimentacaoEstoqueService.LIMITE_REGISTROS_EXPORTACAO + 1),
                        MovimentacaoEstoqueService.LIMITE_REGISTROS_EXPORTACAO + 1L
                ));

        assertThatThrownBy(() -> movimentacaoEstoqueService.listarMovimentacoesParaExportacao(
                null, null, null, null, null, "ocorridoEm", DirecaoOrdenacao.DESC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limite máximo");
    }

    private LoteEstoqueEntity lote(
            EstoqueEntity estoque,
            Long id,
            String codigo,
            LocalDate validade,
            String quantidade
    ) {
        LoteEstoqueEntity lote = new LoteEstoqueEntity();
        lote.setId(id);
        lote.setEstoque(estoque);
        lote.setCodigo(codigo);
        lote.setValidade(validade);
        lote.setQuantidadeAtual(new BigDecimal(quantidade));
        return lote;
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
