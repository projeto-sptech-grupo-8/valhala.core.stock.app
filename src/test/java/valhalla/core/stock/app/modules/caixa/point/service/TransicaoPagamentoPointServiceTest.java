package valhalla.core.stock.app.modules.caixa.point.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
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

        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(pagId)).thenReturn(Optional.of(pedidoId));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
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

        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(pagId)).thenReturn(Optional.of(pedidoId));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
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

    @Test
    void validarConsistenciaZeroTrustAceitaJurosDoCompradorNoMpEAjustaValorCobrado() {
        UUID estabId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID pagId = UUID.randomUUID();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(pedidoId);
        pedido.setEstabelecimentoId(estabId);
        pedido.setOperadorUsuarioId(UUID.randomUUID());
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        pedido.setTotal(new BigDecimal("100.00"));

        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(pagId);
        pagamento.setPedidoId(pedidoId);
        pagamento.setForma(FormaPagamentoCaixa.CREDITO);
        pagamento.setStatus(StatusPagamentoPedido.EM_TERMINAL);
        pagamento.setReferenciaExterna("REF-BUYER");
        pagamento.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        pagamento.setValor(new BigDecimal("100.00"));
        pagamento.setValorCobrado(new BigDecimal("100.00"));
        pagamento.setValorJuros(BigDecimal.ZERO);

        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(pagId)).thenReturn(Optional.of(pedidoId));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId, pedidoId)).thenReturn(Optional.of(pagamento));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagamento));
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.aplicarResultadoOrder(
                pagId, res("processed", "accredited", "REF-BUYER", "99.00"), OrigemAuditoriaCaixa.WEBHOOK, null
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mínimo esperado");

        service.aplicarResultadoOrder(
                pagId, res("processed", "accredited", "REF-BUYER", "108.50"), OrigemAuditoriaCaixa.WEBHOOK, null
        );
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoPedido.APROVADO);
        assertThat(pagamento.getValorCobrado()).isEqualByComparingTo("108.50");
        assertThat(pagamento.getValorJuros()).isEqualByComparingTo("8.50");
    }

    @Test
    void aplicarResultadoOrderIgnoraTransicoesIdempotentesEStatusDesconhecido() {
        UUID estabId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID atorId = UUID.randomUUID();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(pedidoId);
        pedido.setEstabelecimentoId(estabId);
        pedido.setOperadorUsuarioId(atorId);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        pedido.setTotal(new BigDecimal("100.00"));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));

        java.util.Map<StatusPagamentoPedido, String> paresIdempotentes = java.util.Map.of(
                StatusPagamentoPedido.PENDENTE_NA_POINT, "open",
                StatusPagamentoPedido.EM_TERMINAL, "processing",
                StatusPagamentoPedido.ACAO_NECESSARIA, "action_required",
                StatusPagamentoPedido.RECUSADO, "failed",
                StatusPagamentoPedido.EXPIRADO, "expired",
                StatusPagamentoPedido.CANCELADO, "canceled"
        );

        paresIdempotentes.forEach((statusAtual, statusMp) -> {
            UUID pagId = UUID.randomUUID();
            PagamentoPedidoEntity pag = pagamentoPoint(pagId, pedidoId, statusAtual, "100.00");
            when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(pagId)).thenReturn(Optional.of(pedidoId));
            when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId, pedidoId)).thenReturn(Optional.of(pag));
            lenient().when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PagamentoPedidoEntity result = service.aplicarResultadoOrder(
                    pagId, res(statusMp, null, "REF-IDEMP", "100.00"), OrigemAuditoriaCaixa.WEBHOOK, null
            );
            assertThat(result.getStatus()).isEqualTo(statusAtual);
        });

        // Status desconhecido + JSON inválido + statusDetail/paymentId nulos
        UUID idDesc = UUID.randomUUID();
        PagamentoPedidoEntity pagDesc = pagamentoPoint(idDesc, pedidoId, StatusPagamentoPedido.PENDENTE_NA_POINT, "100.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idDesc)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idDesc, pedidoId)).thenReturn(Optional.of(pagDesc));
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PagamentoPedidoEntity resDesc = service.aplicarResultadoOrder(
                idDesc,
                new OrderPointResultado("ORD-2", "pending_other", null, null, null, "REF-IDEMP", new BigDecimal("100.00"), "{invalid"),
                OrigemAuditoriaCaixa.WEBHOOK,
                null
        );
        assertThat(resDesc.getStatus()).isEqualTo(StatusPagamentoPedido.PENDENTE_NA_POINT);
        assertThat(resDesc.getComprovanteDados()).isNull();

        // ESTORNADO recebe accredited -> mantém ESTORNADO; APROVADO recebe canceled -> mantém APROVADO
        UUID idEstornado = UUID.randomUUID();
        PagamentoPedidoEntity pagEstornado = pagamentoPoint(idEstornado, pedidoId, StatusPagamentoPedido.ESTORNADO, "100.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idEstornado)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idEstornado, pedidoId)).thenReturn(Optional.of(pagEstornado));
        PagamentoPedidoEntity resEst = service.aplicarResultadoOrder(
                idEstornado,
                new OrderPointResultado("ORD-3", null, null, "PAY-3", "accredited", "REF-IDEMP", new BigDecimal("100.00"), "   "),
                OrigemAuditoriaCaixa.WEBHOOK,
                null
        );
        assertThat(resEst.getStatus()).isEqualTo(StatusPagamentoPedido.ESTORNADO);

        UUID idAprovado = UUID.randomUUID();
        PagamentoPedidoEntity pagAprovado = pagamentoPoint(idAprovado, pedidoId, StatusPagamentoPedido.APROVADO, "100.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idAprovado)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idAprovado, pedidoId)).thenReturn(Optional.of(pagAprovado));
        PagamentoPedidoEntity resAprov = service.aplicarResultadoOrder(
                idAprovado,
                res("canceled", null, "REF-IDEMP", "100.00"),
                OrigemAuditoriaCaixa.WEBHOOK,
                null
        );
        assertThat(resAprov.getStatus()).isEqualTo(StatusPagamentoPedido.APROVADO);
    }

    @Test
    void aplicarResultadoOrderCobrePagamentoParcialViaApiELiberacaoReservasConformeEstadoPedido() {
        UUID estabId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID atorId = UUID.randomUUID();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(pedidoId);
        pedido.setEstabelecimentoId(estabId);
        pedido.setOperadorUsuarioId(atorId);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        pedido.setTotal(new BigDecimal("100.00"));

        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());

        // Aprovação parcial (40.00 de 100.00) via status "paid" + OrigemAuditoriaCaixa.API -> PAGAMENTO_PARCIAL
        UUID idParc = UUID.randomUUID();
        PagamentoPedidoEntity pagParc = pagamentoPoint(idParc, pedidoId, StatusPagamentoPedido.EM_TERMINAL, "40.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idParc)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idParc, pedidoId)).thenReturn(Optional.of(pagParc));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagParc));

        PagamentoPedidoEntity aprovadoParc = service.aplicarResultadoOrder(
                idParc,
                new OrderPointResultado("ORD-P", "paid", null, null, null, "REF-IDEMP", new BigDecimal("40.00"), null),
                OrigemAuditoriaCaixa.API,
                atorId
        );
        assertThat(aprovadoParc.getStatus()).isEqualTo(StatusPagamentoPedido.APROVADO);
        assertThat(pedido.getStatus()).isEqualTo(StatusPedidoCaixa.PAGAMENTO_PARCIAL);

        // Falha quando há outro pagamento MERCADO_PAGO em PENDENTE_CONCILIACAO -> mantém AGUARDANDO_MAQUINA sem liberar reserva
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        UUID idFalhaOutro = UUID.randomUUID();
        PagamentoPedidoEntity pagFalhaOutro = pagamentoPoint(idFalhaOutro, pedidoId, StatusPagamentoPedido.EM_TERMINAL, "60.00");
        PagamentoPedidoEntity outroPoint = pagamentoPoint(UUID.randomUUID(), pedidoId, StatusPagamentoPedido.PENDENTE_CONCILIACAO, "60.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idFalhaOutro)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idFalhaOutro, pedidoId)).thenReturn(Optional.of(pagFalhaOutro));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId))
                .thenReturn(List.of(pagFalhaOutro, outroPoint));

        service.aplicarResultadoOrder(
                idFalhaOutro, res("failed", "rejected", "REF-IDEMP", "60.00"), OrigemAuditoriaCaixa.WEBHOOK, null
        );
        assertThat(pedido.getStatus()).isEqualTo(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        verify(reservaEstoqueService, never()).liberarReservasAtivasDoPedido(any(), any(), any());

        // Cancelamento sem outro Point pendente, com pagamento anterior APROVADO -> volta para PAGAMENTO_PARCIAL e libera reserva
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        UUID idCancParc = UUID.randomUUID();
        PagamentoPedidoEntity pagCancParc = pagamentoPoint(idCancParc, pedidoId, StatusPagamentoPedido.EM_TERMINAL, "60.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idCancParc)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idCancParc, pedidoId)).thenReturn(Optional.of(pagCancParc));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId))
                .thenReturn(List.of(pagParc, pagCancParc));

        service.aplicarResultadoOrder(
                idCancParc, res("canceled", "cancelled", "REF-IDEMP", "60.00"), OrigemAuditoriaCaixa.SISTEMA, null
        );
        assertThat(pedido.getStatus()).isEqualTo(StatusPedidoCaixa.PAGAMENTO_PARCIAL);
        verify(reservaEstoqueService).liberarReservasAtivasDoPedido(
                eq(pedidoId), eq(StatusReservaEstoque.LIBERADA), any()
        );

        // Pedido já CONCLUIDO não regride ao receber expiração tardia
        pedido.setStatus(StatusPedidoCaixa.CONCLUIDO);
        UUID idConc = UUID.randomUUID();
        PagamentoPedidoEntity pagConc = pagamentoPoint(idConc, pedidoId, StatusPagamentoPedido.EM_TERMINAL, "10.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idConc)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idConc, pedidoId)).thenReturn(Optional.of(pagConc));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagConc));

        service.aplicarResultadoOrder(
                idConc, res("expired", null, "REF-IDEMP", "10.00"), OrigemAuditoriaCaixa.WEBHOOK, null
        );
        assertThat(pedido.getStatus()).isEqualTo(StatusPedidoCaixa.CONCLUIDO);
    }

    @Test
    void expirarPagamentoPorTimeoutCobreEstadosTerminaisEMotivoNulo() {
        UUID estabId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(pedidoId);
        pedido.setEstabelecimentoId(estabId);
        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));

        for (StatusPagamentoPedido st : List.of(
                StatusPagamentoPedido.APROVADO,
                StatusPagamentoPedido.ESTORNADO,
                StatusPagamentoPedido.RECUSADO,
                StatusPagamentoPedido.EXPIRADO
        )) {
            UUID pagId = UUID.randomUUID();
            PagamentoPedidoEntity pag = pagamentoPoint(pagId, pedidoId, st, "50.00");
            when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(pagId)).thenReturn(Optional.of(pedidoId));
            when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId, pedidoId)).thenReturn(Optional.of(pag));

            PagamentoPedidoEntity res = service.expirarPagamentoPorTimeout(pagId, "ignorado");
            assertThat(res.getStatus()).isEqualTo(st);
        }

        UUID idExp = UUID.randomUUID();
        PagamentoPedidoEntity pagExp = pagamentoPoint(idExp, pedidoId, StatusPagamentoPedido.PENDENTE_NA_POINT, "50.00");
        when(pagamentoPedidoRepository.buscarPedidoIdPorPagamentoId(idExp)).thenReturn(Optional.of(pedidoId));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(idExp, pedidoId)).thenReturn(Optional.of(pagExp));
        when(pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)).thenReturn(List.of(pagExp));
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PagamentoPedidoEntity expirado = service.expirarPagamentoPorTimeout(idExp, null);
        assertThat(expirado.getStatus()).isEqualTo(StatusPagamentoPedido.EXPIRADO);
        assertThat(expirado.getMotivoCancelamento()).isEqualTo("Tempo limite na Point excedido");
    }

    private PagamentoPedidoEntity pagamentoPoint(
            UUID pagId, UUID pedidoId, StatusPagamentoPedido status, String valor
    ) {
        PagamentoPedidoEntity p = new PagamentoPedidoEntity();
        p.setId(pagId);
        p.setPedidoId(pedidoId);
        p.setSessaoCaixaId(UUID.randomUUID());
        p.setProvedor("MERCADO_PAGO");
        p.setForma(FormaPagamentoCaixa.PIX);
        p.setStatus(status);
        p.setReferenciaExterna("REF-IDEMP");
        p.setValor(new BigDecimal(valor));
        p.setValorCobrado(new BigDecimal(valor));
        return p;
    }

    private OrderPointResultado res(String status, String paymentStatus, String extRef, String amount) {
        return new OrderPointResultado(
                "ORD-1",
                status,
                "detail",
                "PAY-1",
                paymentStatus,
                extRef,
                amount == null ? null : new java.math.BigDecimal(amount),
                "{\"ok\":true}"
        );
    }
}
