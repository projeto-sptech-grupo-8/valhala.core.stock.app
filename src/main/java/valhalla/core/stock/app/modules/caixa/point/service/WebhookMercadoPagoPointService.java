package valhalla.core.stock.app.modules.caixa.point.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import valhalla.core.stock.app.modules.caixa.point.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.entity.EventoWebhookPagamentoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.point.enums.StatusProcessamentoWebhook;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.exception.WebhookAssinaturaInvalidaException;
import valhalla.core.stock.app.modules.caixa.point.repository.EventoWebhookPagamentoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookMercadoPagoPointService {

    private static final Logger log = LoggerFactory.getLogger(WebhookMercadoPagoPointService.class);
    private static final String PROVEDOR_MERCADO_PAGO = "MERCADO_PAGO";
    private static final long TIMEOUT_PROCESSAMENTO_SEGUNDOS = 60L;

    private final RateLimiterCaixaService rateLimiterCaixaService;
    private final ValidadorAssinaturaWebhookMercadoPago validadorAssinatura;
    private final EventoWebhookPagamentoRepository eventoWebhookRepository;
    private final PagamentoPedidoRepository pagamentoPedidoRepository;
    private final MercadoPagoPointClient mercadoPagoPointClient;
    private final TransicaoPagamentoPointService transicaoPagamentoPointService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookMercadoPagoPointService(
            RateLimiterCaixaService rateLimiterCaixaService,
            ValidadorAssinaturaWebhookMercadoPago validadorAssinatura,
            EventoWebhookPagamentoRepository eventoWebhookRepository,
            PagamentoPedidoRepository pagamentoPedidoRepository,
            MercadoPagoPointClient mercadoPagoPointClient,
            TransicaoPagamentoPointService transicaoPagamentoPointService,
            TransactionTemplate transactionTemplate
    ) {
        this.rateLimiterCaixaService = rateLimiterCaixaService;
        this.validadorAssinatura = validadorAssinatura;
        this.eventoWebhookRepository = eventoWebhookRepository;
        this.pagamentoPedidoRepository = pagamentoPedidoRepository;
        this.mercadoPagoPointClient = mercadoPagoPointClient;
        this.transicaoPagamentoPointService = transicaoPagamentoPointService;
        this.transactionTemplate = transactionTemplate;
    }

    public StatusProcessamentoWebhook processarWebhook(
            String rawPayload,
            String dataIdQuery,
            String idQuery,
            String typeQuery,
            String xSignature,
            String xRequestId,
            String origemIp
    ) {
        rateLimiterCaixaService.verificarLimiteWebhook(origemIp);

        JsonNode payloadNode = parsearPayloadSeguro(rawPayload);
        String tipoEvento = primeiroNaoVazio(extrairTexto(payloadNode, "type"), normalizar(typeQuery));
        String acao = extrairTexto(payloadNode, "action");
        String dataIdQueryNorm = normalizar(dataIdQuery);
        String idQueryNorm = normalizar(idQuery);
        if (dataIdQueryNorm != null && idQueryNorm != null && !dataIdQueryNorm.equalsIgnoreCase(idQueryNorm)) {
            throw new WebhookAssinaturaInvalidaException(
                    "Divergência entre os parâmetros data.id e id na query do webhook"
            );
        }

        String dataIdParamQuery = primeiroNaoVazio(dataIdQueryNorm, idQueryNorm);
        String dataIdJson = extrairTexto(payloadNode.path("data"), "id");
        if (dataIdParamQuery != null && dataIdJson != null && !dataIdParamQuery.equalsIgnoreCase(dataIdJson)) {
            throw new WebhookAssinaturaInvalidaException(
                    "Divergência entre data.id da query e do corpo JSON do webhook"
            );
        }

        String dataIdValidado = primeiroNaoVazio(dataIdParamQuery, dataIdJson);
        validadorAssinatura.validar(xSignature, xRequestId, dataIdValidado);

        String recursoExternoId = dataIdValidado;
        String requestIdNormalizado = normalizar(xRequestId);
        String eventoExternoId = primeiroNaoVazio(
                extrairTexto(payloadNode, "id"),
                requestIdNormalizado != null ? "REQ:" + requestIdNormalizado : null,
                recursoExternoId != null ? "RES:" + recursoExternoId + ":" + primeiroNaoVazio(acao, tipoEvento, "event") : null
        );

        if (eventoExternoId == null) {
            throw new IllegalArgumentException("Payload de webhook do Mercado Pago sem identificador de evento");
        }

        ClaimWebhookResult claim = registrarOuRecuperarEvento(
                eventoExternoId,
                tipoEvento,
                acao,
                recursoExternoId,
                requestIdNormalizado,
                normalizar(xSignature),
                payloadNode
        );

        if (!claim.deveProcessar()) {
            return claim.statusAtual();
        }

        Long eventoId = claim.eventoId();
        try {
            if (!ehEventoOrder(tipoEvento, acao) || recursoExternoId == null) {
                atualizarStatusWebhook(eventoId, StatusProcessamentoWebhook.IGNORADO, null, null);
                return StatusProcessamentoWebhook.IGNORADO;
            }

            OrderPointResultado resultadoOrder;
            try {
                resultadoOrder = mercadoPagoPointClient.consultarOrder(recursoExternoId);
            } catch (IntegracaoMercadoPagoException ex) {
                if (ex.getHttpStatus() == HttpStatus.NOT_FOUND) {
                    atualizarStatusWebhook(
                            eventoId,
                            StatusProcessamentoWebhook.IGNORADO,
                            null,
                            "Order não localizada na API do Mercado Pago: " + recursoExternoId
                    );
                    return StatusProcessamentoWebhook.IGNORADO;
                }
                throw ex;
            }

            Optional<PagamentoPedidoEntity> pagamentoOpt = pagamentoPedidoRepository
                    .findByOrderExternaId(resultadoOrder.orderId());
            if (pagamentoOpt.isEmpty() && resultadoOrder.externalReference() != null) {
                pagamentoOpt = pagamentoPedidoRepository
                        .findByReferenciaExterna(resultadoOrder.externalReference());
            }

            if (pagamentoOpt.isEmpty()) {
                atualizarStatusWebhook(
                        eventoId,
                        StatusProcessamentoWebhook.IGNORADO,
                        null,
                        "Nenhum pagamento vinculado à Order " + resultadoOrder.orderId()
                );
                return StatusProcessamentoWebhook.IGNORADO;
            }

            UUID pagamentoId = pagamentoOpt.get().getId();
            transicaoPagamentoPointService.aplicarResultadoOrder(
                    pagamentoId,
                    resultadoOrder,
                    OrigemAuditoriaCaixa.WEBHOOK,
                    null
            );

            atualizarStatusWebhook(eventoId, StatusProcessamentoWebhook.PROCESSADO, pagamentoId, null);
            return StatusProcessamentoWebhook.PROCESSADO;
        } catch (RuntimeException ex) {
            log.warn("Falha ao processar webhook Mercado Pago eventoId={}: {}", eventoExternoId, ex.getMessage());
            atualizarStatusWebhook(eventoId, StatusProcessamentoWebhook.ERRO, null, ex.getMessage());
            throw ex;
        }
    }

    private ClaimWebhookResult registrarOuRecuperarEvento(
            String eventoExternoId,
            String tipoEvento,
            String acao,
            String recursoExternoId,
            String requestId,
            String assinatura,
            JsonNode payloadNode
    ) {
        try {
            ClaimWebhookResult tentativa = transactionTemplate.execute(status ->
                    executarClaimEmTransacao(
                            eventoExternoId,
                            tipoEvento,
                            acao,
                            recursoExternoId,
                            requestId,
                            assinatura,
                            payloadNode
                    )
            );
            return tentativa != null
                    ? tentativa
                    : new ClaimWebhookResult(null, false, StatusProcessamentoWebhook.IGNORADO);
        } catch (DataIntegrityViolationException ex) {
            // Disputa concorrente no mesmo evento_externo_id ou request_id: recupera registro vencedor em nova TX.
            ClaimWebhookResult recuperado = transactionTemplate.execute(status ->
                    recuperarExistenteParaReprocessamento(eventoExternoId, requestId)
            );
            if (recuperado != null) {
                return recuperado;
            }
            throw ex;
        }
    }

    private ClaimWebhookResult executarClaimEmTransacao(
            String eventoExternoId,
            String tipoEvento,
            String acao,
            String recursoExternoId,
            String requestId,
            String assinatura,
            JsonNode payloadNode
    ) {
        ClaimWebhookResult existente = recuperarExistenteParaReprocessamento(eventoExternoId, requestId);
        if (existente != null) {
            return existente;
        }

        EventoWebhookPagamentoEntity novo = new EventoWebhookPagamentoEntity();
        novo.setProvedor(PROVEDOR_MERCADO_PAGO);
        novo.setEventoExternoId(eventoExternoId);
        novo.setTipoEvento(truncar(tipoEvento, 40));
        novo.setAcao(truncar(acao, 60));
        novo.setRecursoTipo("order");
        novo.setRecursoExternoId(truncar(recursoExternoId, 100));
        novo.setRequestId(truncar(requestId, 100));
        novo.setAssinatura(truncar(assinatura, 255));
        novo.setPayload(payloadNode);
        novo.setStatusProcessamento(StatusProcessamentoWebhook.PROCESSANDO);
        novo.setTentativas(1);

        EventoWebhookPagamentoEntity salvo = eventoWebhookRepository.saveAndFlush(novo);
        return new ClaimWebhookResult(salvo.getId(), true, StatusProcessamentoWebhook.PROCESSANDO);
    }

    private ClaimWebhookResult recuperarExistenteParaReprocessamento(String eventoExternoId, String requestId) {
        Optional<EventoWebhookPagamentoEntity> opt = eventoWebhookRepository
                .buscarPorProvedorEEventoParaAtualizacao(PROVEDOR_MERCADO_PAGO, eventoExternoId);
        if (opt.isEmpty() && requestId != null) {
            opt = eventoWebhookRepository.findByProvedorAndRequestId(PROVEDOR_MERCADO_PAGO, requestId)
                    .flatMap(e -> eventoWebhookRepository.buscarPorIdParaAtualizacao(e.getId()));
        }
        if (opt.isEmpty()) {
            return null;
        }

        EventoWebhookPagamentoEntity existente = opt.get();
        if (existente.getStatusProcessamento() == StatusProcessamentoWebhook.PROCESSADO
                || existente.getStatusProcessamento() == StatusProcessamentoWebhook.IGNORADO) {
            return new ClaimWebhookResult(existente.getId(), false, existente.getStatusProcessamento());
        }
        if (existente.getStatusProcessamento() == StatusProcessamentoWebhook.PROCESSANDO) {
            LocalDateTime referencia = existente.getAtualizadoEm() != null
                    ? existente.getAtualizadoEm()
                    : existente.getRecebidoEm();
            if (referencia != null
                    && referencia.isAfter(LocalDateTime.now().minusSeconds(TIMEOUT_PROCESSAMENTO_SEGUNDOS))) {
                return new ClaimWebhookResult(existente.getId(), false, StatusProcessamentoWebhook.PROCESSANDO);
            }
        }

        existente.setStatusProcessamento(StatusProcessamentoWebhook.PROCESSANDO);
        existente.setTentativas(existente.getTentativas() + 1);
        existente.setAtualizadoEm(LocalDateTime.now());
        existente.setErro(null);
        eventoWebhookRepository.saveAndFlush(existente);
        return new ClaimWebhookResult(existente.getId(), true, StatusProcessamentoWebhook.PROCESSANDO);
    }

    private void atualizarStatusWebhook(
            Long eventoId,
            StatusProcessamentoWebhook statusDestino,
            UUID pagamentoPedidoId,
            String erro
    ) {
        if (eventoId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            EventoWebhookPagamentoEntity entity = eventoWebhookRepository.buscarPorIdParaAtualizacao(eventoId)
                    .orElse(null);
            if (entity == null) {
                return;
            }
            entity.setStatusProcessamento(statusDestino);
            if (pagamentoPedidoId != null) {
                entity.setPagamentoPedidoId(pagamentoPedidoId);
            }
            if (statusDestino == StatusProcessamentoWebhook.PROCESSADO
                    || statusDestino == StatusProcessamentoWebhook.IGNORADO) {
                entity.setProcessadoEm(LocalDateTime.now());
            }
            entity.setErro(erro);
            eventoWebhookRepository.saveAndFlush(entity);
        });
    }

    private JsonNode parsearPayloadSeguro(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            return node == null || node.isNull() ? objectMapper.createObjectNode() : node;
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean ehEventoOrder(String tipoEvento, String acao) {
        String tipoNorm = tipoEvento == null ? "" : tipoEvento.toLowerCase(Locale.ROOT);
        String acaoNorm = acao == null ? "" : acao.toLowerCase(Locale.ROOT);
        if (tipoNorm.isBlank() && acaoNorm.isBlank()) {
            return true;
        }
        return tipoNorm.contains("order")
                || acaoNorm.startsWith("order.")
                || tipoNorm.contains("point");
    }

    private String extrairTexto(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String texto = node.asText();
        return normalizar(texto);
    }

    private String primeiroNaoVazio(String... opcoes) {
        for (String opcao : opcoes) {
            String normalizado = normalizar(opcao);
            if (normalizado != null) {
                return normalizado;
            }
        }
        return null;
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String trimmed = texto.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncar(String texto, int max) {
        if (texto == null || texto.length() <= max) {
            return texto;
        }
        return texto.substring(0, max);
    }

    private record ClaimWebhookResult(
            Long eventoId,
            boolean deveProcessar,
            StatusProcessamentoWebhook statusAtual
    ) {
    }
}
