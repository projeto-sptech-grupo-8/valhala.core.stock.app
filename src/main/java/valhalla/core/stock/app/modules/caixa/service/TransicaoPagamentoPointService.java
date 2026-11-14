package valhalla.core.stock.app.modules.caixa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.entity.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.mapper.PedidoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.repository.PedidoRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TransicaoPagamentoPointService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PagamentoPedidoRepository pagamentoPedidoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ReservaEstoqueService reservaEstoqueService;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransicaoPagamentoPointService(
            PagamentoPedidoRepository pagamentoPedidoRepository,
            PedidoRepository pedidoRepository,
            ItemPedidoRepository itemPedidoRepository,
            ReservaEstoqueService reservaEstoqueService,
            AuditoriaCaixaService auditoriaCaixaService,
            BusinessEventLogger businessEventLogger
    ) {
        this.pagamentoPedidoRepository = pagamentoPedidoRepository;
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.reservaEstoqueService = reservaEstoqueService;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional
    public PagamentoPedidoEntity aplicarResultadoOrder(
            UUID pagamentoId,
            OrderPointResultado resultado,
            OrigemAuditoriaCaixa origemAuditoria,
            UUID atorUsuarioId
    ) {
        PagamentoPedidoEntity pagamentoBase = pagamentoPedidoRepository.findById(pagamentoId)
                .orElseThrow(() -> new EntityNotFoundException("Pagamento Point não encontrado"));

        PedidoEntity pedido = pedidoRepository.buscarParaAtualizacao(
                        pagamentoBase.getPedidoId(),
                        buscarEstabelecimentoPedido(pagamentoBase.getPedidoId())
                )
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado para atualização Point"));

        PagamentoPedidoEntity pagamento = pagamentoPedidoRepository
                .buscarParaAtualizacao(pagamentoBase.getId(), pedido.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pagamento Point não encontrado"));

        if (resultado.orderId() != null && pagamento.getOrderExternaId() == null) {
            pagamento.setOrderExternaId(resultado.orderId());
        }
        if (resultado.paymentId() != null) {
            pagamento.setPaymentExternoId(resultado.paymentId());
        }
        if (resultado.statusDetail() != null) {
            pagamento.setStatusDetalhe(resultado.statusDetail());
        }
        JsonNode rawDados = parseJsonSilencioso(resultado.rawJson());
        if (rawDados != null) {
            pagamento.setComprovanteDados(rawDados);
        }

        String statusMp = normalizarStatus(resultado.status());
        String paymentStatusMp = normalizarStatus(resultado.paymentStatus());

        if (isAprovado(statusMp, paymentStatusMp)) {
            validarConsistenciaZeroTrust(pagamento, resultado);
            if (pagamento.getStatus() == StatusPagamentoPedido.APROVADO
                    || pagamento.getStatus() == StatusPagamentoPedido.ESTORNADO) {
                return pagamento;
            }
            aplicarAprovacao(pagamento, pedido, origemAuditoria, atorUsuarioId);
            return pagamentoPedidoRepository.saveAndFlush(pagamento);
        }

        if (pagamento.getStatus() == StatusPagamentoPedido.APROVADO
                || pagamento.getStatus() == StatusPagamentoPedido.ESTORNADO) {
            // Pagamento já liquidado nunca volta para cancelado/recusado/expirado via evento atrasado.
            return pagamento;
        }

        if ("at_terminal".equals(statusMp) || "processing".equals(statusMp)) {
            if (pagamento.getStatus() == StatusPagamentoPedido.PENDENTE
                    || pagamento.getStatus() == StatusPagamentoPedido.PENDENTE_NA_POINT) {
                pagamento.setStatus(StatusPagamentoPedido.EM_TERMINAL);
            }
            return pagamentoPedidoRepository.saveAndFlush(pagamento);
        }

        if ("created".equals(statusMp) || "open".equals(statusMp)) {
            if (pagamento.getStatus() == StatusPagamentoPedido.PENDENTE) {
                pagamento.setStatus(StatusPagamentoPedido.PENDENTE_NA_POINT);
            }
            return pagamentoPedidoRepository.saveAndFlush(pagamento);
        }

        if ("action_required".equals(statusMp)) {
            if (pagamento.getStatus() != StatusPagamentoPedido.ACAO_NECESSARIA) {
                pagamento.setStatus(StatusPagamentoPedido.ACAO_NECESSARIA);
                auditarEventoPoint(
                        pagamento,
                        pedido,
                        origemAuditoria,
                        atorUsuarioId,
                        EventoAuditoriaCaixa.PAGAMENTO_POINT_FALHOU,
                        "Order Point requer ação ou conciliação (" + resultado.statusDetail() + ")"
                );
            }
            return pagamentoPedidoRepository.saveAndFlush(pagamento);
        }

        if ("failed".equals(statusMp) || "rejected".equals(paymentStatusMp)) {
            if (pagamento.getStatus() != StatusPagamentoPedido.RECUSADO) {
                pagamento.setStatus(StatusPagamentoPedido.RECUSADO);
                pagamento.setMotivoCancelamento("Pagamento recusado ou falhou na Point (" + statusMp + ")");
                PagamentoPedidoEntity salvo = pagamentoPedidoRepository.saveAndFlush(pagamento);
                liberarReservasERecalcularPedido(
                        salvo,
                        pedido,
                        StatusReservaEstoque.LIBERADA,
                        "Pagamento recusado na Point",
                        origemAuditoria,
                        atorUsuarioId,
                        EventoAuditoriaCaixa.PAGAMENTO_POINT_RECUSADO,
                        "Pagamento Point recusado"
                );
                return salvo;
            }
            return pagamento;
        }

        if ("expired".equals(statusMp)) {
            if (pagamento.getStatus() != StatusPagamentoPedido.EXPIRADO) {
                pagamento.setStatus(StatusPagamentoPedido.EXPIRADO);
                pagamento.setMotivoCancelamento("Tempo limite na Point excedido");
                PagamentoPedidoEntity salvo = pagamentoPedidoRepository.saveAndFlush(pagamento);
                liberarReservasERecalcularPedido(
                        salvo,
                        pedido,
                        StatusReservaEstoque.EXPIRADA,
                        "Cobrança Point expirada",
                        origemAuditoria,
                        atorUsuarioId,
                        EventoAuditoriaCaixa.PAGAMENTO_POINT_EXPIRADO,
                        "Cobrança na Point expirada"
                );
                return salvo;
            }
            return pagamento;
        }

        if ("canceled".equals(statusMp) || "cancelled".equals(statusMp)) {
            if (pagamento.getStatus() != StatusPagamentoPedido.CANCELADO) {
                pagamento.setStatus(StatusPagamentoPedido.CANCELADO);
                pagamento.setCanceladoEm(LocalDateTime.now());
                pagamento.setCanceladoPorUsuarioId(atorUsuarioId);
                if (pagamento.getMotivoCancelamento() == null) {
                    pagamento.setMotivoCancelamento("Cancelado na Point Smart 2");
                }
                PagamentoPedidoEntity salvo = pagamentoPedidoRepository.saveAndFlush(pagamento);
                liberarReservasERecalcularPedido(
                        salvo,
                        pedido,
                        StatusReservaEstoque.LIBERADA,
                        "Pagamento Point cancelado",
                        origemAuditoria,
                        atorUsuarioId,
                        EventoAuditoriaCaixa.PAGAMENTO_CANCELADO,
                        "Cobrança Point cancelada"
                );
                return salvo;
            }
            return pagamento;
        }

        return pagamentoPedidoRepository.saveAndFlush(pagamento);
    }

    @Transactional
    public PagamentoPedidoEntity expirarPagamentoPorTimeout(UUID pagamentoId, String motivo) {
        PagamentoPedidoEntity pagamentoBase = pagamentoPedidoRepository.findById(pagamentoId)
                .orElseThrow(() -> new EntityNotFoundException("Pagamento Point não encontrado"));

        PedidoEntity pedido = pedidoRepository.buscarParaAtualizacao(
                        pagamentoBase.getPedidoId(),
                        buscarEstabelecimentoPedido(pagamentoBase.getPedidoId())
                )
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado para expiração Point"));

        PagamentoPedidoEntity pagamento = pagamentoPedidoRepository
                .buscarParaAtualizacao(pagamentoBase.getId(), pedido.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pagamento Point não encontrado"));

        if (pagamento.getStatus() == StatusPagamentoPedido.APROVADO
                || pagamento.getStatus() == StatusPagamentoPedido.ESTORNADO
                || pagamento.getStatus() == StatusPagamentoPedido.CANCELADO
                || pagamento.getStatus() == StatusPagamentoPedido.RECUSADO
                || pagamento.getStatus() == StatusPagamentoPedido.EXPIRADO) {
            return pagamento;
        }

        String motivoFinal = (motivo == null || motivo.isBlank())
                ? "Tempo limite na Point excedido"
                : motivo.trim();

        pagamento.setStatus(StatusPagamentoPedido.EXPIRADO);
        pagamento.setMotivoCancelamento(motivoFinal);
        PagamentoPedidoEntity salvo = pagamentoPedidoRepository.saveAndFlush(pagamento);
        liberarReservasERecalcularPedido(
                salvo,
                pedido,
                StatusReservaEstoque.EXPIRADA,
                motivoFinal,
                OrigemAuditoriaCaixa.SISTEMA,
                null,
                EventoAuditoriaCaixa.PAGAMENTO_POINT_EXPIRADO,
                "Cobrança na Point expirada por timeout local"
        );
        return salvo;
    }

    private void aplicarAprovacao(
            PagamentoPedidoEntity pagamento,
            PedidoEntity pedido,
            OrigemAuditoriaCaixa origemAuditoria,
            UUID atorUsuarioId
    ) {
        LocalDateTime agora = LocalDateTime.now();
        pagamento.setStatus(StatusPagamentoPedido.APROVADO);
        pagamento.setPagoEm(agora);
        PagamentoPedidoEntity pagamentoSalvo = pagamentoPedidoRepository.saveAndFlush(pagamento);

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        auditarEventoPoint(
                pagamentoSalvo,
                pedido,
                origemAuditoria,
                atorUsuarioId,
                EventoAuditoriaCaixa.PAGAMENTO_POINT_APROVADO,
                "Pagamento Point aprovado (" + pagamentoSalvo.getForma() + ")"
        );

        BigDecimal totalAprovado = PedidoCaixaMapper.calcularTotalPagoAprovado(pagamentos);
        if (totalAprovado.compareTo(pedido.getTotal()) >= 0 && pedido.getTotal().compareTo(ZERO) > 0) {
            String origemMov = origemAuditoria == OrigemAuditoriaCaixa.WEBHOOK ? "WEBHOOK" : "SISTEMA";
            reservaEstoqueService.efetivarReservasOuBaixarEstoque(
                    pedido,
                    itens,
                    pedido.getOperadorUsuarioId(),
                    origemMov
            );
            pedido.setStatus(StatusPedidoCaixa.CONCLUIDO);
            pedido.setConcluidoEm(agora);
            pedidoRepository.saveAndFlush(pedido);
            businessEventLogger.success("cash.order.completed", "cash_order", pedido.getId());
        } else {
            pedido.setStatus(StatusPedidoCaixa.PAGAMENTO_PARCIAL);
            pedidoRepository.saveAndFlush(pedido);
        }
    }

    private void liberarReservasERecalcularPedido(
            PagamentoPedidoEntity pagamento,
            PedidoEntity pedido,
            StatusReservaEstoque statusReserva,
            String motivoReserva,
            OrigemAuditoriaCaixa origemAuditoria,
            UUID atorUsuarioId,
            String eventoAuditoria,
            String descricaoAuditoria
    ) {
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        boolean possuiOutroPointPendente = pagamentos.stream().anyMatch(p ->
                !p.getId().equals(pagamento.getId()) && (
                        p.getStatus() == StatusPagamentoPedido.PENDENTE
                                || p.getStatus() == StatusPagamentoPedido.PENDENTE_NA_POINT
                                || p.getStatus() == StatusPagamentoPedido.EM_TERMINAL
                                || p.getStatus() == StatusPagamentoPedido.CANCELAMENTO_SOLICITADO
                )
        );

        if (!possuiOutroPointPendente) {
            reservaEstoqueService.liberarReservasAtivasDoPedido(pedido.getId(), statusReserva, motivoReserva);
        }

        if (pedido.getStatus().estaAberto()) {
            BigDecimal totalAprovado = PedidoCaixaMapper.calcularTotalPagoAprovado(pagamentos);
            if (possuiOutroPointPendente) {
                pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
            } else if (totalAprovado.compareTo(ZERO) > 0) {
                pedido.setStatus(StatusPedidoCaixa.PAGAMENTO_PARCIAL);
            } else {
                pedido.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
            }
            pedidoRepository.saveAndFlush(pedido);
        }

        auditarEventoPoint(
                pagamento,
                pedido,
                origemAuditoria,
                atorUsuarioId,
                eventoAuditoria,
                descricaoAuditoria
        );
    }

    private void validarConsistenciaZeroTrust(PagamentoPedidoEntity pagamento, OrderPointResultado resultado) {
        if (resultado.externalReference() != null
                && pagamento.getReferenciaExterna() != null
                && !pagamento.getReferenciaExterna().equals(resultado.externalReference())) {
            throw new IllegalStateException(
                    "Divergência de external_reference ao aprovar pagamento Point"
            );
        }
        if (resultado.totalAmount() != null) {
            BigDecimal esperado = pagamento.getValorCobrado() != null
                    ? pagamento.getValorCobrado()
                    : pagamento.getValor();
            if (resultado.totalAmount().compareTo(esperado) != 0) {
                throw new IllegalStateException(
                        "Divergência de valor ao aprovar pagamento Point: esperado R$ "
                                + esperado + ", recebido R$ " + resultado.totalAmount()
                );
            }
        }
    }

    private UUID buscarEstabelecimentoPedido(UUID pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"))
                .getEstabelecimentoId();
    }

    private boolean isAprovado(String statusMp, String paymentStatusMp) {
        return "processed".equals(statusMp)
                || "paid".equals(statusMp)
                || "accredited".equals(paymentStatusMp)
                || "processed".equals(paymentStatusMp);
    }

    private String normalizarStatus(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private void auditarEventoPoint(
            PagamentoPedidoEntity pagamento,
            PedidoEntity pedido,
            OrigemAuditoriaCaixa origemAuditoria,
            UUID atorUsuarioId,
            String tipoEvento,
            String descricao
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pagamentoId", pagamento.getId().toString());
        snapshot.put("status", pagamento.getStatus().name());
        snapshot.put("orderExternaId", pagamento.getOrderExternaId());
        snapshot.put("paymentExternoId", pagamento.getPaymentExternoId());
        snapshot.put("terminalId", pagamento.getTerminalId());
        snapshot.put("valor", pagamento.getValor());
        snapshot.put("valorCobrado", pagamento.getValorCobrado());

        auditoriaCaixaService.registrarComOrigem(
                origemAuditoria,
                pedido.getEstabelecimentoId(),
                atorUsuarioId,
                pagamento.getSessaoCaixaId(),
                tipoEvento,
                EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                pagamento.getId().toString(),
                descricao,
                pagamento.getValor(),
                pagamento.getMotivoCancelamento(),
                pedido.getId(),
                pedido.getIdentificadorAtendimento(),
                pagamento.getId(),
                null,
                null,
                null,
                objectMapper.valueToTree(snapshot)
        );
    }

    private JsonNode parseJsonSilencioso(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }
}
