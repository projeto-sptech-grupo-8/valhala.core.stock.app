package valhalla.core.stock.app.modules.caixa.point.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.service.ReservaEstoqueService;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransicaoPagamentoPointServiceTest {

    @Mock private PagamentoPedidoRepository pagamentoPedidoRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private ReservaEstoqueService reservaEstoqueService;
    @Mock private AuditoriaCaixaService auditoriaCaixaService;
    @Mock private BusinessEventLogger businessEventLogger;

    @InjectMocks
    private TransicaoPagamentoPointService service;

    @Test
    void aplicarResultadoOrderCobreAprovacaoZeroTrustEEstadosIntermediariosEFinais() {
        UUID estabId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID pagId = UUID.randomUUID();
        UUID atorId = UUID.randomUUID();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(pedidoId);
        pedido.setEstabelecimentoId(estabId);
        pedido.setOperadorUsuarioId(atorId);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        pedido.setTotal(new BigDecimal("100.00"));

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(pagId);
        pagamento.setPedidoId(pedidoId);
        pagamento.setForma(FormaPagamentoCaixa.CREDITO);
        pagamento.setStatus(StatusPagamentoPedido.PENDENTE);
        pagamento.setReferenciaExterna("REF-01");
        pagamento.setValor(new BigDecimal("100.00"));
        pagamento.setValorCobrado(new BigDecimal("100.00"));

        when(pagamentoPedidoRepository.findById(pagId)).thenReturn(Optional.of(pagamento));
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarParaAtualizacao(pedidoId, estabId)).thenReturn(Optional.of(pedido));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId, pedidoId)).thenReturn(Optional.of(pagamento));
        lenient().when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.saveAndFlush(any(PedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId))
                .thenReturn(List.of(pagamento));

        // 1. created -> PENDENTE_NA_POINT
        service.aplicarResultadoOrder(pagId, res("created", null, "REF-01", "100.00"), OrigemAuditoriaCaixa.API, atorId);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.PENDENTE_NA_POINT);

        // 2. at_terminal -> EM_TERMINAL
        service.aplicarResultadoOrder(pagId, res("at_terminal", null, "REF-01", "100.00"), OrigemAuditoriaCaixa.WEBHOOK, null);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.EM_TERMINAL);

        // 3. action_required -> ACAO_NECESSARIA
        service.aplicarResultadoOrder(pagId, res("action_required", null, "REF-01", "100.00"), OrigemAuditoriaCaixa.WEBHOOK, null);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.ACAO_NECESSARIA);

        // 4. Zero-Trust: external_reference divergente e total_amount divergente
        assertThatThrownBy(() -> service.aplicarResultadoOrder(
                pagId, res("processed", "accredited", "REF-ERRADA", "100.00"), OrigemAuditoriaCaixa.WEBHOOK, null
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("external_reference");

        assertThatThrownBy(() -> service.aplicarResultadoOrder(
                pagId, res("processed", "accredited", "REF-01", "99.00"), OrigemAuditoriaCaixa.WEBHOOK, null
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Divergência de valor");

        // 5. Aprovação completa conclui pedido e efetiva reservas
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());
        service.aplicarResultadoOrder(pagId, res("processed", "accredited", "REF-01", "100.00"), OrigemAuditoriaCaixa.WEBHOOK, null);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.APROVADO);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedidoCaixa.CONCLUIDO);
        verify(reservaEstoqueService).efetivarReservasOuBaixarEstoque(pedido, List.of(), atorId, "WEBHOOK");

        // 6. Evento atrasado de cancelamento/recusa após APROVADO é ignorado
        service.aplicarResultadoOrder(pagId, res("canceled", null, "REF-01", "100.00"), OrigemAuditoriaCaixa.WEBHOOK, null);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.APROVADO);
    }

    @Test
    void aplicarResultadoOrderCobreRecusadoExpiradoCanceladoETimeoutLocal() {
        UUID estabId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID pagId = UUID.randomUUID();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(pedidoId);
        pedido.setEstabelecimentoId(estabId);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        pedido.setTotal(new BigDecimal("100.00"));

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(pagId);
        pagamento.setPedidoId(pedidoId);
        pagamento.setForma(FormaPagamentoCaixa.PIX);
        pagamento.setStatus(StatusPagamentoPedido.EM_TERMINAL);
        pagamento.setValor(new BigDecimal("100.00"));

        when(pagamentoPedidoRepository.findById(pagId)).thenReturn(Optional.of(pagamento));
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarParaAtualizacao(pedidoId, estabId)).thenReturn(Optional.of(pedido));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId, pedidoId)).thenReturn(Optional.of(pagamento));
        lenient().when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pedidoRepository.saveAndFlush(any(PedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId))
                .thenReturn(List.of(pagamento));

        service.aplicarResultadoOrder(pagId, res("failed", "rejected", null, null), OrigemAuditoriaCaixa.WEBHOOK, null);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.RECUSADO);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedidoCaixa.EM_ATENDIMENTO);

        pagamento.setStatus(StatusPagamentoPedido.EM_TERMINAL);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        service.aplicarResultadoOrder(pagId, res("expired", null, null, null), OrigemAuditoriaCaixa.SISTEMA, null);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.EXPIRADO);
        verify(reservaEstoqueService).liberarReservasAtivasDoPedido(eq(pedidoId), eq(StatusReservaEstoque.EXPIRADA), any());

        pagamento.setStatus(StatusPagamentoPedido.EM_TERMINAL);
        pagamento.setMotivoCancelamento(null);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        service.aplicarResultadoOrder(pagId, res("cancelled", null, null, null), OrigemAuditoriaCaixa.API, UUID.randomUUID());
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.CANCELADO);

        // Timeout local: quando já está em status final não altera; quando pendente expira
        service.expirarPagamentoPorTimeout(pagId, "Timeout");
        verify(reservaEstoqueService, org.mockito.Mockito.times(2))
                .liberarReservasAtivasDoPedido(eq(pedidoId), eq(StatusReservaEstoque.LIBERADA), any());

        pagamento.setStatus(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        service.expirarPagamentoPorTimeout(pagId, "  ");
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.EXPIRADO);
        assertThat(pagamento.getMotivoCancelamento()).isEqualTo("Tempo limite na Point excedido");
        verify(pagamentoPedidoRepository, never()).delete(any());
    }

    private OrderPointResultado res(String status, String paymentStatus, String extRef, String amount) {
        return new OrderPointResultado(
                "ORD-1",
                status,
                "detail",
                "PAY-1",
                paymentStatus,
                extRef,
                amount == null ? null : new BigDecimal(amount),
                "{\"ok\":true}"
        );
    }
}
