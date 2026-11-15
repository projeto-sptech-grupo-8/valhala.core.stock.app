package valhalla.core.stock.app.modules.caixa.point.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.point.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ReservaEstoqueRepository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import valhalla.core.stock.app.modules.caixa.pedido.service.ReservaEstoqueService;

@Component
public class ReconciliacaoPointScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoPointScheduler.class);

    private static final Set<StatusPagamentoPedido> STATUS_RECONCILIACAO = EnumSet.of(
            StatusPagamentoPedido.PENDENTE_NA_POINT,
            StatusPagamentoPedido.EM_TERMINAL,
            StatusPagamentoPedido.CANCELAMENTO_SOLICITADO
    );

    private static final Set<StatusPagamentoPedido> STATUS_PENDENTES_EXPIRACAO = EnumSet.of(
            StatusPagamentoPedido.PENDENTE,
            StatusPagamentoPedido.PENDENTE_NA_POINT,
            StatusPagamentoPedido.EM_TERMINAL,
            StatusPagamentoPedido.CANCELAMENTO_SOLICITADO,
            StatusPagamentoPedido.PENDENTE_CONCILIACAO
    );

    private final MercadoPagoPointProperties pointProperties;
    private final PagamentoPedidoRepository pagamentoPedidoRepository;
    private final ReservaEstoqueRepository reservaEstoqueRepository;
    private final MercadoPagoPointClient mercadoPagoPointClient;
    private final TransicaoPagamentoPointService transicaoPagamentoPointService;
    private final ReservaEstoqueService reservaEstoqueService;

    public ReconciliacaoPointScheduler(
            MercadoPagoPointProperties pointProperties,
            PagamentoPedidoRepository pagamentoPedidoRepository,
            ReservaEstoqueRepository reservaEstoqueRepository,
            MercadoPagoPointClient mercadoPagoPointClient,
            TransicaoPagamentoPointService transicaoPagamentoPointService,
            ReservaEstoqueService reservaEstoqueService
    ) {
        this.pointProperties = pointProperties;
        this.pagamentoPedidoRepository = pagamentoPedidoRepository;
        this.reservaEstoqueRepository = reservaEstoqueRepository;
        this.mercadoPagoPointClient = mercadoPagoPointClient;
        this.transicaoPagamentoPointService = transicaoPagamentoPointService;
        this.reservaEstoqueService = reservaEstoqueService;
    }

    @Scheduled(fixedDelayString = "${app.mercado-pago.point.reconciliacao-intervalo-ms:15000}")
    public void executarCicloReconciliacao() {
        if (!Boolean.TRUE.equals(pointProperties.reconciliacaoAtivaHabilitada())) {
            return;
        }
        reconciliarPagamentosPendentes();
        expirarReservasVencidas();
    }

    public int reconciliarPagamentosPendentes() {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(pointProperties.reconciliacaoAtrasoSegundos());
        List<PagamentoPedidoEntity> pendentes = pagamentoPedidoRepository
                .buscarPagamentosPointParaReconciliacao(STATUS_RECONCILIACAO, limite);

        int processados = 0;
        for (PagamentoPedidoEntity pagamento : pendentes) {
            if (pagamento.getOrderExternaId() == null) {
                continue;
            }
            try {
                OrderPointResultado resultado = mercadoPagoPointClient.consultarOrder(pagamento.getOrderExternaId());
                transicaoPagamentoPointService.aplicarResultadoOrder(
                        pagamento.getId(),
                        resultado,
                        OrigemAuditoriaCaixa.SISTEMA,
                        null
                );
                processados++;
            } catch (RuntimeException ex) {
                log.warn(
                        "Falha ao reconciliar pagamento Point id={} orderId={}: {}",
                        pagamento.getId(),
                        pagamento.getOrderExternaId(),
                        ex.getMessage()
                );
            }
        }
        return processados;
    }

    public int expirarReservasVencidas() {
        List<UUID> pedidosExpirados = reservaEstoqueRepository
                .listarPedidosComReservasExpiradas(LocalDateTime.now());

        int pedidosTratados = 0;
        for (UUID pedidoId : pedidosExpirados) {
            try {
                tratarPedidoComReservaExpirada(pedidoId);
                pedidosTratados++;
            } catch (RuntimeException ex) {
                log.warn("Falha ao expirar reservas do pedido id={}: {}", pedidoId, ex.getMessage());
            }
        }
        return pedidosTratados;
    }

    private void tratarPedidoComReservaExpirada(UUID pedidoId) {
        List<PagamentoPedidoEntity> pagamentos = pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId);
        List<PagamentoPedidoEntity> pointPendentes = pagamentos.stream()
                .filter(p -> "MERCADO_PAGO".equals(p.getProvedor()) && STATUS_PENDENTES_EXPIRACAO.contains(p.getStatus()))
                .toList();

        for (PagamentoPedidoEntity pagamento : pointPendentes) {
            if (pagamento.getOrderExternaId() != null) {
                try {
                    OrderPointResultado atualMp = mercadoPagoPointClient.consultarOrder(pagamento.getOrderExternaId());
                    PagamentoPedidoEntity atualizado = transicaoPagamentoPointService.aplicarResultadoOrder(
                            pagamento.getId(),
                            atualMp,
                            OrigemAuditoriaCaixa.SISTEMA,
                            null
                    );
                    if (!STATUS_PENDENTES_EXPIRACAO.contains(atualizado.getStatus())) {
                        continue;
                    }
                } catch (RuntimeException ex) {
                    log.warn(
                            "Erro ao consultar Order {} antes de expirar pagamento {}: {}",
                            pagamento.getOrderExternaId(),
                            pagamento.getId(),
                            ex.getMessage()
                    );
                }

                try {
                    mercadoPagoPointClient.cancelarOrder(
                            pagamento.getOrderExternaId(),
                            "EXP-" + pagamento.getId()
                    );
                } catch (IntegracaoMercadoPagoException ex) {
                    log.debug(
                            "Cancelamento remoto pré-expiração da Order {} retornou: {}",
                            pagamento.getOrderExternaId(),
                            ex.getMessage()
                    );
                }
            }

            transicaoPagamentoPointService.expirarPagamentoPorTimeout(
                    pagamento.getId(),
                    "Tempo limite de reserva/pagamento na Point Smart 2 excedido"
            );
        }

        // Caso restem reservas órfãs sem pagamento Point ativo, garante liberação.
        List<PagamentoPedidoEntity> aposAtualizacao = pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId);
        boolean aindaPendente = aposAtualizacao.stream()
                .anyMatch(p -> "MERCADO_PAGO".equals(p.getProvedor()) && STATUS_PENDENTES_EXPIRACAO.contains(p.getStatus()));
        if (!aindaPendente) {
            reservaEstoqueService.liberarReservasAtivasDoPedido(
                    pedidoId,
                    StatusReservaEstoque.EXPIRADA,
                    "Reserva de estoque expirada por timeout"
            );
        }
    }
}
