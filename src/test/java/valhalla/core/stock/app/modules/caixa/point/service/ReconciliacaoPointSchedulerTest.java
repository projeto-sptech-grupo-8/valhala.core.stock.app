package valhalla.core.stock.app.modules.caixa.point.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ReservaEstoqueRepository;
import valhalla.core.stock.app.modules.caixa.pedido.service.ReservaEstoqueService;
import valhalla.core.stock.app.modules.caixa.point.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.point.config.ModoMercadoPagoPoint;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliacaoPointSchedulerTest {

    @Mock private PagamentoPedidoRepository pagamentoPedidoRepository;
    @Mock private ReservaEstoqueRepository reservaEstoqueRepository;
    @Mock private MercadoPagoPointClient mercadoPagoPointClient;
    @Mock private TransicaoPagamentoPointService transicaoPagamentoPointService;
    @Mock private ReservaEstoqueService reservaEstoqueService;

    private MercadoPagoPointProperties props(boolean habilitada) {
        return new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.MOCK, null, null, true, 300L, null,
                3000L, 8000L, "PT5M", 300L, 20L, 15000L, habilitada, 300, 30
        );
    }

    @Test
    void executarCicloReconciliacaoRespeitaFlagEReconciliaPendentes() {
        ReconciliacaoPointScheduler desabilitado = new ReconciliacaoPointScheduler(
                props(false), pagamentoPedidoRepository, reservaEstoqueRepository,
                mercadoPagoPointClient, transicaoPagamentoPointService, reservaEstoqueService
        );
        desabilitado.executarCicloReconciliacao();
        verify(pagamentoPedidoRepository, never()).buscarPagamentosPointParaReconciliacao(any(), any());

        ReconciliacaoPointScheduler scheduler = new ReconciliacaoPointScheduler(
                props(true), pagamentoPedidoRepository, reservaEstoqueRepository,
                mercadoPagoPointClient, transicaoPagamentoPointService, reservaEstoqueService
        );

        PagamentoPedidoEntity semOrder = new PagamentoPedidoEntity();
        semOrder.setId(UUID.randomUUID());
        PagamentoPedidoEntity comOrderOk = new PagamentoPedidoEntity();
        comOrderOk.setId(UUID.randomUUID());
        comOrderOk.setOrderExternaId("ORD-OK");
        PagamentoPedidoEntity comOrderErro = new PagamentoPedidoEntity();
        comOrderErro.setId(UUID.randomUUID());
        comOrderErro.setOrderExternaId("ORD-ERR");

        when(pagamentoPedidoRepository.buscarPagamentosPointParaReconciliacao(any(), any()))
                .thenReturn(List.of(semOrder, comOrderOk, comOrderErro));

        OrderPointResultado resOk = new OrderPointResultado(
                "ORD-OK", "processed", "accredited", "PAY-1", "accredited", "REF-1", BigDecimal.TEN, "{}"
        );
        when(mercadoPagoPointClient.consultarOrder("ORD-OK")).thenReturn(resOk);
        when(mercadoPagoPointClient.consultarOrder("ORD-ERR"))
                .thenThrow(new RuntimeException("Erro rede"));
        when(reservaEstoqueRepository.listarPedidosComReservasExpiradas(any())).thenReturn(List.of());

        scheduler.executarCicloReconciliacao();

        verify(transicaoPagamentoPointService).aplicarResultadoOrder(
                comOrderOk.getId(), resOk, OrigemAuditoriaCaixa.SISTEMA, null
        );
    }

    @Test
    void expirarReservasVencidasConsultaAntesDeExpirarELiberaReservasOrfas() {
        ReconciliacaoPointScheduler scheduler = new ReconciliacaoPointScheduler(
                props(true), pagamentoPedidoRepository, reservaEstoqueRepository,
                mercadoPagoPointClient, transicaoPagamentoPointService, reservaEstoqueService
        );

        UUID pedidoJaPago = UUID.randomUUID();
        UUID pedidoExpirar = UUID.randomUUID();
        UUID pedidoOrfao = UUID.randomUUID();
        when(reservaEstoqueRepository.listarPedidosComReservasExpiradas(any()))
                .thenReturn(List.of(pedidoJaPago, pedidoExpirar, pedidoOrfao));

        // 1. Pedido que na consulta prévia já está aprovado -> não cancela nem expira
        PagamentoPedidoEntity pagPago = new PagamentoPedidoEntity();
        pagPago.setId(UUID.randomUUID());
        pagPago.setProvedor("MERCADO_PAGO");
        pagPago.setStatus(StatusPagamentoPedido.EM_TERMINAL);
        pagPago.setOrderExternaId("ORD-PAGO");
        PagamentoPedidoEntity pagAtualizadoAprovado = new PagamentoPedidoEntity();
        pagAtualizadoAprovado.setId(pagPago.getId());
        pagAtualizadoAprovado.setProvedor("MERCADO_PAGO");
        pagAtualizadoAprovado.setStatus(StatusPagamentoPedido.APROVADO);

        OrderPointResultado resPago = new OrderPointResultado(
                "ORD-PAGO", "processed", "accredited", "PAY-1", "accredited", "REF", BigDecimal.TEN, "{}"
        );
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoJaPago))
                .thenReturn(List.of(pagPago), List.of(pagAtualizadoAprovado));
        when(mercadoPagoPointClient.consultarOrder("ORD-PAGO")).thenReturn(resPago);
        when(transicaoPagamentoPointService.aplicarResultadoOrder(
                pagPago.getId(), resPago, OrigemAuditoriaCaixa.SISTEMA, null
        )).thenReturn(pagAtualizadoAprovado);

        // 2. Pedido onde Order não existe mais no MP (404) -> força expiração local
        PagamentoPedidoEntity pagExp = new PagamentoPedidoEntity();
        pagExp.setId(UUID.randomUUID());
        pagExp.setProvedor("MERCADO_PAGO");
        pagExp.setStatus(StatusPagamentoPedido.PENDENTE_NA_POINT);
        pagExp.setOrderExternaId("ORD-EXP-404");
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoExpirar))
                .thenReturn(List.of(pagExp), List.of(pagExp));
        when(mercadoPagoPointClient.consultarOrder("ORD-EXP-404"))
                .thenThrow(new IntegracaoMercadoPagoException("Não encontrada", HttpStatus.NOT_FOUND, false));

        // 3. Pedido com falha transitória/rede na consulta ao MP -> NÃO expira localmente
        UUID pedidoRede = UUID.randomUUID();
        PagamentoPedidoEntity pagRede = new PagamentoPedidoEntity();
        pagRede.setId(UUID.randomUUID());
        pagRede.setProvedor("MERCADO_PAGO");
        pagRede.setStatus(StatusPagamentoPedido.ACAO_NECESSARIA);
        pagRede.setOrderExternaId("ORD-REDE");
        when(reservaEstoqueRepository.listarPedidosComReservasExpiradas(any()))
                .thenReturn(List.of(pedidoJaPago, pedidoExpirar, pedidoRede, pedidoOrfao));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoRede))
                .thenReturn(List.of(pagRede), List.of(pagRede));
        when(mercadoPagoPointClient.consultarOrder("ORD-REDE"))
                .thenThrow(new IntegracaoMercadoPagoException("Timeout MP", HttpStatus.GATEWAY_TIMEOUT, true));

        // 4. Pedido com reserva órfã (sem pagamento ativo) -> libera reservas
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoOrfao)).thenReturn(List.of());

        int tratados = scheduler.expirarReservasVencidas();
        assertThat(tratados).isEqualTo(4);

        verify(transicaoPagamentoPointService).expirarPagamentoPorTimeout(
                eq(pagExp.getId()), any()
        );
        verify(transicaoPagamentoPointService, never()).expirarPagamentoPorTimeout(
                eq(pagRede.getId()), any()
        );
        verify(reservaEstoqueService).liberarReservasAtivasDoPedido(
                eq(pedidoOrfao), eq(StatusReservaEstoque.EXPIRADA), any()
        );
    }

    @Test
    void naoExpiraPendenteConciliacaoSemOrderExternaNemOrderComCancelamentoRejeitado4xx() {
        ReconciliacaoPointScheduler scheduler = new ReconciliacaoPointScheduler(
                props(true), pagamentoPedidoRepository, reservaEstoqueRepository,
                mercadoPagoPointClient, transicaoPagamentoPointService, reservaEstoqueService
        );

        UUID pedidoConciliacao = UUID.randomUUID();
        PagamentoPedidoEntity pagConciliacao = new PagamentoPedidoEntity();
        pagConciliacao.setId(UUID.randomUUID());
        pagConciliacao.setProvedor("MERCADO_PAGO");
        pagConciliacao.setStatus(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
        pagConciliacao.setOrderExternaId(null);

        UUID pedidoRejeicao4xx = UUID.randomUUID();
        PagamentoPedidoEntity pagRejeicao4xx = new PagamentoPedidoEntity();
        pagRejeicao4xx.setId(UUID.randomUUID());
        pagRejeicao4xx.setProvedor("MERCADO_PAGO");
        pagRejeicao4xx.setStatus(StatusPagamentoPedido.PENDENTE_NA_POINT);
        pagRejeicao4xx.setOrderExternaId("ORD-4XX");

        OrderPointResultado resAtiva = new OrderPointResultado(
                "ORD-4XX", "created", "created", null, null, null, new BigDecimal("50.00"), "{}"
        );

        when(reservaEstoqueRepository.listarPedidosComReservasExpiradas(any()))
                .thenReturn(List.of(pedidoConciliacao, pedidoRejeicao4xx));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoConciliacao))
                .thenReturn(List.of(pagConciliacao));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoRejeicao4xx))
                .thenReturn(List.of(pagRejeicao4xx));
        when(mercadoPagoPointClient.consultarOrder("ORD-4XX")).thenReturn(resAtiva);
        when(transicaoPagamentoPointService.aplicarResultadoOrder(
                eq(pagRejeicao4xx.getId()), eq(resAtiva), eq(OrigemAuditoriaCaixa.SISTEMA), eq(null)
        )).thenReturn(pagRejeicao4xx);
        when(mercadoPagoPointClient.cancelarOrder(eq("ORD-4XX"), any()))
                .thenThrow(new IntegracaoMercadoPagoException("Cancelamento rejeitado", HttpStatus.BAD_REQUEST, false));

        scheduler.expirarReservasVencidas();

        verify(mercadoPagoPointClient, never()).cancelarOrder(eq(null), any());
        verify(transicaoPagamentoPointService, never()).expirarPagamentoPorTimeout(any(), any());
        verify(reservaEstoqueService, never()).liberarReservasAtivasDoPedido(any(), any(), any());
    }
}
