package valhalla.core.stock.app.modules.caixa.fiscal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.exception.AutorizacaoCaixaNegadaException;
import valhalla.core.stock.app.modules.caixa.exception.NotaFiscalEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.exception.NotaFiscalJaEmitidaException;
import valhalla.core.stock.app.modules.caixa.exception.PedidoEstadoInvalidoException;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.CancelarNotaFiscalRequestDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.EmitirNfceRequestDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.ItemNotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.NotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.TipoNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.mapper.NotaFiscalMapper;
import valhalla.core.stock.app.modules.caixa.fiscal.repository.ItemNotaFiscalRepository;
import valhalla.core.stock.app.modules.caixa.fiscal.repository.NotaFiscalRepository;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ItemEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ItemTributadoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ResultadoCancelamentoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ResultadoEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.SolicitacaoEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.SequenciaDocumentoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.mapper.PedidoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ItemPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PagamentoPedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.PedidoRepository;
import valhalla.core.stock.app.modules.caixa.pedido.repository.SequenciaDocumentoRepository;
import valhalla.core.stock.app.modules.caixa.security.AutorizadorOperacaoCaixaService;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.modules.caixa.sessao.service.EventoAuditoriaCaixa;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class NotaFiscalCaixaService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String TIPO_SEQUENCIA_NFCE = "NFCE";
    private static final String SERIE_PADRAO = "1";
    private static final int TAMANHO_MINIMO_MOTIVO_CANCELAMENTO = 15;

    private static final Set<String> CAMPOS_ORDENACAO =
            Set.of("emitidaEm", "criadaEm", "atualizadaEm", "numero", "valorTotal", "status");
    private static final Set<String> CAMPOS_ORDENACAO_PEDIDOS =
            Set.of("concluidoEm", "criadoEm", "numero", "total");

    private final NotaFiscalRepository notaFiscalRepository;
    private final ItemNotaFiscalRepository itemNotaFiscalRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PagamentoPedidoRepository pagamentoPedidoRepository;
    private final SequenciaDocumentoRepository sequenciaDocumentoRepository;
    private final ProdutoRepository produtoRepository;
    private final EstablishmentRepository establishmentRepository;
    private final UserRepository userRepository;
    private final ProvedorEmissaoFiscal provedorEmissaoFiscal;
    private final AutorizadorOperacaoCaixaService autorizadorOperacaoCaixaService;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final ContextoCaixa contextoCaixa;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ArquivoXmlNotaFiscal(String nomeArquivo, String conteudoXml) {
    }

    public NotaFiscalCaixaService(
            NotaFiscalRepository notaFiscalRepository,
            ItemNotaFiscalRepository itemNotaFiscalRepository,
            PedidoRepository pedidoRepository,
            ItemPedidoRepository itemPedidoRepository,
            PagamentoPedidoRepository pagamentoPedidoRepository,
            SequenciaDocumentoRepository sequenciaDocumentoRepository,
            ProdutoRepository produtoRepository,
            EstablishmentRepository establishmentRepository,
            UserRepository userRepository,
            ProvedorEmissaoFiscal provedorEmissaoFiscal,
            AutorizadorOperacaoCaixaService autorizadorOperacaoCaixaService,
            AuditoriaCaixaService auditoriaCaixaService,
            ContextoCaixa contextoCaixa,
            BusinessEventLogger businessEventLogger
    ) {
        this.notaFiscalRepository = notaFiscalRepository;
        this.itemNotaFiscalRepository = itemNotaFiscalRepository;
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.pagamentoPedidoRepository = pagamentoPedidoRepository;
        this.sequenciaDocumentoRepository = sequenciaDocumentoRepository;
        this.produtoRepository = produtoRepository;
        this.establishmentRepository = establishmentRepository;
        this.userRepository = userRepository;
        this.provedorEmissaoFiscal = provedorEmissaoFiscal;
        this.autorizadorOperacaoCaixaService = autorizadorOperacaoCaixaService;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.contextoCaixa = contextoCaixa;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            """)
    public NotaFiscalDetalheResponseDto emitirNfce(EmitirNfceRequestDto dto) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        PedidoEntity pedido = buscarPedidoParaAtualizacao(dto.pedidoId(), estabelecimentoId);
        if (pedido.getStatus() != StatusPedidoCaixa.CONCLUIDO) {
            throw new PedidoEstadoInvalidoException(
                    "Somente pedidos com status CONCLUIDO permitem emissão de NFC-e (status atual: "
                            + pedido.getStatus() + ")"
            );
        }

        if (notaFiscalRepository.existsByPedidoIdAndStatus(pedido.getId(), StatusNotaFiscal.AUTORIZADA)) {
            throw new NotaFiscalJaEmitidaException(
                    "O pedido #" + pedido.getNumero() + " já possui uma NFC-e autorizada ativa"
            );
        }

        List<ItemPedidoEntity> itensPedido =
                itemPedidoRepository.findByPedidoIdOrderByIdAsc(pedido.getId());
        if (itensPedido.isEmpty() || pedido.getTotal().compareTo(ZERO) <= 0) {
            throw new PedidoEstadoInvalidoException(
                    "Não é possível emitir NFC-e para um pedido sem itens ou com valor total zerado"
            );
        }

        List<PagamentoPedidoEntity> pagamentosAprovados =
                pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId()).stream()
                        .filter(PagamentoPedidoEntity::estaAprovado)
                        .toList();

        String documentoNormalizado = normalizarEValidarDocumentoConsumidor(
                dto.documentoCliente() != null ? dto.documentoCliente() : pedido.getDocumentoCliente()
        );
        String nomeNormalizado = normalizarTexto(
                dto.nomeCliente() != null ? dto.nomeCliente() : pedido.getNomeCliente()
        );

        // Atualiza os dados do cliente no pedido caso tenham sido informados no pós-venda
        boolean pedidoAlterado = false;
        if (documentoNormalizado != null && !Objects.equals(documentoNormalizado, pedido.getDocumentoCliente())) {
            pedido.setDocumentoCliente(documentoNormalizado);
            pedidoAlterado = true;
        }
        if (nomeNormalizado != null && !Objects.equals(nomeNormalizado, pedido.getNomeCliente())) {
            pedido.setNomeCliente(nomeNormalizado);
            pedidoAlterado = true;
        }
        if (pedidoAlterado) {
            pedidoRepository.saveAndFlush(pedido);
        }

        EstablishmentEntity estabelecimento = establishmentRepository.findById(estabelecimentoId)
                .orElseThrow(() -> new EntityNotFoundException("Estabelecimento não encontrado"));

        Map<UUID, ProdutoEntity> produtosPorId = produtoRepository.findAllById(
                        itensPedido.stream().map(ItemPedidoEntity::getProdutoId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(ProdutoEntity::getId, Function.identity()));

        List<ItemEmissaoFiscal> itensEmissao = montarItensComRateioDeDesconto(
                itensPedido,
                produtosPorId,
                pedido.getSubtotal(),
                pedido.getDesconto()
        );

        Long numeroSequencial = gerarProximoNumeroNfce(estabelecimentoId);
        LocalDateTime agora = LocalDateTime.now();

        SolicitacaoEmissaoFiscal solicitacao = new SolicitacaoEmissaoFiscal(
                estabelecimento,
                pedido,
                numeroSequencial,
                SERIE_PADRAO,
                documentoNormalizado,
                nomeNormalizado,
                agora,
                itensEmissao,
                pagamentosAprovados
        );

        ResultadoEmissaoFiscal resultado = provedorEmissaoFiscal.emitirNfce(solicitacao);

        NotaFiscalEntity nota = new NotaFiscalEntity();
        nota.setEstabelecimentoId(estabelecimentoId);
        nota.setPedidoId(pedido.getId());
        nota.setTipo(TipoNotaFiscal.NFCE);
        nota.setModelo("65");
        nota.setAmbiente(resultado.ambiente());
        nota.setProvedor(resultado.provedor());
        nota.setStatus(resultado.status());
        nota.setNumero(resultado.numero());
        nota.setSerie(resultado.serie());
        nota.setChaveAcesso(resultado.chaveAcesso());
        nota.setProtocoloAutorizacao(resultado.protocoloAutorizacao());
        nota.setUrlConsulta(resultado.urlConsulta());
        nota.setContraparteNome(nomeNormalizado);
        nota.setContraparteDocumento(documentoNormalizado);
        nota.setValorProdutos(pedido.getSubtotal());
        nota.setValorDesconto(pedido.getDesconto());
        nota.setValorTotal(pedido.getTotal());
        nota.setDadosFiscais(resultado.dadosFiscais());
        nota.setXmlConteudo(resultado.xmlConteudo());
        nota.setEmitidaPorUsuarioId(usuarioId);
        nota.setEmitidaEm(agora);
        nota.setCriadaEm(agora);
        nota.setAtualizadaEm(agora);

        NotaFiscalEntity notaSalva = notaFiscalRepository.saveAndFlush(nota);

        Map<Long, ItemTributadoFiscal> tributosPorItemPedido = resultado.itensTributados().stream()
                .collect(Collectors.toMap(ItemTributadoFiscal::itemPedidoId, Function.identity()));

        List<ItemNotaFiscalEntity> itensNotaSalvos = new ArrayList<>();
        for (ItemEmissaoFiscal itemEmissao : itensEmissao) {
            ItemPedidoEntity itemPedido = itemEmissao.itemPedido();
            ItemTributadoFiscal trib = tributosPorItemPedido.get(itemPedido.getId());

            ItemNotaFiscalEntity itemNota = new ItemNotaFiscalEntity();
            itemNota.setNotaFiscalId(notaSalva.getId());
            itemNota.setProdutoId(itemPedido.getProdutoId());
            itemNota.setItemPedidoId(itemPedido.getId());
            itemNota.setDescricaoProduto(itemPedido.getDescricaoProduto());
            itemNota.setUnidadeMedida(itemEmissao.unidadeMedida());
            itemNota.setCfop(trib != null ? trib.cfop() : "5405");
            itemNota.setQuantidade(itemPedido.getQuantidade());
            itemNota.setPrecoUnitario(itemPedido.getPrecoUnitario());
            itemNota.setValorDesconto(itemEmissao.valorDesconto());
            itemNota.setValorTotal(itemEmissao.valorLiquido());
            itemNota.setDadosTributarios(trib != null ? trib.dadosTributarios() : null);

            itensNotaSalvos.add(itemNotaFiscalRepository.save(itemNota));
        }
        itemNotaFiscalRepository.flush();

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pedido.getSessaoCaixaId(),
                EventoAuditoriaCaixa.NFCE_EMITIDA,
                EventoAuditoriaCaixa.ENTIDADE_NOTA_FISCAL,
                notaSalva.getId().toString(),
                "NFC-e #" + notaSalva.getNumero() + " (série " + notaSalva.getSerie()
                        + ") emitida para o pedido #" + pedido.getNumero(),
                notaSalva.getValorTotal(),
                null,
                pedido.getId(),
                pedido.getIdentificadorAtendimento(),
                null,
                null,
                null,
                null,
                objectMapper.valueToTree(snapshotNotaFiscal(notaSalva, pedido.getNumero()))
        );

        businessEventLogger.success("cash.nfce.issued", "nfce", notaSalva.getId());

        return montarDetalhe(notaSalva, pedido, estabelecimento, itensNotaSalvos, pagamentosAprovados);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public RespostaPaginadaDto<NotaFiscalResumoResponseDto> listarNotasFiscais(
            StatusNotaFiscal status,
            UUID pedidoId,
            String documentoCliente,
            String numero,
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

        Specification<NotaFiscalEntity> especificacao =
                (root, query, builder) -> builder.equal(root.get("estabelecimentoId"), estabelecimentoId);

        if (status != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("status"), status)
            );
        }
        if (pedidoId != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("pedidoId"), pedidoId)
            );
        }
        if (documentoCliente != null && !documentoCliente.isBlank()) {
            String digitos = documentoCliente.replaceAll("\\D", "");
            String filtroDoc = digitos.isEmpty() ? documentoCliente.trim() : digitos;
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("contraparteDocumento"), filtroDoc)
            );
        }
        if (numero != null && !numero.isBlank()) {
            String numeroLimpo = numero.trim();
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.equal(root.get("numero"), numeroLimpo)
            );
        }
        if (dataInicio != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.greaterThanOrEqualTo(
                            root.get("emitidaEm"), dataInicio.atStartOfDay()
                    )
            );
        }
        if (dataFim != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.lessThan(
                            root.get("emitidaEm"), dataFim.plusDays(1).atStartOfDay()
                    )
            );
        }

        Page<NotaFiscalEntity> resultado = notaFiscalRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO)
        );

        Set<UUID> pedidoIds = resultado.getContent().stream()
                .map(NotaFiscalEntity::getPedidoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Long> numerosPedidos = pedidoIds.isEmpty()
                ? Map.of()
                : pedidoRepository.findAllById(pedidoIds).stream()
                .collect(Collectors.toMap(PedidoEntity::getId, PedidoEntity::getNumero));

        return RespostaPaginadaDto.de(
                resultado,
                nota -> NotaFiscalMapper.paraResumo(nota, numerosPedidos.get(nota.getPedidoId()))
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public RespostaPaginadaDto<PedidoCaixaResumoResponseDto> listarPedidosElegiveisParaEmissao(
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

        Specification<PedidoEntity> especificacao = (root, query, builder) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<NotaFiscalEntity> notaRoot = subquery.from(NotaFiscalEntity.class);
            subquery.select(notaRoot.get("pedidoId"))
                    .where(
                            builder.equal(notaRoot.get("pedidoId"), root.get("id")),
                            builder.equal(notaRoot.get("status"), StatusNotaFiscal.AUTORIZADA)
                    );

            return builder.and(
                    builder.equal(root.get("estabelecimentoId"), estabelecimentoId),
                    builder.equal(root.get("status"), StatusPedidoCaixa.CONCLUIDO),
                    builder.not(builder.exists(subquery))
            );
        };

        if (dataInicio != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.greaterThanOrEqualTo(
                            root.get("concluidoEm"), dataInicio.atStartOfDay()
                    )
            );
        }
        if (dataFim != null) {
            especificacao = especificacao.and(
                    (root, query, builder) -> builder.lessThan(
                            root.get("concluidoEm"), dataFim.plusDays(1).atStartOfDay()
                    )
            );
        }

        Page<PedidoEntity> resultado = pedidoRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO_PEDIDOS)
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

        Map<UUID, NotaFiscalEntity> ultimaNotaPorPedido = carregarUltimaNotaPorPedido(pedidoIds);

        Map<UUID, String> nomesUsuarios = carregarNomesUsuarios(
                pedidos.stream().map(PedidoEntity::getOperadorUsuarioId).collect(Collectors.toSet())
        );

        return RespostaPaginadaDto.de(
                resultado,
                pedido -> PedidoCaixaMapper.paraResumo(
                        pedido,
                        nomesUsuarios.getOrDefault(pedido.getOperadorUsuarioId(), "Operador"),
                        itensPorPedido.getOrDefault(pedido.getId(), List.of()).size(),
                        pagamentosPorPedido.getOrDefault(pedido.getId(), List.of()),
                        NotaFiscalMapper.paraVinculoPedido(ultimaNotaPorPedido.get(pedido.getId()))
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
    public NotaFiscalDetalheResponseDto buscarNotaFiscal(UUID id) {
        NotaFiscalEntity nota = buscarDoEstabelecimento(id);
        return carregarDetalheCompleto(nota);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public ArquivoXmlNotaFiscal obterXmlNotaFiscal(UUID id) {
        NotaFiscalEntity nota = buscarDoEstabelecimento(id);
        if (nota.getXmlConteudo() == null || nota.getXmlConteudo().isBlank()) {
            throw new EntityNotFoundException("O XML da nota fiscal não está disponível");
        }
        String nomeArquivo = "nfce-" + nota.getSerie() + "-" + nota.getNumero() + "-" + nota.getChaveAcesso() + ".xml";
        return new ArquivoXmlNotaFiscal(nomeArquivo, nota.getXmlConteudo());
    }

    @Transactional(noRollbackFor = AutorizacaoCaixaNegadaException.class)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_OPERAR',
                authentication
            )
            || @permissionAuthorizationService.hasPermission(
                'CAIXA_CANCELAR',
                authentication
            )
            || @permissionAuthorizationService.hasPermission(
                'CAIXA_ESTORNAR',
                authentication
            )
            """)
    public NotaFiscalDetalheResponseDto cancelarNotaFiscal(
            UUID id,
            CancelarNotaFiscalRequestDto dto
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        NotaFiscalEntity nota = buscarDoEstabelecimento(id);
        if (nota.getStatus() != StatusNotaFiscal.AUTORIZADA) {
            throw new NotaFiscalEstadoInvalidoException(
                    "Somente uma NFC-e com status AUTORIZADA pode ser cancelada (status atual: "
                            + nota.getStatus() + ")"
            );
        }

        String motivo = dto != null && dto.motivo() != null ? dto.motivo().trim() : "";
        if (motivo.length() < TAMANHO_MINIMO_MOTIVO_CANCELAMENTO) {
            throw new IllegalArgumentException(
                    "O motivo do cancelamento fiscal deve ter no mínimo 15 caracteres"
            );
        }

        var autorizador = autorizadorOperacaoCaixaService.exigirAutorizador(
                "CAIXA_CANCELAR",
                dto.autorizadorEmail(),
                dto.autorizadorSenha(),
                "Permissão CAIXA_CANCELAR necessária para cancelar uma NFC-e emitida"
        );

        PedidoEntity pedido = nota.getPedidoId() != null
                ? pedidoRepository.findById(nota.getPedidoId()).orElse(null)
                : null;
        Long pedidoNumero = pedido != null ? pedido.getNumero() : null;

        Map<String, Object> dadosAnteriores = snapshotNotaFiscal(nota, pedidoNumero);

        ResultadoCancelamentoFiscal resultadoCancelamento =
                provedorEmissaoFiscal.cancelarNfce(nota, motivo);

        nota.setStatus(resultadoCancelamento.status());
        nota.setCanceladaEm(resultadoCancelamento.canceladaEm());
        nota.setCanceladaPorUsuarioId(autorizador.usuarioId());
        nota.setMotivoCancelamento(motivo);
        nota.setDadosFiscais(resultadoCancelamento.dadosFiscaisAtualizados());

        NotaFiscalEntity notaSalva = notaFiscalRepository.saveAndFlush(nota);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                pedido != null ? pedido.getSessaoCaixaId() : null,
                EventoAuditoriaCaixa.NFCE_CANCELADA,
                EventoAuditoriaCaixa.ENTIDADE_NOTA_FISCAL,
                notaSalva.getId().toString(),
                "NFC-e #" + notaSalva.getNumero() + " (série " + notaSalva.getSerie() + ") cancelada",
                notaSalva.getValorTotal(),
                motivo,
                pedido != null ? pedido.getId() : null,
                pedido != null ? pedido.getIdentificadorAtendimento() : null,
                null,
                autorizador.usuarioId(),
                autorizador.nome(),
                objectMapper.valueToTree(dadosAnteriores),
                objectMapper.valueToTree(snapshotNotaFiscal(notaSalva, pedidoNumero))
        );

        businessEventLogger.success("cash.nfce.cancelled", "nfce", notaSalva.getId());

        return carregarDetalheCompleto(notaSalva);
    }

    @Transactional
    public void cancelarAutomaticamenteNoEstornoDoPedido(
            PedidoEntity pedido,
            String motivoEstorno,
            UUID operadorUsuarioId,
            UUID autorizadorUsuarioId,
            String autorizadorNome
    ) {
        var notaAtivaOpt = notaFiscalRepository.findFirstByPedidoIdAndStatusOrderByCriadaEmDesc(
                pedido.getId(),
                StatusNotaFiscal.AUTORIZADA
        );
        if (notaAtivaOpt.isEmpty()) {
            return;
        }

        NotaFiscalEntity nota = notaAtivaOpt.get();
        Map<String, Object> dadosAnteriores = snapshotNotaFiscal(nota, pedido.getNumero());

        String motivoFiscal = motivoEstorno != null && motivoEstorno.trim().length() >= TAMANHO_MINIMO_MOTIVO_CANCELAMENTO
                ? motivoEstorno.trim()
                : "Cancelamento automático por estorno do pedido #" + pedido.getNumero()
                + (motivoEstorno != null && !motivoEstorno.isBlank() ? ": " + motivoEstorno.trim() : "");

        ResultadoCancelamentoFiscal resultadoCancelamento =
                provedorEmissaoFiscal.cancelarNfce(nota, motivoFiscal);

        nota.setStatus(resultadoCancelamento.status());
        nota.setCanceladaEm(resultadoCancelamento.canceladaEm());
        nota.setCanceladaPorUsuarioId(autorizadorUsuarioId);
        nota.setMotivoCancelamento(motivoFiscal);
        nota.setDadosFiscais(resultadoCancelamento.dadosFiscaisAtualizados());

        NotaFiscalEntity notaSalva = notaFiscalRepository.saveAndFlush(nota);

        auditoriaCaixaService.registrar(
                pedido.getEstabelecimentoId(),
                operadorUsuarioId,
                pedido.getSessaoCaixaId(),
                EventoAuditoriaCaixa.NFCE_CANCELADA,
                EventoAuditoriaCaixa.ENTIDADE_NOTA_FISCAL,
                notaSalva.getId().toString(),
                "NFC-e #" + notaSalva.getNumero() + " cancelada automaticamente pelo estorno do pedido #"
                        + pedido.getNumero(),
                notaSalva.getValorTotal(),
                motivoFiscal,
                pedido.getId(),
                pedido.getIdentificadorAtendimento(),
                null,
                autorizadorUsuarioId,
                autorizadorNome,
                objectMapper.valueToTree(dadosAnteriores),
                objectMapper.valueToTree(snapshotNotaFiscal(notaSalva, pedido.getNumero()))
        );

        businessEventLogger.success("cash.nfce.auto_cancelled_on_refund", "nfce", notaSalva.getId());
    }

    @Transactional(readOnly = true)
    public Map<UUID, NotaFiscalEntity> carregarUltimaNotaPorPedido(Collection<UUID> pedidoIds) {
        if (pedidoIds == null || pedidoIds.isEmpty()) {
            return Map.of();
        }
        List<NotaFiscalEntity> notas = notaFiscalRepository.findByPedidoIdInOrderByCriadaEmDesc(pedidoIds);
        Map<UUID, NotaFiscalEntity> resultado = new LinkedHashMap<>();
        for (NotaFiscalEntity nota : notas) {
            NotaFiscalEntity atual = resultado.get(nota.getPedidoId());
            if (atual == null
                    || (atual.getStatus() != StatusNotaFiscal.AUTORIZADA
                    && nota.getStatus() == StatusNotaFiscal.AUTORIZADA)) {
                resultado.put(nota.getPedidoId(), nota);
            }
        }
        return resultado;
    }

    private List<ItemEmissaoFiscal> montarItensComRateioDeDesconto(
            List<ItemPedidoEntity> itensPedido,
            Map<UUID, ProdutoEntity> produtosPorId,
            BigDecimal subtotalPedido,
            BigDecimal descontoTotalPedido
    ) {
        BigDecimal descontoTotal = descontoTotalPedido != null
                ? descontoTotalPedido.setScale(2, RoundingMode.HALF_UP)
                : ZERO;

        List<BigDecimal> descontosRateados = new ArrayList<>(itensPedido.size());
        BigDecimal descontoAcumulado = ZERO;

        if (descontoTotal.compareTo(ZERO) > 0 && subtotalPedido.compareTo(ZERO) > 0) {
            for (int i = 0; i < itensPedido.size(); i++) {
                ItemPedidoEntity item = itensPedido.get(i);
                if (i == itensPedido.size() - 1) {
                    BigDecimal restante = descontoTotal.subtract(descontoAcumulado).setScale(2, RoundingMode.HALF_UP);
                    if (restante.compareTo(ZERO) < 0) {
                        restante = ZERO;
                    }
                    if (restante.compareTo(item.getSubtotal()) > 0) {
                        restante = item.getSubtotal();
                    }
                    descontosRateados.add(restante);
                } else {
                    BigDecimal proporcional = item.getSubtotal()
                            .multiply(descontoTotal)
                            .divide(subtotalPedido, 2, RoundingMode.HALF_UP);
                    if (proporcional.compareTo(item.getSubtotal()) > 0) {
                        proporcional = item.getSubtotal();
                    }
                    if (descontoAcumulado.add(proporcional).compareTo(descontoTotal) > 0) {
                        proporcional = descontoTotal.subtract(descontoAcumulado).setScale(2, RoundingMode.HALF_UP);
                    }
                    descontosRateados.add(proporcional);
                    descontoAcumulado = descontoAcumulado.add(proporcional).setScale(2, RoundingMode.HALF_UP);
                }
            }
        } else {
            for (int i = 0; i < itensPedido.size(); i++) {
                descontosRateados.add(ZERO);
            }
        }

        List<ItemEmissaoFiscal> resultado = new ArrayList<>(itensPedido.size());
        for (int i = 0; i < itensPedido.size(); i++) {
            ItemPedidoEntity item = itensPedido.get(i);
            ProdutoEntity produto = produtosPorId.get(item.getProdutoId());
            ProdutoTipo tipoProduto = produto != null && produto.getTipo() != null
                    ? produto.getTipo()
                    : ProdutoTipo.PADRAO;
            String sku = produto != null ? produto.getSku() : null;
            String unidade = produto != null && produto.getUnidadeMedida() != null
                    ? produto.getUnidadeMedida().toUpperCase()
                    : "UN";

            BigDecimal valorBruto = item.getSubtotal().setScale(2, RoundingMode.HALF_UP);
            BigDecimal valorDesconto = descontosRateados.get(i);
            BigDecimal valorLiquido = valorBruto.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);

            resultado.add(new ItemEmissaoFiscal(
                    item,
                    tipoProduto,
                    sku,
                    unidade,
                    valorBruto,
                    valorDesconto,
                    valorLiquido
            ));
        }

        return resultado;
    }

    private Long gerarProximoNumeroNfce(UUID estabelecimentoId) {
        SequenciaDocumentoEntity sequencia = sequenciaDocumentoRepository
                .buscarParaAtualizacao(estabelecimentoId, TIPO_SEQUENCIA_NFCE)
                .orElseGet(() -> {
                    SequenciaDocumentoEntity nova = new SequenciaDocumentoEntity();
                    nova.setEstabelecimentoId(estabelecimentoId);
                    nova.setTipo(TIPO_SEQUENCIA_NFCE);
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

    private NotaFiscalDetalheResponseDto carregarDetalheCompleto(NotaFiscalEntity nota) {
        PedidoEntity pedido = nota.getPedidoId() != null
                ? pedidoRepository.findById(nota.getPedidoId()).orElse(null)
                : null;
        EstablishmentEntity estabelecimento = establishmentRepository.findById(nota.getEstabelecimentoId())
                .orElse(null);
        List<ItemNotaFiscalEntity> itensNota =
                itemNotaFiscalRepository.findByNotaFiscalIdOrderByIdAsc(nota.getId());
        List<PagamentoPedidoEntity> pagamentosAprovados = pedido != null
                ? pagamentoPedidoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId()).stream()
                .filter(PagamentoPedidoEntity::estaAprovado)
                .toList()
                : List.of();

        return montarDetalhe(nota, pedido, estabelecimento, itensNota, pagamentosAprovados);
    }

    private NotaFiscalDetalheResponseDto montarDetalhe(
            NotaFiscalEntity nota,
            PedidoEntity pedido,
            EstablishmentEntity estabelecimento,
            List<ItemNotaFiscalEntity> itensNota,
            List<PagamentoPedidoEntity> pagamentosAprovados
    ) {
        Set<UUID> idsUsuarios = Stream.of(nota.getEmitidaPorUsuarioId(), nota.getCanceladaPorUsuarioId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> nomesUsuarios = carregarNomesUsuarios(idsUsuarios);
        String emitidaPorNome = nota.getEmitidaPorUsuarioId() != null
                ? nomesUsuarios.get(nota.getEmitidaPorUsuarioId())
                : null;
        String canceladaPorNome = nota.getCanceladaPorUsuarioId() != null
                ? nomesUsuarios.get(nota.getCanceladaPorUsuarioId())
                : null;

        return NotaFiscalMapper.paraDetalhe(
                nota,
                pedido != null ? pedido.getNumero() : null,
                emitidaPorNome,
                canceladaPorNome,
                estabelecimento,
                itensNota,
                pagamentosAprovados
        );
    }

    private NotaFiscalEntity buscarDoEstabelecimento(UUID id) {
        NotaFiscalEntity nota = notaFiscalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Nota fiscal não encontrada"));
        if (!nota.getEstabelecimentoId().equals(contextoCaixa.estabelecimentoId())) {
            throw new AccessDeniedException("Acesso negado");
        }
        return nota;
    }

    private PedidoEntity buscarPedidoParaAtualizacao(UUID pedidoId, UUID estabelecimentoId) {
        PedidoEntity pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        if (!pedido.getEstabelecimentoId().equals(estabelecimentoId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return pedidoRepository.buscarParaAtualizacao(pedidoId, estabelecimentoId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
    }

    private String normalizarEValidarDocumentoConsumidor(String documento) {
        if (documento == null || documento.isBlank()) {
            return null;
        }
        String apenasDigitos = documento.replaceAll("\\D", "");
        if (apenasDigitos.length() != 11 && apenasDigitos.length() != 14) {
            throw new IllegalArgumentException(
                    "O documento do consumidor deve ser um CPF (11 dígitos) ou CNPJ (14 dígitos) válido"
            );
        }
        if (apenasDigitos.chars().distinct().count() == 1) {
            throw new IllegalArgumentException(
                    "O documento do consumidor informado é inválido"
            );
        }
        return apenasDigitos;
    }

    private String normalizarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private Map<UUID, String> carregarNomesUsuarios(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    private Map<String, Object> snapshotNotaFiscal(NotaFiscalEntity nota, Long pedidoNumero) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", nota.getId().toString());
        map.put("pedidoId", nota.getPedidoId() != null ? nota.getPedidoId().toString() : null);
        map.put("pedidoNumero", pedidoNumero);
        map.put("tipo", nota.getTipo().name());
        map.put("modelo", nota.getModelo());
        map.put("status", nota.getStatus().name());
        map.put("numero", nota.getNumero());
        map.put("serie", nota.getSerie());
        map.put("chaveAcesso", nota.getChaveAcesso());
        map.put("protocoloAutorizacao", nota.getProtocoloAutorizacao());
        map.put("contraparteDocumento", nota.getContraparteDocumento());
        map.put("contraparteNome", nota.getContraparteNome());
        map.put("valorProdutos", nota.getValorProdutos());
        map.put("valorDesconto", nota.getValorDesconto());
        map.put("valorTotal", nota.getValorTotal());
        map.put("motivoCancelamento", nota.getMotivoCancelamento());
        return map;
    }
}
