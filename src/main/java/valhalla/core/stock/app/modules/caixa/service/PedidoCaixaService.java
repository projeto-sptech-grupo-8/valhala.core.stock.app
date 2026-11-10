package valhalla.core.stock.app.modules.caixa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.dto.CancelarPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.ConciliarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.DecisaoDescontoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.DescontoPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.EstornarPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.ItemPedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.PagamentoPedidoResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.PedidoCaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.PedidoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.PedidoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.entity.CanalPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.entity.SequenciaDocumentoEntity;
import valhalla.core.stock.app.modules.caixa.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.entity.StatusDescontoPedido;
import valhalla.core.stock.app.modules.caixa.entity.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.entity.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.entity.TipoDescontoPedido;
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.SessaoCaixaEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.mapper.ConfiguracaoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.mapper.PagamentoPedidoMapper;
import valhalla.core.stock.app.modules.caixa.mapper.PedidoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.repository.SequenciaDocumentoRepository;
import valhalla.core.stock.app.modules.caixa.repository.SessaoCaixaRepository;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.estoque.service.MovimentacaoEstoqueService;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PedidoCaixaService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final String TIPO_DOCUMENTO_PEDIDO = "PEDIDO";
    private static final Set<String> CAMPOS_ORDENACAO =
            Set.of("criadoEm", "atualizadoEm", "concluidoEm", "numero", "total", "status");

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PagamentoPedidoRepository pagamentoPedidoRepository;
    private final SequenciaDocumentoRepository sequenciaDocumentoRepository;
    private final SessaoCaixaRepository sessaoCaixaRepository;
    private final ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    private final ProdutoRepository produtoRepository;
    private final UserRepository userRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final AutorizadorOperacaoCaixaService autorizadorOperacaoCaixaService;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final ContextoCaixa contextoCaixa;
    private final List<ProcessadorPagamento> processadoresPagamento;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PedidoCaixaService(
            PedidoRepository pedidoRepository,
            ItemPedidoRepository itemPedidoRepository,
            PagamentoPedidoRepository pagamentoPedidoRepository,
            SequenciaDocumentoRepository sequenciaDocumentoRepository,
            SessaoCaixaRepository sessaoCaixaRepository,
            ConfiguracaoCaixaRepository configuracaoCaixaRepository,
            ProdutoRepository produtoRepository,
            UserRepository userRepository,
            MovimentacaoEstoqueService movimentacaoEstoqueService,
            AutorizadorOperacaoCaixaService autorizadorOperacaoCaixaService,
            AuditoriaCaixaService auditoriaCaixaService,
            ContextoCaixa contextoCaixa,
            List<ProcessadorPagamento> processadoresPagamento,
            BusinessEventLogger businessEventLogger
    ) {
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.pagamentoPedidoRepository = pagamentoPedidoRepository;
        this.sequenciaDocumentoRepository = sequenciaDocumentoRepository;
        this.sessaoCaixaRepository = sessaoCaixaRepository;
        this.configuracaoCaixaRepository = configuracaoCaixaRepository;
        this.produtoRepository = produtoRepository;
        this.userRepository = userRepository;
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.autorizadorOperacaoCaixaService = autorizadorOperacaoCaixaService;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.contextoCaixa = contextoCaixa;
        this.processadoresPagamento = processadoresPagamento;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto criarPedido(PedidoCaixaRequestDto dto) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        SessaoCaixaEntity sessao = resolverSessaoAberta(
                dto == null ? null : dto.sessaoCaixaId(),
                estabelecimentoId,
                usuarioId
        );

        CanalPedidoCaixa canal = dto != null && dto.canal() != null ? dto.canal() : CanalPedidoCaixa.BALCAO;
        String identificador = normalizarTexto(dto == null ? null : dto.identificadorAtendimento());
        validarCanalEIdentificador(canal, identificador);

        List<ItemPedidoCaixaRequestDto> itensRequisitados = dto != null && dto.itens() != null
                ? dto.itens()
                : List.of();

        validarEstoqueDosItens(estabelecimentoId, itensRequisitados);

        Long numero = gerarProximoNumeroPedido(estabelecimentoId);
        LocalDateTime agora = LocalDateTime.now();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setEstabelecimentoId(estabelecimentoId);
        pedido.setOperadorUsuarioId(usuarioId);
        pedido.setSessaoCaixaId(sessao.getId());
        pedido.setNumero(numero);
        pedido.setTipo("VENDA_PDV");
        pedido.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        pedido.setCanal(canal);
        pedido.setIdentificadorAtendimento(identificador);
        pedido.setObservacao(normalizarTexto(dto == null ? null : dto.observacao()));
        pedido.setSubtotal(ZERO);
        pedido.setDesconto(ZERO);
        pedido.setTotal(ZERO);
        pedido.setStatusDesconto(StatusDescontoPedido.NENHUM);
        pedido.setCriadoEm(agora);
        pedido.setAtualizadoEm(agora);

        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);
        List<ItemPedidoEntity> itensSalvos = salvarItensDoPedido(
                pedidoSalvo.getId(),
                estabelecimentoId,
                itensRequisitados
        );

        recalcularTotaisComItens(pedidoSalvo, itensSalvos);
        pedidoSalvo = pedidoRepository.saveAndFlush(pedidoSalvo);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                sessao.getId(),
                EventoAuditoriaCaixa.PEDIDO_CRIADO,
                EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                pedidoSalvo.getId().toString(),
                "Pedido #" + pedidoSalvo.getNumero() + " aberto no caixa",
                pedidoSalvo.getTotal(),
                null,
                pedidoSalvo.getId(),
                pedidoSalvo.getIdentificadorAtendimento(),
                null,
                null,
                null,
                null,
                objectMapper.valueToTree(snapshotPedido(pedidoSalvo, itensSalvos))
        );

        businessEventLogger.success("cash.order.created", "cash_order", pedidoSalvo.getId());

        return montarDetalhe(pedidoSalvo, itensSalvos, List.of());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public RespostaPaginadaDto<PedidoCaixaResumoResponseDto> listarPedidos(
            UUID sessaoCaixaId,
            StatusPedidoCaixa status,
            CanalPedidoCaixa canal,
            LocalDate dataInicio,
            LocalDate dataFim,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();

        Specification<PedidoEntity> especificacao =
                (root, query, builder) -> builder.equal(root.get("estabelecimentoId"), estabelecimentoId);

        if (sessaoCaixaId != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("sessaoCaixaId"), sessaoCaixaId)
            );
        }
        if (status != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("status"), status)
            );
        }
        if (canal != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("canal"), canal)
            );
        }
        if (dataInicio != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.greaterThanOrEqualTo(
                            root.get("criadoEm"), dataInicio.atStartOfDay()
                    )
            );
        }
        if (dataFim != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.lessThan(
                            root.get("criadoEm"), dataFim.plusDays(1).atStartOfDay()
                    )
            );
        }

        Page<PedidoEntity> resultado = pedidoRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO)
        );

        List<PedidoEntity> pedidos = resultado.getContent();
        List<UUID> pedidoIds = pedidos.stream().map(PedidoEntity::getId).toList();

        Map<UUID, List<ItemPedidoEntity>> itensPorPedido = pedidoIds.isEmpty()
                ? Map.of()
                : itemPedidoRepository.findByPedidoIdInOrderByIdAsc(pedidoIds).stream()
                .collect(Collectors.groupingBy(ItemPedidoEntity::getPedidoId));

        Map<UUID, List<PagamentoPedidoEntity>> pagamentosPorPedido = pedidoIds.isEmpty()
                ? Map.of()
                : pagamentoPedidoRepository.findByPedidoIdInOrderByCriadoEmAsc(pedidoIds).stream()
                .collect(Collectors.groupingBy(PagamentoPedidoEntity::getPedidoId));

        Map<UUID, String> nomesUsuarios = carregarNomesUsuarios(
                pedidos.stream().map(PedidoEntity::getOperadorUsuarioId).collect(Collectors.toSet())
        );

        return RespostaPaginadaDto.de(
                resultado,
                pedido -> PedidoCaixaMapper.paraResumo(
                        pedido,
                        nomesUsuarios.getOrDefault(pedido.getOperadorUsuarioId(), "Operador"),
                        itensPorPedido.getOrDefault(pedido.getId(), List.of()).size(),
                        pagamentosPorPedido.getOrDefault(pedido.getId(), List.of())
                )
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto buscarPedido(UUID id) {
        PedidoEntity pedido = buscarDoEstabelecimento(id);
        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());
        return montarDetalhe(pedido, itens, pagamentos);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto atualizarPedido(
            UUID id,
            PedidoCaixaAtualizacaoRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(id);
        exigirPedidoEditavelParaItens(pedido);

        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());
        boolean possuiPagamentoAtivo = pagamentos.stream().anyMatch(PagamentoPedidoEntity::compoeSaldoEmAberto);
        if (possuiPagamentoAtivo) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível alterar os itens de um pedido que já possui pagamentos registrados ou pendentes"
            );
        }

        CanalPedidoCaixa novoCanal = dto.canal() != null ? dto.canal() : pedido.getCanal();
        String novoIdentificador = dto.identificadorAtendimento() != null
                ? normalizarTexto(dto.identificadorAtendimento())
                : pedido.getIdentificadorAtendimento();
        validarCanalEIdentificador(novoCanal, novoIdentificador);

        pedido.setCanal(novoCanal);
        pedido.setIdentificadorAtendimento(novoIdentificador);
        if (dto.observacao() != null) {
            pedido.setObservacao(normalizarTexto(dto.observacao()));
        }

        List<ItemPedidoEntity> itensAtuais = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<ItemPedidoEntity> itensFinais = itensAtuais;

        if (dto.itens() != null) {
            validarEstoqueDosItens(estabelecimentoId, dto.itens());
            auditarItensRemovidosOuReduzidos(pedido, itensAtuais, dto.itens(), usuarioId);

            itemPedidoRepository.deleteByPedidoId(pedido.getId());
            itemPedidoRepository.flush();

            itensFinais = salvarItensDoPedido(pedido.getId(), estabelecimentoId, dto.itens());
            recalcularTotaisComItens(pedido, itensFinais);
        }

        pedido.setAtualizadoEm(LocalDateTime.now());
        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        return montarDetalhe(pedidoSalvo, itensFinais, pagamentos);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto aplicarOuSolicitarDesconto(
            UUID id,
            DescontoPedidoCaixaRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(id);
        exigirPedidoAbertoParaDesconto(pedido);

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        if (itens.isEmpty() || pedido.getSubtotal().compareTo(ZERO) <= 0) {
            throw new PedidoEstadoInvalidoException(
                    "Adicione itens ao pedido antes de aplicar um desconto"
            );
        }

        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());
        BigDecimal totalComprometido = pagamentos.stream()
                .filter(PagamentoPedidoEntity::compoeSaldoEmAberto)
                .map(PagamentoPedidoEntity::getValor)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal valorInformado = dto.valorDescontoInformado().setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorDescontoCalculado = calcularValorDesconto(
                pedido.getSubtotal(),
                dto.tipoDesconto(),
                valorInformado
        );

        if (valorDescontoCalculado.compareTo(pedido.getSubtotal()) >= 0) {
            throw new IllegalArgumentException(
                    "O desconto deve ser menor que o subtotal do pedido"
            );
        }

        BigDecimal novoTotalPotencial = pedido.getSubtotal().subtract(valorDescontoCalculado).setScale(2, RoundingMode.HALF_UP);
        if (novoTotalPotencial.compareTo(totalComprometido) < 0) {
            throw new PedidoEstadoInvalidoException(
                    "O desconto reduz o total do pedido abaixo do valor já pago ou em processamento"
            );
        }

        ConfiguracaoCaixaEntity configuracao = obterConfiguracao(estabelecimentoId);
        BigDecimal limiteEfetivo = calcularLimiteDescontoSemAutorizacao(pedido.getSubtotal(), configuracao);
        boolean dentroDoLimite = valorDescontoCalculado.compareTo(limiteEfetivo) <= 0;
        String motivo = normalizarTexto(dto.motivo());

        Map<String, Object> dadosAnteriores = snapshotDesconto(pedido);

        if (dentroDoLimite) {
            pedido.setTipoDesconto(dto.tipoDesconto());
            pedido.setValorDescontoInformado(valorInformado);
            pedido.setDesconto(valorDescontoCalculado);
            pedido.setTotal(novoTotalPotencial);
            pedido.setStatusDesconto(StatusDescontoPedido.APROVADO);
            pedido.setDescontoSolicitado(null);
            pedido.setMotivoDesconto(motivo);
            pedido.setDescontoDecididoPorUsuarioId(usuarioId);
            pedido.setDescontoDecididoEm(LocalDateTime.now());
            pedido.setObservacaoDecisaoDesconto(null);

            PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);
            auditoriaCaixaService.registrar(
                    estabelecimentoId,
                    usuarioId,
                    pedidoSalvo.getSessaoCaixaId(),
                    EventoAuditoriaCaixa.DESCONTO_CONCEDIDO,
                    EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                    pedidoSalvo.getId().toString(),
                    "Desconto aplicado dentro do limite permitido",
                    valorDescontoCalculado,
                    motivo,
                    pedidoSalvo.getId(),
                    pedidoSalvo.getIdentificadorAtendimento(),
                    null,
                    null,
                    null,
                    objectMapper.valueToTree(dadosAnteriores),
                    objectMapper.valueToTree(snapshotDesconto(pedidoSalvo))
            );
            return montarDetalhe(pedidoSalvo, itens, pagamentos);
        }

        var autorizadorOpt = autorizadorOperacaoCaixaService.tentarResolverAutorizador(
                "CAIXA_AUTORIZAR_DESCONTO",
                dto.autorizadorEmail(),
                dto.autorizadorSenha()
        );

        if (autorizadorOpt.isPresent()) {
            var autorizador = autorizadorOpt.get();
            pedido.setTipoDesconto(dto.tipoDesconto());
            pedido.setValorDescontoInformado(valorInformado);
            pedido.setDesconto(valorDescontoCalculado);
            pedido.setTotal(novoTotalPotencial);
            pedido.setStatusDesconto(StatusDescontoPedido.APROVADO);
            pedido.setDescontoSolicitado(null);
            pedido.setMotivoDesconto(motivo);
            pedido.setDescontoDecididoPorUsuarioId(autorizador.usuarioId());
            pedido.setDescontoDecididoEm(LocalDateTime.now());
            pedido.setObservacaoDecisaoDesconto("Autorizado diretamente na aplicação do desconto");

            PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);
            auditoriaCaixaService.registrar(
                    estabelecimentoId,
                    usuarioId,
                    pedidoSalvo.getSessaoCaixaId(),
                    EventoAuditoriaCaixa.DESCONTO_ACIMA_DO_LIMITE,
                    EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                    pedidoSalvo.getId().toString(),
                    "Desconto acima do limite autorizado imediatamente",
                    valorDescontoCalculado,
                    motivo,
                    pedidoSalvo.getId(),
                    pedidoSalvo.getIdentificadorAtendimento(),
                    null,
                    autorizador.usuarioId(),
                    autorizador.nome(),
                    objectMapper.valueToTree(dadosAnteriores),
                    objectMapper.valueToTree(snapshotDesconto(pedidoSalvo))
            );
            return montarDetalhe(pedidoSalvo, itens, pagamentos);
        }

        if (motivo == null) {
            throw new IllegalArgumentException(
                    "O motivo é obrigatório para solicitar desconto acima do limite permitido"
            );
        }

        pedido.setTipoDesconto(dto.tipoDesconto());
        pedido.setValorDescontoInformado(valorInformado);
        pedido.setDesconto(ZERO);
        pedido.setTotal(pedido.getSubtotal());
        pedido.setStatusDesconto(StatusDescontoPedido.PENDENTE_AUTORIZACAO);
        pedido.setDescontoSolicitado(valorDescontoCalculado);
        pedido.setMotivoDesconto(motivo);
        pedido.setDescontoDecididoPorUsuarioId(null);
        pedido.setDescontoDecididoEm(null);
        pedido.setObservacaoDecisaoDesconto(null);

        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);
        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pedidoSalvo.getSessaoCaixaId(),
                EventoAuditoriaCaixa.DESCONTO_SOLICITADO,
                EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                pedidoSalvo.getId().toString(),
                "Solicitação de desconto acima do limite aguardando autorização",
                valorDescontoCalculado,
                motivo,
                pedidoSalvo.getId(),
                pedidoSalvo.getIdentificadorAtendimento(),
                null,
                null,
                null,
                objectMapper.valueToTree(dadosAnteriores),
                objectMapper.valueToTree(snapshotDesconto(pedidoSalvo))
        );

        return montarDetalhe(pedidoSalvo, itens, pagamentos);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            || @permissionAuthorizationService.hasPermission(
                'CAIXA_AUTORIZAR_DESCONTO',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto decidirDescontoPendente(
            UUID id,
            DecisaoDescontoPedidoRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(id);
        exigirPedidoAbertoParaDesconto(pedido);

        if (pedido.getStatusDesconto() != StatusDescontoPedido.PENDENTE_AUTORIZACAO
                || pedido.getDescontoSolicitado() == null) {
            throw new PedidoEstadoInvalidoException(
                    "O pedido não possui solicitação de desconto pendente de autorização"
            );
        }

        var autorizador = autorizadorOperacaoCaixaService.exigirAutorizador(
                "CAIXA_AUTORIZAR_DESCONTO",
                dto.autorizadorEmail(),
                dto.autorizadorSenha(),
                "Permissão CAIXA_AUTORIZAR_DESCONTO necessária para aprovar ou negar o desconto"
        );

        String observacao = normalizarTexto(dto.observacao());
        if (!Boolean.TRUE.equals(dto.aprovado()) && observacao == null) {
            throw new IllegalArgumentException(
                    "Informe a observação/motivo ao negar a solicitação de desconto"
            );
        }

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        Map<String, Object> dadosAnteriores = snapshotDesconto(pedido);
        BigDecimal valorSolicitado = pedido.getDescontoSolicitado();

        if (Boolean.TRUE.equals(dto.aprovado())) {
            BigDecimal novoTotal = pedido.getSubtotal().subtract(valorSolicitado).setScale(2, RoundingMode.HALF_UP);
            if (novoTotal.compareTo(ZERO) <= 0) {
                throw new PedidoEstadoInvalidoException("O desconto solicitado excede o subtotal atual do pedido");
            }
            pedido.setDesconto(valorSolicitado);
            pedido.setTotal(novoTotal);
            pedido.setStatusDesconto(StatusDescontoPedido.APROVADO);
            pedido.setDescontoSolicitado(null);
            pedido.setDescontoDecididoPorUsuarioId(autorizador.usuarioId());
            pedido.setDescontoDecididoEm(LocalDateTime.now());
            pedido.setObservacaoDecisaoDesconto(observacao);

            PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);
            auditoriaCaixaService.registrar(
                    estabelecimentoId,
                    usuarioId,
                    pedidoSalvo.getSessaoCaixaId(),
                    EventoAuditoriaCaixa.DESCONTO_ACIMA_DO_LIMITE,
                    EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                    pedidoSalvo.getId().toString(),
                    "Desconto acima do limite aprovado por " + autorizador.nome(),
                    valorSolicitado,
                    observacao != null ? observacao : pedidoSalvo.getMotivoDesconto(),
                    pedidoSalvo.getId(),
                    pedidoSalvo.getIdentificadorAtendimento(),
                    null,
                    autorizador.usuarioId(),
                    autorizador.nome(),
                    objectMapper.valueToTree(dadosAnteriores),
                    objectMapper.valueToTree(snapshotDesconto(pedidoSalvo))
            );
            return montarDetalhe(pedidoSalvo, itens, pagamentos);
        }

        pedido.setDesconto(ZERO);
        pedido.setTotal(pedido.getSubtotal());
        pedido.setStatusDesconto(StatusDescontoPedido.NEGADO);
        pedido.setDescontoDecididoPorUsuarioId(autorizador.usuarioId());
        pedido.setDescontoDecididoEm(LocalDateTime.now());
        pedido.setObservacaoDecisaoDesconto(observacao);

        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);
        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pedidoSalvo.getSessaoCaixaId(),
                EventoAuditoriaCaixa.DESCONTO_NEGADO,
                EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                pedidoSalvo.getId().toString(),
                "Solicitação de desconto negada por " + autorizador.nome(),
                valorSolicitado,
                observacao,
                pedidoSalvo.getId(),
                pedidoSalvo.getIdentificadorAtendimento(),
                null,
                autorizador.usuarioId(),
                autorizador.nome(),
                objectMapper.valueToTree(dadosAnteriores),
                objectMapper.valueToTree(snapshotDesconto(pedidoSalvo))
        );
        return montarDetalhe(pedidoSalvo, itens, pagamentos);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto removerDesconto(UUID id) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(id);
        exigirPedidoAbertoParaDesconto(pedido);

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        if (pedido.getStatusDesconto() == StatusDescontoPedido.NENHUM) {
            return montarDetalhe(pedido, itens, pagamentos);
        }

        Map<String, Object> dadosAnteriores = snapshotDesconto(pedido);
        BigDecimal valorReferencia = pedido.getDesconto() != null && pedido.getDesconto().compareTo(ZERO) > 0
                ? pedido.getDesconto()
                : pedido.getDescontoSolicitado();

        limparCamposDeDesconto(pedido);
        pedido.setTotal(pedido.getSubtotal());

        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);
        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pedidoSalvo.getSessaoCaixaId(),
                EventoAuditoriaCaixa.DESCONTO_REMOVIDO,
                EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                pedidoSalvo.getId().toString(),
                "Desconto ou solicitação de desconto removido do pedido",
                valorReferencia,
                null,
                pedidoSalvo.getId(),
                pedidoSalvo.getIdentificadorAtendimento(),
                null,
                null,
                null,
                objectMapper.valueToTree(dadosAnteriores),
                objectMapper.valueToTree(snapshotDesconto(pedidoSalvo))
        );

        return montarDetalhe(pedidoSalvo, itens, pagamentos);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto cancelarPedido(
            UUID id,
            CancelarPedidoCaixaRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(id);
        if (pedido.getStatus() == StatusPedidoCaixa.CANCELADO
                || pedido.getStatus() == StatusPedidoCaixa.CONCLUIDO
                || pedido.getStatus() == StatusPedidoCaixa.ESTORNADO) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível cancelar um pedido com status " + pedido.getStatus()
            );
        }

        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());
        boolean possuiPagamentoAtivo = pagamentos.stream().anyMatch(PagamentoPedidoEntity::compoeSaldoEmAberto);
        if (possuiPagamentoAtivo) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível cancelar um pedido que já possui pagamentos aprovados ou pendentes"
            );
        }

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        String motivo = normalizarTexto(dto == null ? null : dto.motivo());

        UUID autorizadorId = usuarioId;
        String autorizadorNome = null;

        if (!itens.isEmpty()) {
            if (motivo == null) {
                throw new IllegalArgumentException(
                        "O motivo é obrigatório para cancelar um pedido que já possui itens"
                );
            }
            var autorizador = autorizadorOperacaoCaixaService.exigirAutorizador(
                    "CAIXA_CANCELAR",
                    dto == null ? null : dto.autorizadorEmail(),
                    dto == null ? null : dto.autorizadorSenha(),
                    "Permissão CAIXA_CANCELAR necessária para cancelar um pedido com itens"
            );
            autorizadorId = autorizador.usuarioId();
            autorizadorNome = autorizador.nome();
        } else if (motivo == null) {
            motivo = "Descarte de pedido vazio";
        }

        pedido.setStatus(StatusPedidoCaixa.CANCELADO);
        pedido.setMotivoCancelamento(motivo);
        pedido.setCanceladoPorUsuarioId(autorizadorId);
        pedido.setCanceladoEm(LocalDateTime.now());

        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pedidoSalvo.getSessaoCaixaId(),
                EventoAuditoriaCaixa.PEDIDO_CANCELADO,
                EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                pedidoSalvo.getId().toString(),
                "Pedido #" + pedidoSalvo.getNumero() + " cancelado",
                pedidoSalvo.getTotal(),
                motivo,
                pedidoSalvo.getId(),
                pedidoSalvo.getIdentificadorAtendimento(),
                null,
                autorizadorId,
                autorizadorNome,
                null,
                objectMapper.valueToTree(snapshotPedido(pedidoSalvo, itens))
        );

        businessEventLogger.success("cash.order.cancelled", "cash_order", pedidoSalvo.getId());

        return montarDetalhe(pedidoSalvo, itens, pagamentos);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto registrarPagamento(
            UUID id,
            RegistrarPagamentoPedidoRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(id);
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
                List<PagamentoPedidoEntity> pagamentosAtuais =
                        pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());
                return montarDetalhe(pedido, itens, pagamentosAtuais);
            }
        }

        SessaoCaixaEntity sessaoRecebedora = resolverSessaoParaRecebimento(pedido, estabelecimentoId, usuarioId);
        ConfiguracaoCaixaEntity configuracao = obterConfiguracao(estabelecimentoId);

        List<PagamentoPedidoEntity> pagamentosExistentes =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        BigDecimal valorComprometido = pagamentosExistentes.stream()
                .filter(PagamentoPedidoEntity::compoeSaldoEmAberto)
                .map(PagamentoPedidoEntity::getValor)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal saldoDisponivelParaCobranca = pedido.getTotal().subtract(valorComprometido).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorSolicitado = dto.valor().setScale(2, RoundingMode.HALF_UP);

        if (valorSolicitado.compareTo(saldoDisponivelParaCobranca) > 0) {
            throw new IllegalArgumentException(
                    "O valor do pagamento (R$ " + valorSolicitado
                            + ") excede o saldo em aberto do pedido (R$ " + saldoDisponivelParaCobranca + ")"
            );
        }

        ProcessadorPagamento processador = processadoresPagamento.stream()
                .filter(p -> p.suporta(dto.forma(), configuracao))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhum processador disponível para a forma de pagamento " + dto.forma()
                ));

        PagamentoPedidoEntity novoPagamento = processador.processar(
                pedido,
                sessaoRecebedora,
                configuracao,
                dto
        );

        PagamentoPedidoEntity pagamentoSalvo = salvarPagamentoComIdempotencia(novoPagamento);
        List<PagamentoPedidoEntity> todosPagamentos = new ArrayList<>(pagamentosExistentes);
        todosPagamentos.add(pagamentoSalvo);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                sessaoRecebedora.getId(),
                EventoAuditoriaCaixa.PAGAMENTO_REGISTRADO,
                EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                pagamentoSalvo.getId().toString(),
                "Pagamento " + pagamentoSalvo.getForma() + " registrado (" + pagamentoSalvo.getStatus() + ")",
                pagamentoSalvo.getValor(),
                null,
                pedido.getId(),
                pedido.getIdentificadorAtendimento(),
                pagamentoSalvo.getId(),
                null,
                null,
                null,
                objectMapper.valueToTree(snapshotPagamento(pagamentoSalvo))
        );

        atualizarStatusPedidoAposPagamento(pedido, itens, todosPagamentos, usuarioId);
        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        return montarDetalhe(pedidoSalvo, itens, todosPagamentos);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public List<PagamentoPedidoResponseDto> listarPagamentosDoPedido(UUID pedidoId) {
        PedidoEntity pedido = buscarDoEstabelecimento(pedidoId);
        return pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId())
                .stream()
                .map(PagamentoPedidoMapper::paraResposta)
                .toList();
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto conciliarPagamento(
            UUID pedidoId,
            UUID pagamentoId,
            ConciliarPagamentoPedidoRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(pedidoId);
        if (pedido.getStatus() == StatusPedidoCaixa.CANCELADO
                || pedido.getStatus() == StatusPedidoCaixa.CONCLUIDO
                || pedido.getStatus() == StatusPedidoCaixa.ESTORNADO) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível conciliar pagamentos de um pedido com status " + pedido.getStatus()
            );
        }

        PagamentoPedidoEntity pagamento = pagamentoPedidoRepository
                .buscarParaAtualizacao(pagamentoId, pedido.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pagamento não encontrado para este pedido"));

        if (pagamento.getStatus() != StatusPagamentoPedido.PENDENTE_CONCILIACAO) {
            throw new PedidoEstadoInvalidoException(
                    "Somente pagamentos com status PENDENTE_CONCILIACAO podem ser conciliados manualmente"
            );
        }

        String motivo = normalizarTexto(dto.motivo());
        String comprovante = normalizarTexto(dto.comprovanteReferencia());
        if (comprovante != null) {
            pagamento.setComprovanteReferencia(comprovante);
        }

        Map<String, Object> estadoAnterior = snapshotPagamento(pagamento);
        LocalDateTime agora = LocalDateTime.now();

        if (Boolean.TRUE.equals(dto.aprovado())) {
            pagamento.setStatus(StatusPagamentoPedido.APROVADO);
            pagamento.setPagoEm(agora);
        } else {
            if (motivo == null) {
                throw new IllegalArgumentException(
                        "Informe o motivo ao cancelar/rejeitar um pagamento na conciliação manual"
                );
            }
            pagamento.setStatus(StatusPagamentoPedido.CANCELADO);
            pagamento.setCanceladoEm(agora);
            pagamento.setCanceladoPorUsuarioId(usuarioId);
            pagamento.setMotivoCancelamento(motivo);
        }

        PagamentoPedidoEntity pagamentoSalvo = pagamentoPedidoRepository.saveAndFlush(pagamento);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pagamentoSalvo.getSessaoCaixaId(),
                Boolean.TRUE.equals(dto.aprovado())
                        ? EventoAuditoriaCaixa.CONCILIACAO_MANUAL
                        : EventoAuditoriaCaixa.PAGAMENTO_CANCELADO,
                EventoAuditoriaCaixa.ENTIDADE_PAGAMENTO_PEDIDO,
                pagamentoSalvo.getId().toString(),
                Boolean.TRUE.equals(dto.aprovado())
                        ? "Pagamento manual aprovado na conciliação"
                        : "Pagamento manual cancelado na conciliação",
                pagamentoSalvo.getValor(),
                motivo,
                pedido.getId(),
                pedido.getIdentificadorAtendimento(),
                pagamentoSalvo.getId(),
                null,
                null,
                objectMapper.valueToTree(estadoAnterior),
                objectMapper.valueToTree(snapshotPagamento(pagamentoSalvo))
        );

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<PagamentoPedidoEntity> todosPagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        atualizarStatusPedidoAposPagamento(pedido, itens, todosPagamentos, usuarioId);
        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        return montarDetalhe(pedidoSalvo, itens, todosPagamentos);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            || @permissionAuthorizationService.hasPermission(
                'CAIXA_ESTORNAR',
                authentication
            )
            """)
    public PedidoCaixaDetalheResponseDto estornarPedido(
            UUID id,
            EstornarPedidoCaixaRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarParaAtualizacao(id);
        if (pedido.getStatus() != StatusPedidoCaixa.CONCLUIDO) {
            throw new PedidoEstadoInvalidoException(
                    "Somente pedidos com status CONCLUIDO podem ser estornados"
            );
        }

        var autorizador = autorizadorOperacaoCaixaService.exigirAutorizador(
                "CAIXA_ESTORNAR",
                dto.autorizadorEmail(),
                dto.autorizadorSenha(),
                "Permissão CAIXA_ESTORNAR necessária para estornar um pedido concluído"
        );

        String motivo = dto.motivo().trim();
        LocalDateTime agora = LocalDateTime.now();

        List<ItemPedidoEntity> itens = itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        List<PagamentoPedidoEntity> pagamentos =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        for (PagamentoPedidoEntity pagamento : pagamentos) {
            if (pagamento.getStatus() == StatusPagamentoPedido.APROVADO) {
                BigDecimal baseEstorno = pagamento.getValorCobrado() != null
                        ? pagamento.getValorCobrado()
                        : pagamento.getValor();
                pagamento.setStatus(StatusPagamentoPedido.ESTORNADO);
                pagamento.setValorEstornado(baseEstorno.setScale(2, RoundingMode.HALF_UP));
                pagamento.setMotivoEstorno(motivo);
                pagamento.setEstornadoEm(agora);
                pagamento.setEstornadoPorUsuarioId(autorizador.usuarioId());
                pagamentoPedidoRepository.save(pagamento);
            }
        }
        pagamentoPedidoRepository.flush();

        movimentacaoEstoqueService.estornarEstoquePedido(pedido, itens, autorizador.usuarioId());

        pedido.setStatus(StatusPedidoCaixa.ESTORNADO);
        pedido.setMotivoEstorno(motivo);
        pedido.setEstornadoPorUsuarioId(autorizador.usuarioId());
        pedido.setEstornadoEm(agora);

        PedidoEntity pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pedidoSalvo.getSessaoCaixaId(),
                EventoAuditoriaCaixa.PAGAMENTO_ESTORNADO,
                EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                pedidoSalvo.getId().toString(),
                "Pedido #" + pedidoSalvo.getNumero() + " estornado integralmente com devolução ao estoque",
                pedidoSalvo.getTotal(),
                motivo,
                pedidoSalvo.getId(),
                pedidoSalvo.getIdentificadorAtendimento(),
                null,
                autorizador.usuarioId(),
                autorizador.nome(),
                null,
                objectMapper.valueToTree(snapshotPedido(pedidoSalvo, itens))
        );

        businessEventLogger.success("cash.order.refunded", "cash_order", pedidoSalvo.getId());

        return montarDetalhe(pedidoSalvo, itens, pagamentos);
    }

    private void atualizarStatusPedidoAposPagamento(
            PedidoEntity pedido,
            List<ItemPedidoEntity> itens,
            List<PagamentoPedidoEntity> pagamentos,
            UUID operadorUsuarioId
    ) {
        BigDecimal totalAprovado = PedidoCaixaMapper.calcularTotalPagoAprovado(pagamentos);
        boolean possuiPendente = pagamentos.stream().anyMatch(p ->
                p.getStatus() == StatusPagamentoPedido.PENDENTE
                        || p.getStatus() == StatusPagamentoPedido.PENDENTE_CONCILIACAO
                        || p.getStatus() == StatusPagamentoPedido.PENDENTE_NA_POINT
                        || p.getStatus() == StatusPagamentoPedido.EM_TERMINAL
        );

        if (totalAprovado.compareTo(pedido.getTotal()) >= 0 && pedido.getTotal().compareTo(ZERO) > 0) {
            movimentacaoEstoqueService.baixarEstoquePedido(pedido, itens, operadorUsuarioId);
            pedido.setStatus(StatusPedidoCaixa.CONCLUIDO);
            pedido.setConcluidoEm(LocalDateTime.now());
            businessEventLogger.success("cash.order.completed", "cash_order", pedido.getId());
            return;
        }

        if (totalAprovado.compareTo(ZERO) > 0) {
            pedido.setStatus(StatusPedidoCaixa.PAGAMENTO_PARCIAL);
        } else if (possuiPendente) {
            pedido.setStatus(StatusPedidoCaixa.AGUARDANDO_PAGAMENTO);
        } else {
            pedido.setStatus(StatusPedidoCaixa.EM_ATENDIMENTO);
        }
    }

    private PagamentoPedidoEntity salvarPagamentoComIdempotencia(PagamentoPedidoEntity pagamento) {
        try {
            return pagamentoPedidoRepository.saveAndFlush(pagamento);
        } catch (DataIntegrityViolationException excecao) {
            if (pagamento.getChaveIdempotencia() != null) {
                return pagamentoPedidoRepository
                        .findByPedidoIdAndChaveIdempotencia(pagamento.getPedidoId(), pagamento.getChaveIdempotencia())
                        .orElseThrow(() -> excecao);
            }
            throw excecao;
        }
    }

    private Long gerarProximoNumeroPedido(UUID estabelecimentoId) {
        SequenciaDocumentoEntity sequencia = sequenciaDocumentoRepository
                .buscarParaAtualizacao(estabelecimentoId, TIPO_DOCUMENTO_PEDIDO)
                .orElseGet(() -> {
                    SequenciaDocumentoEntity nova = new SequenciaDocumentoEntity();
                    nova.setEstabelecimentoId(estabelecimentoId);
                    nova.setTipo(TIPO_DOCUMENTO_PEDIDO);
                    nova.setUltimoNumero(0L);
                    nova.setAtualizadoEm(LocalDateTime.now());
                    return sequenciaDocumentoRepository.saveAndFlush(nova);
                });

        long proximo = (sequencia.getUltimoNumero() == null ? 0L : sequencia.getUltimoNumero()) + 1L;
        sequencia.setUltimoNumero(proximo);
        sequencia.setAtualizadoEm(LocalDateTime.now());
        sequenciaDocumentoRepository.saveAndFlush(sequencia);
        return proximo;
    }

    private SessaoCaixaEntity resolverSessaoAberta(
            UUID sessaoCaixaIdInformada,
            UUID estabelecimentoId,
            UUID usuarioId
    ) {
        if (sessaoCaixaIdInformada != null) {
            SessaoCaixaEntity sessao = sessaoCaixaRepository
                    .findByIdAndEstabelecimentoId(sessaoCaixaIdInformada, estabelecimentoId)
                    .orElseThrow(() -> new EntityNotFoundException("Sessão de caixa não encontrada"));
            if (!sessao.isAberta()) {
                throw new SessaoCaixaEstadoInvalidoException(
                        "A sessão de caixa informada não está aberta"
                );
            }
            if (!sessao.getOperadorUsuarioId().equals(usuarioId) && !contextoCaixa.isGerente()) {
                throw new AccessDeniedException(
                        "Somente o operador titular da sessão ou um gerente pode abrir pedidos nesta sessão"
                );
            }
            return sessao;
        }

        return sessaoCaixaRepository
                .findFirstByEstabelecimentoIdAndOperadorUsuarioIdAndStatusOrderByAbertaEmDesc(
                        estabelecimentoId,
                        usuarioId,
                        StatusSessaoCaixa.ABERTA
                )
                .orElseThrow(() -> new SessaoCaixaEstadoInvalidoException(
                        "Abra uma sessão de caixa antes de iniciar um pedido"
                ));
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

    private void validarEstoqueDosItens(
            UUID estabelecimentoId,
            List<ItemPedidoCaixaRequestDto> itens
    ) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        List<MovimentacaoEstoqueService.ItemConsumoValidacao> itensValidacao = itens.stream()
                .map(i -> new MovimentacaoEstoqueService.ItemConsumoValidacao(i.produtoId(), i.quantidade()))
                .toList();
        movimentacaoEstoqueService.validarDisponibilidadeItens(estabelecimentoId, itensValidacao);
    }

    private List<ItemPedidoEntity> salvarItensDoPedido(
            UUID pedidoId,
            UUID estabelecimentoId,
            List<ItemPedidoCaixaRequestDto> itensRequisitados
    ) {
        if (itensRequisitados == null || itensRequisitados.isEmpty()) {
            return List.of();
        }

        Map<UUID, BigDecimal> quantidadesAgrupadas = new LinkedHashMap<>();
        for (ItemPedidoCaixaRequestDto req : itensRequisitados) {
            quantidadesAgrupadas.merge(req.produtoId(), req.quantidade(), BigDecimal::add);
        }

        List<ItemPedidoEntity> entidades = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantidadesAgrupadas.entrySet()) {
            ProdutoEntity produto = produtoRepository.findById(entry.getKey())
                    .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
            if (!produto.getEstabelecimentoId().equals(estabelecimentoId)) {
                throw new AccessDeniedException("Acesso negado ao produto informado");
            }
            if (!produto.isAtivo()) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " está inativo");
            }

            BigDecimal quantidade = entry.getValue().setScale(3, RoundingMode.HALF_UP);
            BigDecimal precoUnitario = produto.getPrecoVenda().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalItem = precoUnitario.multiply(quantidade).setScale(2, RoundingMode.HALF_UP);
            BigDecimal custoUnitario = movimentacaoEstoqueService.calcularCustoUnitario(produto);
            CategoriaEntity categoria = produto.getCategoria();

            ItemPedidoEntity item = new ItemPedidoEntity();
            item.setPedidoId(pedidoId);
            item.setProdutoId(produto.getId());
            item.setDescricaoProduto(produto.getNome());
            item.setQuantidade(quantidade);
            item.setPrecoUnitario(precoUnitario);
            item.setCustoUnitario(custoUnitario);
            item.setDesconto(ZERO);
            item.setSubtotal(subtotalItem);
            if (categoria != null) {
                item.setCategoriaId(categoria.getId());
                item.setCategoriaNome(categoria.getNome());
            }
            entidades.add(item);
        }

        return itemPedidoRepository.saveAllAndFlush(entidades);
    }

    private void recalcularTotaisComItens(PedidoEntity pedido, List<ItemPedidoEntity> itens) {
        BigDecimal subtotal = itens.stream()
                .map(ItemPedidoEntity::getSubtotal)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);

        if (subtotal.compareTo(ZERO) == 0) {
            limparCamposDeDesconto(pedido);
            pedido.setTotal(ZERO);
            return;
        }

        if (pedido.getStatusDesconto() == StatusDescontoPedido.APROVADO && pedido.getTipoDesconto() != null) {
            BigDecimal novoDesconto = calcularValorDesconto(
                    subtotal,
                    pedido.getTipoDesconto(),
                    pedido.getValorDescontoInformado()
            );
            if (novoDesconto.compareTo(subtotal) >= 0) {
                limparCamposDeDesconto(pedido);
                pedido.setTotal(subtotal);
            } else {
                pedido.setDesconto(novoDesconto);
                pedido.setTotal(subtotal.subtract(novoDesconto).setScale(2, RoundingMode.HALF_UP));
            }
            return;
        }

        if (pedido.getStatusDesconto() == StatusDescontoPedido.PENDENTE_AUTORIZACAO && pedido.getTipoDesconto() != null) {
            BigDecimal novoSolicitado = calcularValorDesconto(
                    subtotal,
                    pedido.getTipoDesconto(),
                    pedido.getValorDescontoInformado()
            );
            if (novoSolicitado.compareTo(subtotal) >= 0) {
                limparCamposDeDesconto(pedido);
            } else {
                pedido.setDescontoSolicitado(novoSolicitado);
            }
        }

        pedido.setDesconto(ZERO);
        pedido.setTotal(subtotal);
    }

    private void auditarItensRemovidosOuReduzidos(
            PedidoEntity pedido,
            List<ItemPedidoEntity> itensAnteriores,
            List<ItemPedidoCaixaRequestDto> novosItens,
            UUID usuarioId
    ) {
        if (itensAnteriores.isEmpty()) {
            return;
        }
        Map<UUID, BigDecimal> novasQuantidades = new HashMap<>();
        for (ItemPedidoCaixaRequestDto item : novosItens) {
            novasQuantidades.merge(item.produtoId(), item.quantidade(), BigDecimal::add);
        }

        for (ItemPedidoEntity itemAnterior : itensAnteriores) {
            BigDecimal novaQtd = novasQuantidades.getOrDefault(itemAnterior.getProdutoId(), BigDecimal.ZERO);
            if (novaQtd.compareTo(itemAnterior.getQuantidade()) < 0) {
                BigDecimal diferencaQtd = itemAnterior.getQuantidade().subtract(novaQtd);
                BigDecimal valorRemovido = itemAnterior.getPrecoUnitario()
                        .multiply(diferencaQtd)
                        .setScale(2, RoundingMode.HALF_UP);

                Map<String, Object> dadosItem = new LinkedHashMap<>();
                dadosItem.put("produtoId", itemAnterior.getProdutoId().toString());
                dadosItem.put("descricaoProduto", itemAnterior.getDescricaoProduto());
                dadosItem.put("quantidadeAnterior", itemAnterior.getQuantidade());
                dadosItem.put("quantidadeNova", novaQtd);
                dadosItem.put("quantidadeRemovida", diferencaQtd);
                dadosItem.put("valorRemovido", valorRemovido);

                auditoriaCaixaService.registrar(
                        pedido.getEstabelecimentoId(),
                        usuarioId,
                        pedido.getSessaoCaixaId(),
                        EventoAuditoriaCaixa.ITEM_REMOVIDO,
                        EventoAuditoriaCaixa.ENTIDADE_PEDIDO,
                        pedido.getId().toString(),
                        "Item removido ou reduzido no pedido #" + pedido.getNumero() + ": " + itemAnterior.getDescricaoProduto(),
                        valorRemovido,
                        null,
                        pedido.getId(),
                        pedido.getIdentificadorAtendimento(),
                        null,
                        null,
                        null,
                        objectMapper.valueToTree(dadosItem),
                        null
                );
            }
        }
    }

    private BigDecimal calcularValorDesconto(
            BigDecimal subtotal,
            TipoDescontoPedido tipo,
            BigDecimal valorInformado
    ) {
        if (tipo == TipoDescontoPedido.PERCENTUAL) {
            if (valorInformado.compareTo(CEM) >= 0) {
                throw new IllegalArgumentException("O desconto percentual deve ser menor que 100%");
            }
            return subtotal.multiply(valorInformado)
                    .divide(CEM, 2, RoundingMode.HALF_UP);
        }
        return valorInformado.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularLimiteDescontoSemAutorizacao(
            BigDecimal subtotal,
            ConfiguracaoCaixaEntity configuracao
    ) {
        BigDecimal limitePercentual = configuracao.getLimiteDescontoPercentual() == null
                ? ZERO
                : configuracao.getLimiteDescontoPercentual();
        BigDecimal limiteValor = configuracao.getLimiteDescontoValor() == null
                ? ZERO
                : configuracao.getLimiteDescontoValor().setScale(2, RoundingMode.HALF_UP);

        BigDecimal tetoPorPercentual = subtotal.multiply(limitePercentual)
                .divide(CEM, 2, RoundingMode.HALF_UP);

        return tetoPorPercentual.min(limiteValor).setScale(2, RoundingMode.HALF_UP);
    }

    private void limparCamposDeDesconto(PedidoEntity pedido) {
        pedido.setTipoDesconto(null);
        pedido.setValorDescontoInformado(null);
        pedido.setDesconto(ZERO);
        pedido.setStatusDesconto(StatusDescontoPedido.NENHUM);
        pedido.setDescontoSolicitado(null);
        pedido.setMotivoDesconto(null);
        pedido.setDescontoDecididoPorUsuarioId(null);
        pedido.setDescontoDecididoEm(null);
        pedido.setObservacaoDecisaoDesconto(null);
    }

    private void validarCanalEIdentificador(CanalPedidoCaixa canal, String identificador) {
        if (canal == CanalPedidoCaixa.MESA && (identificador == null || identificador.isBlank())) {
            throw new IllegalArgumentException(
                    "O identificador do atendimento (ex.: Mesa 04) é obrigatório quando o canal é MESA"
            );
        }
    }

    private void exigirPedidoEditavelParaItens(PedidoEntity pedido) {
        if (pedido.getStatus() != StatusPedidoCaixa.EM_ATENDIMENTO) {
            throw new PedidoEstadoInvalidoException(
                    "Somente pedidos com status EM_ATENDIMENTO permitem edição de itens"
            );
        }
    }

    private void exigirPedidoAbertoParaDesconto(PedidoEntity pedido) {
        if (pedido.getStatus() == StatusPedidoCaixa.CONCLUIDO
                || pedido.getStatus() == StatusPedidoCaixa.CANCELADO
                || pedido.getStatus() == StatusPedidoCaixa.ESTORNADO) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível alterar desconto de um pedido com status " + pedido.getStatus()
            );
        }
    }

    private void validarPedidoAptoParaPagamento(PedidoEntity pedido) {
        if (pedido.getStatus() == StatusPedidoCaixa.CONCLUIDO
                || pedido.getStatus() == StatusPedidoCaixa.CANCELADO
                || pedido.getStatus() == StatusPedidoCaixa.ESTORNADO) {
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

    private PedidoEntity buscarDoEstabelecimento(UUID id) {
        PedidoEntity pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        if (!pedido.getEstabelecimentoId().equals(contextoCaixa.estabelecimentoId())) {
            throw new AccessDeniedException("Acesso negado");
        }
        return pedido;
    }

    private PedidoEntity buscarParaAtualizacao(UUID id) {
        PedidoEntity pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        if (!pedido.getEstabelecimentoId().equals(contextoCaixa.estabelecimentoId())) {
            throw new AccessDeniedException("Acesso negado");
        }
        return pedidoRepository.buscarParaAtualizacao(id, contextoCaixa.estabelecimentoId())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
    }

    private ConfiguracaoCaixaEntity obterConfiguracao(UUID estabelecimentoId) {
        return configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId)
                .orElseGet(() -> ConfiguracaoCaixaMapper.criarPadrao(estabelecimentoId));
    }

    private PedidoCaixaDetalheResponseDto montarDetalhe(
            PedidoEntity pedido,
            List<ItemPedidoEntity> itens,
            List<PagamentoPedidoEntity> pagamentos
    ) {
        Set<UUID> idsUsuarios = java.util.stream.Stream.of(
                        pedido.getOperadorUsuarioId(),
                        pedido.getDescontoDecididoPorUsuarioId()
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> nomes = carregarNomesUsuarios(idsUsuarios);
        String operadorNome = nomes.getOrDefault(pedido.getOperadorUsuarioId(), "Operador");
        String autorizadorDescontoNome = pedido.getDescontoDecididoPorUsuarioId() == null
                ? null
                : nomes.get(pedido.getDescontoDecididoPorUsuarioId());

        return PedidoCaixaMapper.paraDetalhe(
                pedido,
                operadorNome,
                autorizadorDescontoNome,
                itens,
                pagamentos
        );
    }

    private Map<UUID, String> carregarNomesUsuarios(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    private Map<String, Object> snapshotPedido(PedidoEntity pedido, List<ItemPedidoEntity> itens) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", pedido.getId().toString());
        map.put("numero", pedido.getNumero());
        map.put("status", pedido.getStatus().name());
        map.put("canal", pedido.getCanal().name());
        map.put("identificadorAtendimento", pedido.getIdentificadorAtendimento());
        map.put("subtotal", pedido.getSubtotal());
        map.put("desconto", pedido.getDesconto());
        map.put("total", pedido.getTotal());
        map.put("quantidadeItens", itens == null ? 0 : itens.size());
        return map;
    }

    private Map<String, Object> snapshotDesconto(PedidoEntity pedido) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("statusDesconto", pedido.getStatusDesconto().name());
        map.put("tipoDesconto", pedido.getTipoDesconto() == null ? null : pedido.getTipoDesconto().name());
        map.put("valorDescontoInformado", pedido.getValorDescontoInformado());
        map.put("desconto", pedido.getDesconto());
        map.put("descontoSolicitado", pedido.getDescontoSolicitado());
        map.put("total", pedido.getTotal());
        map.put("motivoDesconto", pedido.getMotivoDesconto());
        map.put("descontoDecididoPorUsuarioId",
                pedido.getDescontoDecididoPorUsuarioId() == null
                        ? null
                        : pedido.getDescontoDecididoPorUsuarioId().toString());
        map.put("observacaoDecisaoDesconto", pedido.getObservacaoDecisaoDesconto());
        return map;
    }

    private Map<String, Object> snapshotPagamento(PagamentoPedidoEntity pagamento) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", pagamento.getId().toString());
        map.put("pedidoId", pagamento.getPedidoId().toString());
        map.put("sessaoCaixaId", pagamento.getSessaoCaixaId() == null ? null : pagamento.getSessaoCaixaId().toString());
        map.put("forma", pagamento.getForma().name());
        map.put("status", pagamento.getStatus().name());
        map.put("provedor", pagamento.getProvedor());
        map.put("valor", pagamento.getValor());
        map.put("valorCobrado", pagamento.getValorCobrado());
        map.put("valorRecebido", pagamento.getValorRecebido());
        map.put("valorTroco", pagamento.getValorTroco());
        map.put("parcelas", pagamento.getParcelas());
        map.put("comprovanteReferencia", pagamento.getComprovanteReferencia());
        map.put("pagoEm", pagamento.getPagoEm() == null ? null : pagamento.getPagoEm().toString());
        map.put("canceladoEm", pagamento.getCanceladoEm() == null ? null : pagamento.getCanceladoEm().toString());
        return map;
    }

    private String normalizarTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
