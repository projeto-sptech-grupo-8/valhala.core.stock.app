package valhalla.core.stock.app.modules.caixa.pedido.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ReservaEstoqueEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ReservaEstoqueRepository;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.point.config.ModoMercadoPagoPoint;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.estoque.service.MovimentacaoEstoqueService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaEstoqueServiceTest {

    @Mock private ReservaEstoqueRepository reservaEstoqueRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private EstoqueRepository estoqueRepository;
    @Mock private ComposicaoDrinkRepository composicaoDrinkRepository;
    @Mock private MovimentacaoEstoqueService movimentacaoEstoqueService;

    private ReservaEstoqueService service;

    @BeforeEach
    void configurar() {
        MercadoPagoPointProperties props = new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.MOCK, null, null, true, 300L, null,
                3000L, 8000L, "PT5M", 300L, 20L, 15000L, true, 300, 30
        );
        service = new ReservaEstoqueService(
                reservaEstoqueRepository,
                produtoRepository,
                estoqueRepository,
                composicaoDrinkRepository,
                movimentacaoEstoqueService,
                props
        );
        lenient().when(reservaEstoqueRepository.saveAllAndFlush(anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reservarParaPagamentoPointTrataListaVaziaExistentesAtivasEEfetivadas() {
        UUID pedidoId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        PedidoEntity pedido = pedido(pedidoId, UUID.randomUUID());

        assertThat(service.reservarParaPagamentoPoint(pedido, List.of(), pagamentoId)).isEmpty();
        assertThat(service.reservarParaPagamentoPoint(pedido, null, pagamentoId)).isEmpty();

        ItemPedidoEntity item = item(1L, pedidoId, UUID.randomUUID(), BigDecimal.ONE);
        ReservaEstoqueEntity ativa = new ReservaEstoqueEntity();
        ativa.setStatus(StatusReservaEstoque.ATIVA);
        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of(ativa));

        List<ReservaEstoqueEntity> renovadas = service.reservarParaPagamentoPoint(pedido, List.of(item), pagamentoId);
        assertThat(renovadas).hasSize(1);
        assertThat(ativa.getPagamentoPedidoId()).isEqualTo(pagamentoId);
        assertThat(ativa.getExpiraEm()).isAfter(LocalDateTime.now().plusSeconds(200));

        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of());
        ReservaEstoqueEntity efetivada = new ReservaEstoqueEntity();
        efetivada.setStatus(StatusReservaEstoque.EFETIVADA);
        when(reservaEstoqueRepository.findByPedidoIdAndStatusOrderByIdAsc(pedidoId, StatusReservaEstoque.EFETIVADA))
                .thenReturn(List.of(efetivada));

        List<ReservaEstoqueEntity> retornadas = service.reservarParaPagamentoPoint(pedido, List.of(item), pagamentoId);
        assertThat(retornadas).containsExactly(efetivada);
    }

    @Test
    void reservarParaPagamentoPointReservaProdutoFracionadoEDrinkComValidacoes() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        PedidoEntity pedido = pedido(pedidoId, estabelecimentoId);

        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka", true);
        vodka.setVolumeEmbalagemMl(new BigDecimal("1000"));
        ProdutoEntity energetico = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Energetico", true);
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Combo Vodka", true);

        EstoqueEntity estoqueVodka = estoque(10, vodka, "2000", "0");
        EstoqueEntity estoqueEnergetico = estoque(11, energetico, "20", "1");

        ItemPedidoEntity itemVodka = item(1L, pedidoId, vodka.getId(), new BigDecimal("1"));
        ItemPedidoEntity itemDrink = item(2L, pedidoId, drink.getId(), new BigDecimal("2"));

        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of());
        when(reservaEstoqueRepository.findByPedidoIdAndStatusOrderByIdAsc(pedidoId, StatusReservaEstoque.EFETIVADA))
                .thenReturn(List.of());
        when(produtoRepository.findById(vodka.getId())).thenReturn(Optional.of(vodka));
        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId()))
                .thenReturn(List.of(
                        composicao(drink, vodka, new BigDecimal("50"), UnidadeConsumoDrink.ML),
                        composicao(drink, energetico, new BigDecimal("1"), UnidadeConsumoDrink.UN)
                ));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any()))
                .thenReturn(List.of(estoqueVodka, estoqueEnergetico));

        List<ReservaEstoqueEntity> reservas = service.reservarParaPagamentoPoint(
                pedido, List.of(itemVodka, itemDrink), pagamentoId
        );

        assertThat(reservas).hasSize(3);
        assertThat(estoqueVodka.getQuantidadeReservada()).isEqualByComparingTo("1100");
        assertThat(estoqueEnergetico.getQuantidadeReservada()).isEqualByComparingTo("3");
        verify(movimentacaoEstoqueService).validarSaldoVendavel(vodka, estoqueVodka, new BigDecimal("1100"));
        verify(movimentacaoEstoqueService).validarSaldoVendavel(energetico, estoqueEnergetico, new BigDecimal("2"));
    }

    @Test
    void reservarParaPagamentoPointValidaErrosDeProdutoEReceita() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        PedidoEntity pedido = pedido(pedidoId, estabelecimentoId);

        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of());
        when(reservaEstoqueRepository.findByPedidoIdAndStatusOrderByIdAsc(pedidoId, StatusReservaEstoque.EFETIVADA))
                .thenReturn(List.of());

        UUID inexistenteId = UUID.randomUUID();
        when(produtoRepository.findById(inexistenteId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reservarParaPagamentoPoint(
                pedido, List.of(item(1L, pedidoId, inexistenteId, BigDecimal.ONE)), UUID.randomUUID()
        )).isInstanceOf(EntityNotFoundException.class);

        ProdutoEntity outroEstab = produto(UUID.randomUUID(), ProdutoTipo.PADRAO, false, "Outro", true);
        when(produtoRepository.findById(outroEstab.getId())).thenReturn(Optional.of(outroEstab));
        assertThatThrownBy(() -> service.reservarParaPagamentoPoint(
                pedido, List.of(item(1L, pedidoId, outroEstab.getId(), BigDecimal.ONE)), UUID.randomUUID()
        )).isInstanceOf(AccessDeniedException.class);

        ProdutoEntity inativo = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Inativo", false);
        when(produtoRepository.findById(inativo.getId())).thenReturn(Optional.of(inativo));
        assertThatThrownBy(() -> service.reservarParaPagamentoPoint(
                pedido, List.of(item(1L, pedidoId, inativo.getId(), BigDecimal.ONE)), UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("está inativo");

        ProdutoEntity drinkSemReceita = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Negroni", true);
        when(produtoRepository.findById(drinkSemReceita.getId())).thenReturn(Optional.of(drinkSemReceita));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drinkSemReceita.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.reservarParaPagamentoPoint(
                pedido, List.of(item(1L, pedidoId, drinkSemReceita.getId(), BigDecimal.ONE)), UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não possui ingredientes");

        ProdutoEntity ingUnidadeErrada = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Água", true);
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drinkSemReceita.getId()))
                .thenReturn(List.of(composicao(drinkSemReceita, ingUnidadeErrada, BigDecimal.TEN, UnidadeConsumoDrink.ML)));
        assertThatThrownBy(() -> service.reservarParaPagamentoPoint(
                pedido, List.of(item(1L, pedidoId, drinkSemReceita.getId(), BigDecimal.ONE)), UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unidade incompatível");
    }

    @Test
    void efetivarReservasOuBaixarEstoqueCobreFallbackEEfetivacaoDeDrink() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID operadorId = UUID.randomUUID();
        PedidoEntity pedido = pedido(pedidoId, estabelecimentoId);

        // 1. Sem reservas ativas, já efetivada -> no-op
        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of());
        when(reservaEstoqueRepository.findByPedidoIdAndStatusOrderByIdAsc(pedidoId, StatusReservaEstoque.EFETIVADA))
                .thenReturn(List.of(new ReservaEstoqueEntity()));
        service.efetivarReservasOuBaixarEstoque(pedido, List.of(), operadorId, "PEDIDO");
        verify(movimentacaoEstoqueService, never()).baixarEstoquePedido(any(), any(), any());

        // 2. Sem reservas ativas e sem efetivadas -> baixa direta
        when(reservaEstoqueRepository.findByPedidoIdAndStatusOrderByIdAsc(pedidoId, StatusReservaEstoque.EFETIVADA))
                .thenReturn(List.of());
        service.efetivarReservasOuBaixarEstoque(pedido, List.of(), operadorId, "PEDIDO");
        verify(movimentacaoEstoqueService).baixarEstoquePedido(pedido, List.of(), operadorId);

        // 3. Com reservas ativas (padrão + drink) -> efetiva com snapshot e ajusta reservado negativo para zero
        ProdutoEntity drink = produto(estabelecimentoId, ProdutoTipo.DRINK, false, "Caipirinha", true);
        ProdutoEntity vodka = produto(estabelecimentoId, ProdutoTipo.PADRAO, true, "Vodka", true);
        ProdutoEntity gelo = produto(estabelecimentoId, ProdutoTipo.PADRAO, false, "Gelo", true);

        EstoqueEntity estVodka = estoque(1, vodka, "500", "20"); // reservado menor que reserva -> clamp para 0
        EstoqueEntity estGelo = estoque(2, gelo, "10", "5");

        ReservaEstoqueEntity rDrink = new ReservaEstoqueEntity();
        rDrink.setPedidoId(pedidoId);
        rDrink.setItemPedidoId(10L);
        rDrink.setProdutoId(vodka.getId());
        rDrink.setOrigemDrinkId(drink.getId());
        rDrink.setQuantidade(new BigDecimal("50"));

        ReservaEstoqueEntity rPadrao = new ReservaEstoqueEntity();
        rPadrao.setPedidoId(pedidoId);
        rPadrao.setItemPedidoId(11L);
        rPadrao.setProdutoId(gelo.getId());
        rPadrao.setQuantidade(new BigDecimal("2"));

        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of(rDrink, rPadrao));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any()))
                .thenReturn(List.of(estVodka, estGelo));
        when(produtoRepository.findById(drink.getId())).thenReturn(Optional.of(drink));
        when(composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId()))
                .thenReturn(List.of(composicao(drink, vodka, new BigDecimal("50"), UnidadeConsumoDrink.ML)));

        MovimentacaoEstoqueEntity movSalva = new MovimentacaoEstoqueEntity();
        movSalva.setId(99L);
        when(movimentacaoEstoqueService.registrarBaixaPedidoComFefo(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(movSalva);

        service.efetivarReservasOuBaixarEstoque(
                pedido,
                List.of(item(10L, pedidoId, drink.getId(), BigDecimal.ONE), item(11L, pedidoId, gelo.getId(), BigDecimal.valueOf(2))),
                operadorId,
                null
        );

        assertThat(estVodka.getQuantidadeReservada()).isEqualByComparingTo("0");
        assertThat(estGelo.getQuantidadeReservada()).isEqualByComparingTo("3");
        assertThat(rDrink.getStatus()).isEqualTo(StatusReservaEstoque.EFETIVADA);
        assertThat(rPadrao.getStatus()).isEqualTo(StatusReservaEstoque.EFETIVADA);
        verify(movimentacaoEstoqueService).registrarBaixaPedidoComFefo(
                eq(estVodka), eq(TipoMovimentacaoEstoque.SAIDA_DRINK), eq(new BigDecimal("50")),
                any(), eq("WEBHOOK"), eq(null), eq(10L), eq(drink.getId()), any(), any()
        );
    }

    @Test
    void liberarReservasAtivasDoPedidoValidaStatusETruncaMotivo() {
        UUID pedidoId = UUID.randomUUID();
        assertThatThrownBy(() -> service.liberarReservasAtivasDoPedido(pedidoId, StatusReservaEstoque.ATIVA, "x"))
                .isInstanceOf(IllegalArgumentException.class);

        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of());
        assertThat(service.liberarReservasAtivasDoPedido(pedidoId, StatusReservaEstoque.EXPIRADA, null)).isZero();

        ProdutoEntity produto = produto(UUID.randomUUID(), ProdutoTipo.PADRAO, false, "Suco", true);
        EstoqueEntity est = estoque(1, produto, "10", "3");
        ReservaEstoqueEntity reserva = new ReservaEstoqueEntity();
        reserva.setPedidoId(pedidoId);
        reserva.setProdutoId(produto.getId());
        reserva.setQuantidade(new BigDecimal("5")); // > 3 => clamp para 0

        when(reservaEstoqueRepository.buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA))
                .thenReturn(List.of(reserva));
        when(estoqueRepository.findAllByProdutoIdInForUpdate(any())).thenReturn(List.of(est));

        String motivoLongo = "M".repeat(250);
        int liberadas = service.liberarReservasAtivasDoPedido(pedidoId, StatusReservaEstoque.LIBERADA, motivoLongo);
        assertThat(liberadas).isEqualTo(1);
        assertThat(est.getQuantidadeReservada()).isEqualByComparingTo("0");
        assertThat(reserva.getStatus()).isEqualTo(StatusReservaEstoque.LIBERADA);
        assertThat(reserva.getMotivoLiberacao()).hasSize(200);
    }

    private PedidoEntity pedido(UUID id, UUID estabelecimentoId) {
        PedidoEntity p = new PedidoEntity();
        p.setId(id);
        p.setEstabelecimentoId(estabelecimentoId);
        p.setNumero(10L);
        return p;
    }

    private ItemPedidoEntity item(Long id, UUID pedidoId, UUID produtoId, BigDecimal qtd) {
        ItemPedidoEntity i = new ItemPedidoEntity();
        i.setId(id);
        i.setPedidoId(pedidoId);
        i.setProdutoId(produtoId);
        i.setQuantidade(qtd);
        return i;
    }

    private ProdutoEntity produto(UUID estabId, ProdutoTipo tipo, boolean fracionado, String nome, boolean ativo) {
        ProdutoEntity p = new ProdutoEntity();
        p.setId(UUID.randomUUID());
        p.setEstabelecimentoId(estabId);
        p.setTipo(tipo);
        p.setFracionado(fracionado);
        p.setNome(nome);
        p.setAtivo(ativo);
        return p;
    }

    private EstoqueEntity estoque(Integer id, ProdutoEntity produto, String atual, String reservada) {
        EstoqueEntity e = new EstoqueEntity();
        e.setId(id);
        e.setProduto(produto);
        e.setQuantidadeAtual(new BigDecimal(atual));
        e.setQuantidadeReservada(new BigDecimal(reservada));
        return e;
    }

    private ComposicaoDrinkEntity composicao(
            ProdutoEntity drink,
            ProdutoEntity ing,
            BigDecimal qtd,
            UnidadeConsumoDrink unidade
    ) {
        ComposicaoDrinkEntity c = new ComposicaoDrinkEntity();
        c.setDrink(drink);
        c.setProduto(ing);
        c.setQuantidade(qtd);
        c.setUnidadeConsumo(unidade);
        return c;
    }
}
