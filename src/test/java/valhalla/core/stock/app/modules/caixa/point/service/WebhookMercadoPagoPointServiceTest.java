package valhalla.core.stock.app.modules.caixa.point.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.exception.WebhookAssinaturaInvalidaException;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.point.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.entity.EventoWebhookPagamentoEntity;
import valhalla.core.stock.app.modules.caixa.point.enums.StatusProcessamentoWebhook;
import valhalla.core.stock.app.modules.caixa.point.repository.EventoWebhookPagamentoRepository;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookMercadoPagoPointServiceTest {

    @Mock private RateLimiterCaixaService rateLimiterCaixaService;
    @Mock private ValidadorAssinaturaWebhookMercadoPago validadorAssinatura;
    @Mock private EventoWebhookPagamentoRepository eventoWebhookRepository;
    @Mock private PagamentoPedidoRepository pagamentoPedidoRepository;
    @Mock private MercadoPagoPointClient mercadoPagoPointClient;
    @Mock private TransicaoPagamentoPointService transicaoPagamentoPointService;

    private WebhookMercadoPagoPointService service;

    @BeforeEach
    void configurar() {
        TransactionTemplate txTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(mock(TransactionStatus.class));
            }

            @Override
            public void executeWithoutResult(Consumer<TransactionStatus> action) {
                action.accept(mock(TransactionStatus.class));
            }
        };
        service = new WebhookMercadoPagoPointService(
                rateLimiterCaixaService,
                validadorAssinatura,
                eventoWebhookRepository,
                pagamentoPedidoRepository,
                mercadoPagoPointClient,
                transicaoPagamentoPointService,
                txTemplate
        );
        lenient().when(eventoWebhookRepository.saveAndFlush(any(EventoWebhookPagamentoEntity.class)))
                .thenAnswer(inv -> {
                    EventoWebhookPagamentoEntity e = inv.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(1L);
                    }
                    return e;
                });
    }

    @Test
    void processarWebhookValidaIdentificadorEIdempotenciaDeEventosJaConcluidos() {
        assertThatThrownBy(() -> service.processarWebhook("{}", null, null, null, null, null, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem identificador de evento");

        EventoWebhookPagamentoEntity jaProcessado = new EventoWebhookPagamentoEntity();
        jaProcessado.setId(10L);
        jaProcessado.setStatusProcessamento(StatusProcessamentoWebhook.PROCESSADO);
        when(eventoWebhookRepository.buscarPorProvedorEEventoParaAtualizacao("MERCADO_PAGO", "EVT-1"))
                .thenReturn(Optional.of(jaProcessado));

        StatusProcessamentoWebhook status = service.processarWebhook(
                "{\"id\":\"EVT-1\",\"type\":\"order\",\"data\":{\"id\":\"ORD-1\"}}",
                null, null, null, "sig", "req-1", "127.0.0.1"
        );
        assertThat(status).isEqualTo(StatusProcessamentoWebhook.PROCESSADO);
        verify(mercadoPagoPointClient, never()).consultarOrder(any());
    }

    @Test
    void processarWebhookIgnoraEventosNaoOrderOrder404EOuSemPagamentoVinculado() {
        EventoWebhookPagamentoEntity evento = new EventoWebhookPagamentoEntity();
        evento.setId(1L);
        when(eventoWebhookRepository.buscarPorProvedorEEventoParaAtualizacao(any(), any())).thenReturn(Optional.empty());
        when(eventoWebhookRepository.buscarPorIdParaAtualizacao(1L)).thenReturn(Optional.of(evento));

        // Evento de tipo diferente de order/point
        StatusProcessamentoWebhook naoOrder = service.processarWebhook(
                "{\"id\":\"EVT-2\",\"type\":\"payment\",\"action\":\"payment.updated\",\"data\":{\"id\":\"PAY-1\"}}",
                null, null, null, null, "req-2", "127.0.0.1"
        );
        assertThat(naoOrder).isEqualTo(StatusProcessamentoWebhook.IGNORADO);

        // Order não localizada (404) na API MP
        when(mercadoPagoPointClient.consultarOrder("ORD-404"))
                .thenThrow(new IntegracaoMercadoPagoException("Não encontrada", HttpStatus.NOT_FOUND, false));
        StatusProcessamentoWebhook order404 = service.processarWebhook(
                "{\"id\":\"EVT-3\",\"type\":\"order\",\"data\":{\"id\":\"ORD-404\"}}",
                null, null, null, null, "req-3", "127.0.0.1"
        );
        assertThat(order404).isEqualTo(StatusProcessamentoWebhook.IGNORADO);

        // Order localizada mas sem pagamento correspondente
        OrderPointResultado semPag = new OrderPointResultado(
                "ORD-SEM", "processed", "accredited", "PAY-1", "accredited", "REF-INEX", BigDecimal.TEN, "{}"
        );
        when(mercadoPagoPointClient.consultarOrder("ORD-SEM")).thenReturn(semPag);
        when(pagamentoPedidoRepository.findByOrderExternaId("ORD-SEM")).thenReturn(Optional.empty());
        when(pagamentoPedidoRepository.findByReferenciaExterna("REF-INEX")).thenReturn(Optional.empty());

        StatusProcessamentoWebhook semVinculo = service.processarWebhook(
                "{\"id\":\"EVT-4\",\"type\":\"order\",\"data\":{\"id\":\"ORD-SEM\"}}",
                null, null, null, null, "req-4", "127.0.0.1"
        );
        assertThat(semVinculo).isEqualTo(StatusProcessamentoWebhook.IGNORADO);
    }

    @Test
    void processarWebhookProcessaPorExternalReferenceRecuperaConcorrenciaEMarcaErro() {
        UUID pagamentoId = UUID.randomUUID();
        PagamentoPedidoEntity pag = new PagamentoPedidoEntity();
        pag.setId(pagamentoId);

        EventoWebhookPagamentoEntity existenteComErro = new EventoWebhookPagamentoEntity();
        existenteComErro.setId(30L);
        existenteComErro.setStatusProcessamento(StatusProcessamentoWebhook.ERRO);
        existenteComErro.setTentativas(1);

        // Disputa concorrente: saveAndFlush lança DataIntegrityViolationException na 1a tentativa, recupera na 2a
        when(eventoWebhookRepository.buscarPorProvedorEEventoParaAtualizacao("MERCADO_PAGO", "RES:ORD-1:order.updated"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existenteComErro));
        when(eventoWebhookRepository.saveAndFlush(any(EventoWebhookPagamentoEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_evento"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventoWebhookRepository.buscarPorIdParaAtualizacao(30L)).thenReturn(Optional.of(existenteComErro));

        OrderPointResultado resultado = new OrderPointResultado(
                "ORD-1", "processed", "accredited", "PAY-1", "accredited", "REF-1", BigDecimal.TEN, "{}"
        );
        when(mercadoPagoPointClient.consultarOrder("ORD-1")).thenReturn(resultado);
        when(pagamentoPedidoRepository.findByOrderExternaId("ORD-1")).thenReturn(Optional.empty());
        when(pagamentoPedidoRepository.findByReferenciaExterna("REF-1")).thenReturn(Optional.of(pag));

        StatusProcessamentoWebhook status = service.processarWebhook(
                "{\"action\":\"order.updated\"}",
                "ORD-1", null, null, null, null, "127.0.0.1"
        );
        assertThat(status).isEqualTo(StatusProcessamentoWebhook.PROCESSADO);
        verify(transicaoPagamentoPointService).aplicarResultadoOrder(
                pagamentoId, resultado, OrigemAuditoriaCaixa.WEBHOOK, null
        );

        // Falha de integração != 404 marca evento como ERRO e relança
        EventoWebhookPagamentoEntity novo = new EventoWebhookPagamentoEntity();
        novo.setId(40L);
        when(eventoWebhookRepository.buscarPorProvedorEEventoParaAtualizacao("MERCADO_PAGO", "EVT-ERR"))
                .thenReturn(Optional.empty());
        when(eventoWebhookRepository.findByProvedorAndRequestId("MERCADO_PAGO", "req-err"))
                .thenReturn(Optional.empty());
        when(eventoWebhookRepository.saveAndFlush(any(EventoWebhookPagamentoEntity.class)))
                .thenAnswer(inv -> {
                    EventoWebhookPagamentoEntity e = inv.getArgument(0);
                    if (e.getId() == null) e.setId(40L);
                    return e;
                });
        when(eventoWebhookRepository.buscarPorIdParaAtualizacao(40L)).thenReturn(Optional.of(novo));
        when(mercadoPagoPointClient.consultarOrder("ORD-ERR"))
                .thenThrow(new IntegracaoMercadoPagoException("Erro 502", HttpStatus.BAD_GATEWAY, true));

        assertThatThrownBy(() -> service.processarWebhook(
                "{\"id\":\"EVT-ERR\",\"data\":{\"id\":\"ORD-ERR\"}}",
                null, null, null, null, "req-err", "127.0.0.1"
        )).isInstanceOf(IntegracaoMercadoPagoException.class);
        assertThat(novo.getStatusProcessamento()).isEqualTo(StatusProcessamentoWebhook.ERRO);
    }

    @Test
    void processarWebhookBloqueiaParameterConfusionEConcorrenciaEmProcessamentoRecente() {
        assertThatThrownBy(() -> service.processarWebhook(
                "{\"id\":\"EVT-CONF-1\",\"type\":\"order\",\"data\":{\"id\":\"ORD-REAL\"}}",
                "ORD-FALSO", null, null, "sig", "req-conf-1", "127.0.0.1"
        )).isInstanceOf(WebhookAssinaturaInvalidaException.class)
                .hasMessageContaining("Divergência entre data.id da query e do corpo JSON");

        assertThatThrownBy(() -> service.processarWebhook(
                "{\"id\":\"EVT-CONF-2\",\"type\":\"order\",\"data\":{\"id\":\"ORD-A\"}}",
                "ORD-A", "ORD-B", null, "sig", "req-conf-2", "127.0.0.1"
        )).isInstanceOf(WebhookAssinaturaInvalidaException.class)
                .hasMessageContaining("Divergência entre os parâmetros data.id e id");

        EventoWebhookPagamentoEntity emProcessamentoRecente = new EventoWebhookPagamentoEntity();
        emProcessamentoRecente.setId(50L);
        emProcessamentoRecente.setStatusProcessamento(StatusProcessamentoWebhook.PROCESSANDO);
        emProcessamentoRecente.setAtualizadoEm(LocalDateTime.now());
        when(eventoWebhookRepository.buscarPorProvedorEEventoParaAtualizacao("MERCADO_PAGO", "EVT-PROC"))
                .thenReturn(Optional.of(emProcessamentoRecente));

        StatusProcessamentoWebhook statusConcorrente = service.processarWebhook(
                "{\"id\":\"EVT-PROC\",\"type\":\"order\",\"data\":{\"id\":\"ORD-PROC\"}}",
                "ORD-PROC", null, null, "sig", "req-proc", "127.0.0.1"
        );
        assertThat(statusConcorrente).isEqualTo(StatusProcessamentoWebhook.PROCESSANDO);
        verify(mercadoPagoPointClient, never()).consultarOrder("ORD-PROC");

        // Evento travado em PROCESSANDO há > 60s pode ser retomado
        emProcessamentoRecente.setAtualizadoEm(LocalDateTime.now().minusSeconds(90));
        emProcessamentoRecente.setTentativas(1);
        when(eventoWebhookRepository.buscarPorIdParaAtualizacao(50L))
                .thenReturn(Optional.of(emProcessamentoRecente));
        when(mercadoPagoPointClient.consultarOrder("ORD-PROC"))
                .thenThrow(new IntegracaoMercadoPagoException("Não encontrada", HttpStatus.NOT_FOUND, false));

        StatusProcessamentoWebhook retomado = service.processarWebhook(
                "{\"id\":\"EVT-PROC\",\"type\":\"order\",\"data\":{\"id\":\"ORD-PROC\"}}",
                "ORD-PROC", null, null, "sig", "req-proc", "127.0.0.1"
        );
        assertThat(retomado).isEqualTo(StatusProcessamentoWebhook.IGNORADO);
        assertThat(emProcessamentoRecente.getTentativas()).isEqualTo(2);
    }
}
