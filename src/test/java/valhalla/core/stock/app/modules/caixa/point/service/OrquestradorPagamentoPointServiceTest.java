package valhalla.core.stock.app.modules.caixa.point.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.CaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TerminalCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TerminalCaixaRepository;
import valhalla.core.stock.app.modules.caixa.exception.CaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.TerminalOcupadoPointException;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.service.ReservaEstoqueService;
import valhalla.core.stock.app.modules.caixa.pedido.strategy.ProcessadorPagamentoPoint;
import valhalla.core.stock.app.modules.caixa.point.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.point.config.ModoMercadoPagoPoint;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.enums.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrquestradorPagamentoPointServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;
    @Mock private PagamentoPedidoRepository pagamentoPedidoRepository;
    @Mock private TerminalCaixaRepository terminalCaixaRepository;
    @Mock private SessaoCaixaRepository sessaoCaixaRepository;
    @Mock private ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProcessadorPagamentoPoint processadorPagamentoPoint;
    @Mock private ReservaEstoqueService reservaEstoqueService;
    @Mock private TransicaoPagamentoPointService transicaoPagamentoPointService;
    @Mock private MercadoPagoPointClient mercadoPagoPointClient;
    @Mock private RateLimiterCaixaService rateLimiterCaixaService;
    @Mock private AuditoriaCaixaService auditoriaCaixaService;
    @Mock private ContextoCaixa contextoCaixa;

    private OrquestradorPagamentoPointService service;

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
        MercadoPagoPointProperties props = new MercadoPagoPointProperties(
                ModoMercadoPagoPoint.MOCK, null, null, true, 300L, null,
                3000L, 8000L, "PT5M", 300L, 20L, 15000L, true, 300, 30
        );
        service = new OrquestradorPagamentoPointService(
                txTemplate,
                pedidoRepository,
                itemPedidoRepository,
                pagamentoPedidoRepository,
                terminalCaixaRepository,
                sessaoCaixaRepository,
                configuracaoCaixaRepository,
                userRepository,
                processadorPagamentoPoint,
                reservaEstoqueService,
                transicaoPagamentoPointService,
                mercadoPagoPointClient,
                props,
                rateLimiterCaixaService,
                auditoriaCaixaService,
                contextoCaixa
        );
    }

    @Test
    void resolveInstallmentsCostConformePoliticaEJurosCalculados() {
        PagamentoPedidoEntity absorvida = new PagamentoPedidoEntity();
        absorvida.setPoliticaJuros(PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA);
        absorvida.setValorJuros(BigDecimal.ZERO);
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(absorvida)).isEqualTo("seller");

        PagamentoPedidoEntity repassadaComJurosNoValorCobrado = new PagamentoPedidoEntity();
        repassadaComJurosNoValorCobrado.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        repassadaComJurosNoValorCobrado.setValorJuros(new BigDecimal("5.24"));
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(repassadaComJurosNoValorCobrado))
                .isEqualTo("seller");

        PagamentoPedidoEntity repassadaSemTaxaAtiva = new PagamentoPedidoEntity();
        repassadaSemTaxaAtiva.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        repassadaSemTaxaAtiva.setValorJuros(BigDecimal.ZERO);
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(repassadaSemTaxaAtiva))
                .isEqualTo("buyer");

        PagamentoPedidoEntity repassadaComJurosNulo = new PagamentoPedidoEntity();
        repassadaComJurosNulo.setPoliticaJuros(PoliticaJurosCaixa.REPASSADA_AO_CLIENTE);
        repassadaComJurosNulo.setValorJuros(null);
        assertThat(OrquestradorPagamentoPointService.resolverInstallmentsCost(repassadaComJurosNulo))
                .isEqualTo("buyer");
    }

    @Test
    void iniciarPagamentoPointValidaPreCondicoesETrataFalhaTransitoriaEDefinitiva() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID terminalCaixaId = UUID.randomUUID();

        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);

        PedidoEntity pedido = pedido(pedidoId, estabId, userId, new BigDecimal("100.00"));
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));

        // Pedido sem itens -> erro
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), null, terminalCaixaId)
        )).isInstanceOf(PedidoEstadoInvalidoException.class);

        ItemPedidoEntity item = new ItemPedidoEntity();
        item.setId(1L);
        when(itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedidoId)).thenReturn(List.of(item));

        // Idempotência existente retorna detalhe direto sem chamar MP
        PagamentoPedidoEntity existente = pagamento(UUID.randomUUID(), pedidoId, StatusPagamentoPedido.PENDENTE_NA_POINT);
        when(pagamentoPedidoRepository.findByPedidoIdAndChaveIdempotencia(pedidoId, "idem-ok"))
                .thenReturn(Optional.of(existente));
        PedidoCaixaDetalheResponseDto detalheIdemp = service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), "idem-ok", terminalCaixaId)
        );
        assertThat(detalheIdemp.id()).isEqualTo(pedidoId);
        verify(mercadoPagoPointClient, never()).criarOrderPoint(any());

        // terminalCaixaId nulo
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminalCaixaId");

        // Terminal não encontrado, outro estabelecimento, inativo e ocupado
        when(terminalCaixaRepository.buscarParaAtualizacao(terminalCaixaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), null, terminalCaixaId)
        )).isInstanceOf(EntityNotFoundException.class);

        TerminalCaixaEntity terminalOutro = terminal(terminalCaixaId, UUID.randomUUID(), true, true);
        when(terminalCaixaRepository.buscarParaAtualizacao(terminalCaixaId)).thenReturn(Optional.of(terminalOutro));
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), null, terminalCaixaId)
        )).isInstanceOf(AccessDeniedException.class);

        TerminalCaixaEntity terminalInativo = terminal(terminalCaixaId, estabId, false, true);
        when(terminalCaixaRepository.buscarParaAtualizacao(terminalCaixaId)).thenReturn(Optional.of(terminalInativo));
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), null, terminalCaixaId)
        )).isInstanceOf(CaixaEstadoInvalidoException.class);

        TerminalCaixaEntity terminalAtivo = terminal(terminalCaixaId, estabId, true, true);
        when(terminalCaixaRepository.buscarParaAtualizacao(terminalCaixaId)).thenReturn(Optional.of(terminalAtivo));
        when(pagamentoPedidoRepository.existePagamentoAtivoNoTerminal(eq("TERM-01"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), null, terminalCaixaId)
        )).isInstanceOf(TerminalOcupadoPointException.class);

        when(pagamentoPedidoRepository.existePagamentoAtivoNoTerminal(eq("TERM-01"), any())).thenReturn(false);

        // Sem sessão aberta -> SessaoCaixaEstadoInvalidoException
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("50.00"), null, terminalCaixaId)
        )).isInstanceOf(SessaoCaixaEstadoInvalidoException.class);

        SessaoCaixaEntity sessao = new SessaoCaixaEntity();
        sessao.setId(UUID.randomUUID());
        sessao.setStatus(StatusSessaoCaixa.ABERTA);
        when(sessaoCaixaRepository.findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                estabId, userId, StatusSessaoCaixa.ABERTA
        )).thenReturn(Optional.of(sessao));
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabId)).thenReturn(Optional.empty());

        // Valor acima do saldo disponível
        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("150.00"), null, terminalCaixaId)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede o saldo em aberto");

        // Falha transitória na criação da Order -> PENDENTE_CONCILIACAO
        UUID pagId1 = UUID.randomUUID();
        PagamentoPedidoEntity pag1 = pagamento(pagId1, pedidoId, StatusPagamentoPedido.PENDENTE);
        when(processadorPagamentoPoint.prepararPagamentoPendente(any(), any(), any(), any(), any(), any()))
                .thenReturn(pag1);
        when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(pedidoRepository.saveAndFlush(any(PedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId1, pedidoId)).thenReturn(Optional.of(pag1));
        when(mercadoPagoPointClient.criarOrderPoint(any()))
                .thenThrow(
                        new IntegracaoMercadoPagoException("Timeout", HttpStatus.GATEWAY_TIMEOUT, true),
                        new IntegracaoMercadoPagoException("Bad request", HttpStatus.BAD_REQUEST, false)
                );

        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("100.00"), null, terminalCaixaId)
        )).isInstanceOf(IntegracaoMercadoPagoException.class);
        assertThat(pag1.getStatus()).isEqualTo(StatusPagamentoPedido.PENDENTE_CONCILIACAO);

        // Falha definitiva na criação da Order -> RECUSADO e libera reservas
        UUID pagId2 = UUID.randomUUID();
        PagamentoPedidoEntity pag2 = pagamento(pagId2, pedidoId, StatusPagamentoPedido.PENDENTE);
        when(processadorPagamentoPoint.prepararPagamentoPendente(any(), any(), any(), any(), any(), any()))
                .thenReturn(pag2);
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId2, pedidoId)).thenReturn(Optional.of(pag2));

        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("100.00"), null, terminalCaixaId)
        )).isInstanceOf(IntegracaoMercadoPagoException.class);
        assertThat(pag2.getStatus()).isEqualTo(StatusPagamentoPedido.RECUSADO);
        verify(reservaEstoqueService).liberarReservasAtivasDoPedido(eq(pedidoId), eq(StatusReservaEstoque.LIBERADA), any());

        // Falha inesperada (RuntimeException genérica) na Fase 2 também compensa para PENDENTE_CONCILIACAO
        UUID pagId3 = UUID.randomUUID();
        PagamentoPedidoEntity pag3 = pagamento(pagId3, pedidoId, StatusPagamentoPedido.PENDENTE);
        when(processadorPagamentoPoint.prepararPagamentoPendente(any(), any(), any(), any(), any(), any()))
                .thenReturn(pag3);
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagId3, pedidoId)).thenReturn(Optional.of(pag3));
        doThrow(new IllegalStateException("Falha inesperada de serialização"))
                .when(mercadoPagoPointClient).criarOrderPoint(any());

        assertThatThrownBy(() -> service.iniciarPagamentoPoint(
                pedidoId, req(new BigDecimal("100.00"), null, terminalCaixaId)
        )).isInstanceOf(IllegalStateException.class);
        assertThat(pag3.getStatus()).isEqualTo(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
        assertThat(pag3.getStatusDetalhe()).isEqualTo("ERRO_INESPERADO_ENVIO_MP");
    }

    @Test
    void cancelarESincronizarCobrancaPointCobreLocalERemoto() {
        UUID estabId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID pedidoId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabId);
        when(contextoCaixa.usuarioId()).thenReturn(userId);

        PedidoEntity pedido = pedido(pedidoId, estabId, userId, new BigDecimal("50.00"));
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.buscarPorIdParaAtualizacao(pedidoId)).thenReturn(Optional.of(pedido));
        lenient().when(pedidoRepository.saveAndFlush(any(PedidoEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(pagamentoPedidoRepository.saveAndFlush(any(PagamentoPedidoEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Provedor diferente -> erro
        PagamentoPedidoEntity interno = pagamento(pagamentoId, pedidoId, StatusPagamentoPedido.PENDENTE_NA_POINT);
        interno.setProvedor("INTERNO");
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(interno));
        assertThatThrownBy(() -> service.cancelarCobrancaPoint(pedidoId, pagamentoId))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        // Já cancelado -> idempotente
        PagamentoPedidoEntity jaCancelado = pagamento(pagamentoId, pedidoId, StatusPagamentoPedido.CANCELADO);
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(jaCancelado));
        assertThat(service.cancelarCobrancaPoint(pedidoId, pagamentoId).id()).isEqualTo(pedidoId);

        // Status inválido (APROVADO) -> erro
        PagamentoPedidoEntity aprovado = pagamento(pagamentoId, pedidoId, StatusPagamentoPedido.APROVADO);
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(aprovado));
        assertThatThrownBy(() -> service.cancelarCobrancaPoint(pedidoId, pagamentoId))
                .isInstanceOf(PedidoEstadoInvalidoException.class);

        // Sem orderExternaId -> cancelamento local
        PagamentoPedidoEntity semOrder = pagamento(pagamentoId, pedidoId, StatusPagamentoPedido.PENDENTE_NA_POINT);
        semOrder.setOrderExternaId(null);
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(semOrder));
        service.cancelarCobrancaPoint(pedidoId, pagamentoId);
        assertThat(semOrder.getStatus()).isEqualTo(StatusPagamentoPedido.CANCELADO);
        verify(reservaEstoqueService).liberarReservasAtivasDoPedido(eq(pedidoId), eq(StatusReservaEstoque.LIBERADA), any());

        // Com orderExternaId e erro não transitório no cancelamento -> consultaOrder fallback
        PagamentoPedidoEntity comOrder = pagamento(pagamentoId, pedidoId, StatusPagamentoPedido.EM_TERMINAL);
        comOrder.setOrderExternaId("ORD-123");
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(comOrder));
        when(mercadoPagoPointClient.cancelarOrder("ORD-123", pagamentoId + "-cancel"))
                .thenThrow(
                        new IntegracaoMercadoPagoException("Já paga", HttpStatus.CONFLICT, false),
                        new IntegracaoMercadoPagoException("Timeout", HttpStatus.GATEWAY_TIMEOUT, true)
                );
        OrderPointResultado resultadoConsulta = new OrderPointResultado(
                "ORD-123", "processed", "accredited", "PAY-1", "accredited", "REF-1", new BigDecimal("50.00"), "{}"
        );
        when(mercadoPagoPointClient.consultarOrder("ORD-123")).thenReturn(resultadoConsulta);
        when(transicaoPagamentoPointService.aplicarResultadoOrder(
                pagamentoId, resultadoConsulta, OrigemAuditoriaCaixa.API, userId
        )).thenReturn(comOrder);

        service.cancelarCobrancaPoint(pedidoId, pagamentoId);
        verify(transicaoPagamentoPointService).aplicarResultadoOrder(
                pagamentoId, resultadoConsulta, OrigemAuditoriaCaixa.API, userId
        );

        // Erro transitório ao cancelar -> propaga exceção
        comOrder.setStatus(StatusPagamentoPedido.EM_TERMINAL);
        assertThatThrownBy(() -> service.cancelarCobrancaPoint(pedidoId, pagamentoId))
                .isInstanceOf(IntegracaoMercadoPagoException.class);

        // Sincronização sem orderExternaId -> erro; com orderExternaId -> sucesso
        assertThatThrownBy(() -> {
            comOrder.setOrderExternaId("  ");
            service.sincronizarCobrancaPoint(pedidoId, pagamentoId);
        }).isInstanceOf(PedidoEstadoInvalidoException.class);

        comOrder.setOrderExternaId("ORD-123");
        service.sincronizarCobrancaPoint(pedidoId, pagamentoId);
        verify(mercadoPagoPointClient, org.mockito.Mockito.times(2)).consultarOrder("ORD-123");

        // Cancelamento de PENDENTE local e ACAO_NECESSARIA remoto
        PagamentoPedidoEntity pendenteLocal = pagamento(pagamentoId, pedidoId, StatusPagamentoPedido.PENDENTE);
        pendenteLocal.setOrderExternaId(null);
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(pendenteLocal));
        service.cancelarCobrancaPoint(pedidoId, pagamentoId);
        assertThat(pendenteLocal.getStatus()).isEqualTo(StatusPagamentoPedido.CANCELADO);

        PagamentoPedidoEntity acaoNecessaria = pagamento(pagamentoId, pedidoId, StatusPagamentoPedido.ACAO_NECESSARIA);
        acaoNecessaria.setOrderExternaId("ORD-ACAO");
        when(pagamentoPedidoRepository.buscarParaAtualizacao(pagamentoId, pedidoId)).thenReturn(Optional.of(acaoNecessaria));
        OrderPointResultado resCanceladoAcao = new OrderPointResultado(
                "ORD-ACAO", "canceled", "canceled", null, "canceled", "REF-A", new BigDecimal("50.00"), "{}"
        );
        when(mercadoPagoPointClient.cancelarOrder("ORD-ACAO", pagamentoId + "-cancel"))
                .thenReturn(resCanceladoAcao);
        service.cancelarCobrancaPoint(pedidoId, pagamentoId);
        verify(transicaoPagamentoPointService).aplicarResultadoOrder(
                pagamentoId, resCanceladoAcao, OrigemAuditoriaCaixa.API, userId
        );
    }

    @Test
    void estornarOrderExternaTrataSucessoRecuperacaoDeJaEstornadoEFalhas() {
        UUID pagId = UUID.randomUUID();
        OrderPointResultado refunded = new OrderPointResultado(
                "ORD-REF", "refunded", "refunded", "PAY-1", "refunded", "REF-1", new BigDecimal("50.00"), "{}"
        );
        when(mercadoPagoPointClient.estornarOrder("ORD-REF", pagId + "-refund"))
                .thenReturn(refunded)
                .thenThrow(new IntegracaoMercadoPagoException("Já estornado", HttpStatus.CONFLICT, false))
                .thenThrow(new IntegracaoMercadoPagoException("Bad request", HttpStatus.BAD_REQUEST, false))
                .thenThrow(new IntegracaoMercadoPagoException("Timeout", HttpStatus.GATEWAY_TIMEOUT, true));

        assertThat(service.estornarOrderExterna("ORD-REF", pagId)).isEqualTo(refunded);

        // Erro não transitório + reconsulta confirma refunded -> retorna sucesso idempotente
        when(mercadoPagoPointClient.consultarOrder("ORD-REF"))
                .thenReturn(refunded)
                .thenReturn(new OrderPointResultado(
                        "ORD-REF", "processed", "accredited", "PAY-1", "accredited", "REF-1", new BigDecimal("50.00"), "{}"
                ));
        assertThat(service.estornarOrderExterna("ORD-REF", pagId)).isEqualTo(refunded);

        // Erro não transitório quando não foi estornado -> propaga exceção
        assertThatThrownBy(() -> service.estornarOrderExterna("ORD-REF", pagId))
                .isInstanceOf(IntegracaoMercadoPagoException.class);

        // Erro transitório -> propaga direto
        assertThatThrownBy(() -> service.estornarOrderExterna("ORD-REF", pagId))
                .isInstanceOf(IntegracaoMercadoPagoException.class);
    }

    private RegistrarPagamentoPedidoRequestDto req(BigDecimal valor, String idem, UUID terminalId) {
        return new RegistrarPagamentoPedidoRequestDto(
                FormaPagamentoCaixa.PIX, valor, 1, null, idem, null, terminalId, false
        );
    }

    private PedidoEntity pedido(UUID pedidoId, UUID estabId, UUID userId, BigDecimal total) {
        PedidoEntity p = new PedidoEntity();
        p.setId(pedidoId);
        p.setEstabelecimentoId(estabId);
        p.setOperadorUsuarioId(userId);
        p.setNumero(1L);
        p.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        p.setCanal(CanalPedidoCaixa.BALCAO);
        p.setStatusDesconto(StatusDescontoPedido.NENHUM);
        p.setSubtotal(total);
        p.setDesconto(BigDecimal.ZERO);
        p.setTotal(total);
        return p;
    }

    private TerminalCaixaEntity terminal(UUID id, UUID estabId, boolean ativo, boolean caixaAtivo) {
        CaixaEntity caixa = new CaixaEntity();
        caixa.setId(UUID.randomUUID());
        caixa.setEstabelecimentoId(estabId);
        caixa.setAtivo(caixaAtivo);

        TerminalCaixaEntity t = new TerminalCaixaEntity();
        t.setId(id);
        t.setCaixa(caixa);
        t.setTerminalId("TERM-01");
        t.setNome("Maquininha 1");
        t.setAtivo(ativo);
        return t;
    }

    private PagamentoPedidoEntity pagamento(UUID id, UUID pedidoId, StatusPagamentoPedido status) {
        PagamentoPedidoEntity p = new PagamentoPedidoEntity();
        p.setId(id);
        p.setPedidoId(pedidoId);
        p.setProvedor("MERCADO_PAGO");
        p.setForma(FormaPagamentoCaixa.PIX);
        p.setStatus(status);
        p.setTerminalId("TERM-01");
        p.setValor(new BigDecimal("50.00"));
        p.setValorCobrado(new BigDecimal("50.00"));
        return p;
    }
}
