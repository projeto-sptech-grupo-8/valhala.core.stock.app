package valhalla.core.stock.app.modules.caixa.point.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import valhalla.core.stock.app.modules.caixa.point.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.point.client.dto.CriarOrderPointCommand;
import valhalla.core.stock.app.modules.caixa.point.client.dto.OrderPointResultado;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.sessao.enums.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.TerminalCaixaEntity;
import valhalla.core.stock.app.modules.caixa.exception.CaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.IntegracaoMercadoPagoException;
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.TerminalOcupadoPointException;
import valhalla.core.stock.app.modules.caixa.configuracao.mapper.ConfiguracaoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.pedido.mapper.PedidoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.sessao.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.TerminalCaixaRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import valhalla.core.stock.app.modules.caixa.pedido.service.ReservaEstoqueService;
import valhalla.core.stock.app.modules.caixa.pedido.strategy.ProcessadorPagamentoPoint;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.modules.caixa.sessao.service.EventoAuditoriaCaixa;

@Service
public class OrquestradorPagamentoPointService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<StatusPagamentoPedido> STATUS_BLOQUEIO_TERMINAL = EnumSet.of(
            StatusPagamentoPedido.PENDENTE,
            StatusPagamentoPedido.PENDENTE_NA_POINT,
            StatusPagamentoPedido.EM_TERMINAL,
            StatusPagamentoPedido.CANCELAMENTO_SOLICITADO,
            StatusPagamentoPedido.ACAO_NECESSARIA,
            StatusPagamentoPedido.PENDENTE_CONCILIACAO
    );

    private final TransactionTemplate transactionTemplate;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PagamentoPedidoRepository pagamentoPedidoRepository;
    private final TerminalCaixaRepository terminalCaixaRepository;
    private final SessaoCaixaRepository sessaoCaixaRepository;
    private final ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    private final UserRepository userRepository;
    private final ProcessadorPagamentoPoint processadorPagamentoPoint;
    private final ReservaEstoqueService reservaEstoqueService;
    private final TransicaoPagamentoPointService transicaoPagamentoPointService;
    private final MercadoPagoPointClient mercadoPagoPointClient;
    private final MercadoPagoPointProperties pointProperties;
    private final RateLimiterCaixaService rateLimiterCaixaService;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final ContextoCaixa contextoCaixa;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrquestradorPagamentoPointService(
            TransactionTemplate transactionTemplate,
            PedidoRepository pedidoRepository,
            ItemPedidoRepository itemPedidoRepository,
            PagamentoPedidoRepository pagamentoPedidoRepository,
            TerminalCaixaRepository terminalCaixaRepository,
            SessaoCaixaRepository sessaoCaixaRepository,
            ConfiguracaoCaixaRepository configuracaoCaixaRepository,
            UserRepository userRepository,
            ProcessadorPagamentoPoint processadorPagamentoPoint,
            ReservaEstoqueService reservaEstoqueService,
            TransicaoPagamentoPointService transicaoPagamentoPointService,
            MercadoPagoPointClient mercadoPagoPointClient,
            MercadoPagoPointProperties pointProperties,
            RateLimiterCaixaService rateLimiterCaixaService,
            AuditoriaCaixaService auditoriaCaixaService,
            ContextoCaixa contextoCaixa
    ) {
        this.transactionTemplate = transactionTemplate;
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.pagamentoPedidoRepository = pagamentoPedidoRepository;
        this.terminalCaixaRepository = terminalCaixaRepository;
        this.sessaoCaixaRepository = sessaoCaixaRepository;
        this.configuracaoCaixaRepository = configuracaoCaixaRepository;
        this.userRepository = userRepository;
        this.processadorPagamentoPoint = processadorPagamentoPoint;
        this.reservaEstoqueService = reservaEstoqueService;
        this.transicaoPagamentoPointService = transicaoPagamentoPointService;
        this.mercadoPagoPointClient = mercadoPagoPointClient;
        this.pointProperties = pointProperties;
        this.rateLimiterCaixaService = rateLimiterCaixaService;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.contextoCaixa = contextoCaixa;
    }

    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto iniciarPagamentoPoint(
            UUID pedidoId,
            RegistrarPagamentoPedidoRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        rateLimiterCaixaService.verificarLimiteCriacaoPoint(usuarioId);
        String chaveIdempotenciaSolicitada = normalizarTexto(dto.chaveIdempotencia());

        // FASE 1: Transação curta — validações, lock do terminal, reserva de estoque e pagamento PENDENTE.
        Fase1CriacaoPoint fase1;
        try {
            fase1 = transactionTemplate.execute(
                    status -> prepararCobrancaPoint(pedidoId, dto, estabelecimentoId, usuarioId)
            );
        } catch (DataIntegrityViolationException excecao) {
            if (chaveIdempotenciaSolicitada != null) {
                Optional<PagamentoPedidoEntity> existente = transactionTemplate.execute(status ->
                        pagamentoPedidoRepository.findByPedidoIdAndChaveIdempotencia(
                                pedidoId,
                                chaveIdempotenciaSolicitada
                        )
                );
                if (existente != null && existente.isPresent()) {
                    return carregarDetalhePedido(pedidoId, estabelecimentoId);
                }
            }
            throw excecao;
        }
        if (fase1 == null) {
            throw new IllegalStateException("Falha inesperada na preparação da cobrança Point");
        }
        if (fase1.jaExistia()) {
            return carregarDetalhePedido(pedidoId, estabelecimentoId);
        }

        // FASE 2: Chamada HTTP externa FORA de qualquer transação aberta no PostgreSQL.
        OrderPointResultado resultadoExterno;
        try {
            resultadoExterno = mercadoPagoPointClient.criarOrderPoint(fase1.command());
        } catch (IntegracaoMercadoPagoException excecao) {
            transactionTemplate.executeWithoutResult(status ->
                    tratarFalhaCriacaoOrder(
                            fase1.pagamentoId(),
                            pedidoId,
                            estabelecimentoId,
                            usuarioId,
                            excecao.isErroTransitorio(),
                            excecao.isErroTransitorio() ? "TIMEOUT_COMUNICACAO_MP" : "FALHA_ENVIO_MP",
                            excecao.getMessage()
                    )
            );
            throw excecao;
        } catch (RuntimeException excecao) {
            String mensagem = excecao.getMessage() != null && !excecao.getMessage().isBlank()
                    ? excecao.getMessage()
                    : excecao.getClass().getSimpleName();
            transactionTemplate.executeWithoutResult(status ->
                    tratarFalhaCriacaoOrder(
                            fase1.pagamentoId(),
                            pedidoId,
                            estabelecimentoId,
                            usuarioId,
                            true,
                            "ERRO_INESPERADO_ENVIO_MP",
                            mensagem
                    )
            );
            throw excecao;
        }

        // FASE 3: Transação curta — persiste order_externa_id, status PENDENTE_NA_POINT e auditoria.
        return transactionTemplate.execute(status -> {
            PagamentoPedidoEntity atualizado = transicaoPagamentoPointService.aplicarResultadoOrder(
                    fase1.pagamentoId(),
                    resultadoExterno,
                    OrigemAuditoriaCaixa.API,
                    usuarioId
            );

            PedidoEntity pedido = pedidoRepository.findById(pedidoId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

            auditoriaCaixaService.registrar(
                    estabelecimentoId,
                    usuarioId,
                    atualizado.getSessaoCaixaId(),
                    EventoAuditoriaCaixa.PAGAMENTO_POINT_ENVIADO,
                    EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                    atualizado.getId().toString(),
                    "Cobrança enviada para a Point Smart 2 (" + atualizado.getTerminalId() + ")",
                    atualizado.getValor(),
                    null,
                    pedido.getId(),
                    pedido.getIdentificadorAtendimento(),
                    atualizado.getId(),
                    null,
                    null,
                    null,
                    objectMapper.valueToTree(snapshotPagamentoPoint(atualizado))
            );

            return montarDetalhe(pedido);
        });
    }

    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto cancelarCobrancaPoint(UUID pedidoId, UUID pagamentoId) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        // FASE 1: Valida estado, trava pagamento como CANCELAMENTO_SOLICITADO ou cancela localmente se nunca enviado.
        String orderExternaId = transactionTemplate.execute(status -> {
            PedidoEntity pedido = buscarParaAtualizacao(pedidoId, estabelecimentoId);
            PagamentoPedidoEntity pagamento = pagamentoPedidoRepository
                    .buscarParaAtualizacao(pagamentoId, pedido.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Pagamento não encontrado para este pedido"));

            if (!"MERCADO_PAGO".equals(pagamento.getProvedor())) {
                throw new PedidoEstadoInvalidoException(
                        "Somente cobranças da Point (MERCADO_PAGO) suportam cancelamento remoto"
                );
            }
            if (pagamento.getStatus() == StatusPagamentoPedido.CANCELADO) {
                return null;
            }
            if (pagamento.getStatus() != StatusPagamentoPedido.PENDENTE
                    && pagamento.getStatus() != StatusPagamentoPedido.PENDENTE_NA_POINT
                    && pagamento.getStatus() != StatusPagamentoPedido.EM_TERMINAL
                    && pagamento.getStatus() != StatusPagamentoPedido.CANCELAMENTO_SOLICITADO
                    && pagamento.getStatus() != StatusPagamentoPedido.ACAO_NECESSARIA
                    && pagamento.getStatus() != StatusPagamentoPedido.PENDENTE_CONCILIACAO) {
                throw new PedidoEstadoInvalidoException(
                        "Não é possível cancelar cobrança Point com status " + pagamento.getStatus()
                );
            }

            if (pagamento.getOrderExternaId() == null || pagamento.getOrderExternaId().isBlank()) {
                pagamento.setStatus(StatusPagamentoPedido.CANCELADO);
                pagamento.setCanceladoEm(LocalDateTime.now());
                pagamento.setCanceladoPorUsuarioId(usuarioId);
                pagamento.setMotivoCancelamento("Cancelado localmente antes de confirmação externa");
                pagamentoPedidoRepository.saveAndFlush(pagamento);
                reservaEstoqueService.liberarReservasAtivasDoPedido(
                        pedido.getId(),
                        StatusReservaEstoque.LIBERADA,
                        "Cobrança Point cancelada antes de confirmação externa"
                );
                recalcularStatusPedidoAberto(pedido);
                auditoriaCaixaService.registrar(
                        estabelecimentoId,
                        usuarioId,
                        pagamento.getSessaoCaixaId(),
                        EventoAuditoriaCaixa.PAGAMENTO_CANCELADO,
                        EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                        pagamento.getId().toString(),
                        "Cobrança Point cancelada localmente",
                        pagamento.getValor(),
                        pagamento.getMotivoCancelamento(),
                        pedido.getId(),
                        pedido.getIdentificadorAtendimento(),
                        pagamento.getId(),
                        null,
                        null,
                        null,
                        objectMapper.valueToTree(snapshotPagamentoPoint(pagamento))
                );
                return null;
            }

            pagamento.setStatus(StatusPagamentoPedido.CANCELAMENTO_SOLICITADO);
            pagamentoPedidoRepository.saveAndFlush(pagamento);

            auditoriaCaixaService.registrar(
                    estabelecimentoId,
                    usuarioId,
                    pagamento.getSessaoCaixaId(),
                    EventoAuditoriaCaixa.PAGAMENTO_POINT_CANCELAMENTO_SOLICITADO,
                    EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                    pagamento.getId().toString(),
                    "Solicitado cancelamento da cobrança na Point Smart 2",
                    pagamento.getValor(),
                    null,
                    pedido.getId(),
                    pedido.getIdentificadorAtendimento(),
                    pagamento.getId(),
                    null,
                    null,
                    null,
                    objectMapper.valueToTree(snapshotPagamentoPoint(pagamento))
            );
            return pagamento.getOrderExternaId();
        });

        if (orderExternaId == null) {
            return carregarDetalhePedido(pedidoId, estabelecimentoId);
        }

        // FASE 2: Chamada externa de cancelamento (se já foi paga no aparelho, consulta o estado real).
        OrderPointResultado resultadoExterno;
        try {
            resultadoExterno = mercadoPagoPointClient.cancelarOrder(
                    orderExternaId,
                    pagamentoId + "-cancel"
            );
        } catch (IntegracaoMercadoPagoException excecao) {
            if (!excecao.isErroTransitorio()) {
                resultadoExterno = mercadoPagoPointClient.consultarOrder(orderExternaId);
            } else {
                throw excecao;
            }
        }

        // FASE 3: Aplica estado oficial retornado pela Point.
        final OrderPointResultado resultadoFinal = resultadoExterno;
        return transactionTemplate.execute(status -> {
            transicaoPagamentoPointService.aplicarResultadoOrder(
                    pagamentoId,
                    resultadoFinal,
                    OrigemAuditoriaCaixa.API,
                    usuarioId
            );
            return carregarDetalhePedido(pedidoId, estabelecimentoId);
        });
    }

    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto sincronizarCobrancaPoint(UUID pedidoId, UUID pagamentoId) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        String orderExternaId = transactionTemplate.execute(status -> {
            PedidoEntity pedido = buscarDoEstabelecimento(pedidoId, estabelecimentoId);
            PagamentoPedidoEntity pagamento = pagamentoPedidoRepository
                    .buscarParaAtualizacao(pagamentoId, pedido.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Pagamento não encontrado para este pedido"));
            if (!"MERCADO_PAGO".equals(pagamento.getProvedor())
                    || pagamento.getOrderExternaId() == null
                    || pagamento.getOrderExternaId().isBlank()) {
                throw new PedidoEstadoInvalidoException(
                        "Este pagamento não possui Order externa do Mercado Pago para sincronização"
                );
            }
            return pagamento.getOrderExternaId();
        });

        OrderPointResultado resultado = mercadoPagoPointClient.consultarOrder(orderExternaId);

        return transactionTemplate.execute(status -> {
            transicaoPagamentoPointService.aplicarResultadoOrder(
                    pagamentoId,
                    resultado,
                    OrigemAuditoriaCaixa.API,
                    usuarioId
            );
            return carregarDetalhePedido(pedidoId, estabelecimentoId);
        });
    }

    public OrderPointResultado estornarOrderExterna(String orderExternaId, UUID pagamentoId) {
        try {
            return mercadoPagoPointClient.estornarOrder(orderExternaId, pagamentoId + "-refund");
        } catch (IntegracaoMercadoPagoException excecao) {
            if (!excecao.isErroTransitorio()) {
                OrderPointResultado atual = mercadoPagoPointClient.consultarOrder(orderExternaId);
                if ("refunded".equalsIgnoreCase(atual.status())
                        || "refunded".equalsIgnoreCase(atual.paymentStatus())) {
                    return atual;
                }
            }
            throw excecao;
        }
    }

    private Fase1CriacaoPoint prepararCobrancaPoint(
            UUID pedidoId,
            RegistrarPagamentoPedidoRequestDto dto,
            UUID estabelecimentoId,
            UUID usuarioId
    ) {
        PedidoEntity pedido = buscarParaAtualizacao(pedidoId, estabelecimentoId);
        validarPedidoAptoParaPagamento(pedido);

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        if (itens.isEmpty() || pedido.getTotal().compareTo(ZERO) <= 0) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível registrar pagamento para um pedido sem itens ou com total zerado"
            );
        }

        String chaveIdempotencia = normalizarTexto(dto.chaveIdempotencia());
        if (chaveIdempotencia != null) {
            var existente = pagamentoPedidoRepository.findByPedidoIdAndChaveIdempotencia(
                    pedido.getId(),
                    chaveIdempotencia
            );
            if (existente.isPresent()) {
                return new Fase1CriacaoPoint(true, existente.get().getId(), null);
            }
        } else {
            chaveIdempotencia = UUID.randomUUID().toString();
        }

        if (dto.terminalCaixaId() == null) {
            throw new IllegalArgumentException(
                    "Informe o terminalCaixaId da Point Smart 2 para cobrança integrada"
            );
        }

        TerminalCaixaEntity terminal = terminalCaixaRepository.buscarParaAtualizacao(dto.terminalCaixaId())
                .orElseThrow(() -> new EntityNotFoundException("Terminal de caixa não encontrado"));
        if (!terminal.getCaixa().getEstabelecimentoId().equals(estabelecimentoId)) {
            throw new AccessDeniedException("Acesso negado ao terminal informado");
        }
        if (!terminal.isAtivo() || !terminal.getCaixa().isAtivo()) {
            throw new CaixaEstadoInvalidoException(
                    "O terminal selecionado ou o caixa vinculado encontra-se inativo"
            );
        }

        if (pagamentoPedidoRepository.existePagamentoAtivoNoTerminal(
                terminal.getTerminalId(),
                STATUS_BLOQUEIO_TERMINAL
        )) {
            throw new TerminalOcupadoPointException(
                    "O terminal " + terminal.getNome() + " já possui uma cobrança em andamento"
            );
        }

        SessaoCaixaEntity sessaoRecebedora = resolverSessaoParaRecebimento(pedido, estabelecimentoId, usuarioId);
        ConfiguracaoCaixaEntity configuracao = configuracaoCaixaRepository
                .findByEstabelecimentoId(estabelecimentoId)
                .orElseGet(() -> ConfiguracaoCaixaMapper.criarPadrao(estabelecimentoId));

        List<PagamentoPedidoEntity> pagamentosExistentes =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());
        BigDecimal valorComprometido = pagamentosExistentes.stream()
                .filter(PagamentoPedidoEntity::compoeSaldoEmAberto)
                .map(PagamentoPedidoEntity::getValor)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal saldoDisponivel = pedido.getTotal().subtract(valorComprometido).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorSolicitado = dto.valor().setScale(2, RoundingMode.HALF_UP);
        if (valorSolicitado.compareTo(saldoDisponivel) > 0) {
            throw new IllegalArgumentException(
                    "O valor do pagamento (R$ " + valorSolicitado
                            + ") excede o saldo em aberto do pedido (R$ " + saldoDisponivel + ")"
            );
        }

        PagamentoPedidoEntity pagamento = processadorPagamentoPoint.prepararPagamentoPendente(
                pedido,
                sessaoRecebedora,
                configuracao,
                dto,
                terminal.getTerminalId(),
                chaveIdempotencia
        );
        pagamento.setTerminalCaixa(terminal);

        PagamentoPedidoEntity pagamentoSalvo = pagamentoPedidoRepository.saveAndFlush(pagamento);
        reservaEstoqueService.reservarParaPagamentoPoint(pedido, itens, pagamentoSalvo.getId());

        pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        pedidoRepository.saveAndFlush(pedido);

        CriarOrderPointCommand command = new CriarOrderPointCommand(
                pagamentoSalvo.getChaveIdempotencia(),
                pagamentoSalvo.getReferenciaExterna(),
                pagamentoSalvo.getValorCobrado(),
                terminal.getTerminalId(),
                pagamentoSalvo.getMetodoPagamento(),
                pagamentoSalvo.getParcelas(),
                pointProperties.orderExpirationTime(),
                "Pedido #" + pedido.getNumero() + " - Valhalla",
                resolverInstallmentsCost(pagamentoSalvo)
        );

        return new Fase1CriacaoPoint(false, pagamentoSalvo.getId(), command);
    }

    private void tratarFalhaCriacaoOrder(
            UUID pagamentoId,
            UUID pedidoId,
            UUID estabelecimentoId,
            UUID usuarioId,
            boolean erroTransitorio,
            String statusDetalhe,
            String mensagemErro
    ) {
        PedidoEntity pedido = buscarParaAtualizacao(pedidoId, estabelecimentoId);
        PagamentoPedidoEntity pagamento = pagamentoPedidoRepository
                .buscarParaAtualizacao(pagamentoId, pedido.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pagamento não encontrado"));

        if (erroTransitorio) {
            pagamento.setStatus(StatusPagamentoPedido.PENDENTE_CONCILIACAO);
            pagamento.setStatusDetalhe(statusDetalhe);
            pagamento.setMotivoCancelamento(mensagemErro);
            pagamentoPedidoRepository.saveAndFlush(pagamento);
        } else {
            pagamento.setStatus(StatusPagamentoPedido.RECUSADO);
            pagamento.setStatusDetalhe(statusDetalhe);
            pagamento.setMotivoCancelamento(mensagemErro);
            pagamentoPedidoRepository.saveAndFlush(pagamento);

            reservaEstoqueService.liberarReservasAtivasDoPedido(
                    pedido.getId(),
                    StatusReservaEstoque.LIBERADA,
                    "Falha ao enviar cobrança para a Point"
            );
            recalcularStatusPedidoAberto(pedido);
        }

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pagamento.getSessaoCaixaId(),
                EventoAuditoriaCaixa.PAGAMENTO_POINT_FALHOU,
                EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                pagamento.getId().toString(),
                "Falha ao enviar cobrança para a Point: " + mensagemErro,
                pagamento.getValor(),
                mensagemErro,
                pedido.getId(),
                pedido.getIdentificadorAtendimento(),
                pagamento.getId(),
                null,
                null,
                null,
                objectMapper.valueToTree(snapshotPagamentoPoint(pagamento))
        );
    }

    private void recalcularStatusPedidoAberto(PedidoEntity pedido) {
        if (!pedido.getStatus().estaAberto()) {
            return;
        }
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());
        BigDecimal totalAprovado = PedidoCaixaMapper.calcularTotalPagoAprovado(pagamentos);
        boolean possuiPendentePoint = pagamentos.stream().anyMatch(p ->
                p.getStatus() == StatusPagamentoPedido.PENDENTE
                        || p.getStatus() == StatusPagamentoPedido.PENDENTE_NA_POINT
                        || p.getStatus() == StatusPagamentoPedido.EM_TERMINAL
                        || p.getStatus() == StatusPagamentoPedido.CANCELAMENTO_SOLICITADO
                        || p.getStatus() == StatusPagamentoPedido.ACAO_NECESSARIA
        );
        if (possuiPendentePoint) {
            pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_MAQUINA);
        } else if (totalAprovado.compareTo(ZERO) > 0) {
            pedido.setStatus(StatusPedidoCaixa.PAGAMENTO_PARCIAL);
        } else {
            pedido.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        }
        pedidoRepository.saveAndFlush(pedido);
    }

    private PedidoCaixaDetalheResponseDto carregarDetalhePedido(UUID pedidoId, UUID estabelecimentoId) {
        PedidoEntity pedido = buscarDoEstabelecimento(pedidoId, estabelecimentoId);
        return montarDetalhe(pedido);
    }

    private PedidoCaixaDetalheResponseDto montarDetalhe(PedidoEntity pedido) {
        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        Set<UUID> idsUsuarios = java.util.stream.Stream.of(
                        pedido.getOperadorUsuarioId(),
                        pedido.getDescontoDecididoPorUsuarioId()
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> nomes = idsUsuarios.isEmpty()
                ? Map.of()
                : userRepository.findAllById(idsUsuarios).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));

        String operadorNome = nomes.getOrDefault(pedido.getOperadorUsuarioId(), "Operador");
        String autorizadorNome = pedido.getDescontoDecididoPorUsuarioId() == null
                ? null
                : nomes.get(pedido.getDescontoDecididoPorUsuarioId());

        return PedidoCaixaMapper.paraDetalhe(pedido, operadorNome, autorizadorNome, itens, pagamentos);
    }

    private SessaoCaixaEntity resolverSessaoParaRecebimento(
            PedidoEntity pedido,
            UUID estabelecimentoId,
            UUID usuarioId
    ) {
        var sessaoOperador = sessaoCaixaRepository
                .findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                        estabelecimentoId,
                        usuarioId,
                        StatusSessaoCaixa.ABERTA
                );
        if (sessaoOperador.isPresent()) {
            return sessaoOperador.get();
        }

        if (pedido.getSessaoCaixaId() != null) {
            SessaoCaixaEntity sessaoPedido = sessaoCaixaRepository
                    .findByIdAndEstabelecimentoId(pedido.getSessaoCaixaId(), estabelecimentoId)
                    .orElseThrow(() -> new EntityNotFoundException("Sessão de caixa do pedido não encontrada"));
            if (sessaoPedido.isAberta()
                    && (sessaoPedido.getOperadorUsuarioId().equals(usuarioId) || contextoCaixa.isGerente())) {
                return sessaoPedido;
            }
        }

        throw new SessaoCaixaEstadoInvalidoException(
                "É necessário possuir uma sessão de caixa aberta para registrar pagamentos"
        );
    }

    private PedidoEntity buscarDoEstabelecimento(UUID id, UUID estabelecimentoId) {
        PedidoEntity pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        if (!pedido.getEstabelecimentoId().equals(estabelecimentoId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return pedido;
    }

    private PedidoEntity buscarParaAtualizacao(UUID id, UUID estabelecimentoId) {
        PedidoEntity pedido = pedidoRepository.buscarPorIdParaAtualizacao(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        if (!pedido.getEstabelecimentoId().equals(estabelecimentoId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return pedido;
    }

    private void validarPedidoAptoParaPagamento(PedidoEntity pedido) {
        if (!pedido.getStatus().permitePagamento()) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível registrar pagamento para um pedido com status " + pedido.getStatus()
            );
        }
        if (pedido.getStatusDesconto() == StatusDescontoPedido.PENDENTE_AUTORIZACAO) {
            throw new PedidoEstadoInvalidoException(
                    "O pedido possui um desconto pendente de autorização. Aguarde a decisão do gerente ou remova o desconto para prosseguir"
            );
        }
    }

    private Map<String, Object> snapshotPagamentoPoint(PagamentoPedidoEntity pagamento) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", pagamento.getId().toString());
        map.put("pedidoId", pagamento.getPedidoId().toString());
        map.put("terminalId", pagamento.getTerminalId());
        map.put("forma", pagamento.getForma().name());
        map.put("status", pagamento.getStatus().name());
        map.put("orderExternaId", pagamento.getOrderExternaId());
        map.put("referenciaExterna", pagamento.getReferenciaExterna());
        map.put("valor", pagamento.getValor());
        map.put("valorCobrado", pagamento.getValorCobrado());
        return map;
    }

    static String resolverInstallmentsCost(PagamentoPedidoEntity pagamento) {
        if (pagamento.getPoliticaJuros() == PoliticaJurosCaixa.REPASSADA_AO_CLIENTE
                && (pagamento.getValorJuros() == null || pagamento.getValorJuros().compareTo(ZERO) <= 0)) {
            return "buyer";
        }
        return "seller";
    }

    private String normalizarTexto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private record Fase1CriacaoPoint(
            boolean jaExistia,
            UUID pagamentoId,
            CriarOrderPointCommand command
    ) {
    }
}
