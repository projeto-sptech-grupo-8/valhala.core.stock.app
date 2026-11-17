package valhalla.core.stock.app.modules.vendas.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.estoque.service.MovimentacaoEstoqueService;
import valhalla.core.stock.app.modules.vendas.dto.RequisicaoVendaDto;
import valhalla.core.stock.app.modules.vendas.dto.RespostaVendaDto;
import valhalla.core.stock.app.modules.vendas.entity.CanalVenda;
import valhalla.core.stock.app.modules.vendas.entity.FormaPagamento;
import valhalla.core.stock.app.modules.vendas.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.vendas.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.vendas.entity.PedidoEntity;
import valhalla.core.stock.app.modules.vendas.entity.StatusPagamento;
import valhalla.core.stock.app.modules.vendas.entity.StatusPedido;
import valhalla.core.stock.app.modules.vendas.repository.PedidoRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vendas do caixa. A finalização grava pedido, itens e pagamentos e baixa o estoque na mesma transação:
 * se qualquer item não tiver saldo, nada é gravado. O cancelamento devolve os itens aos lotes de origem.
 */
@Service
public class VendaService {

    private static final String PODE_CONSULTAR =
            "@permissionAuthorizationService.hasPermission('REGISTRAR_VENDA', authentication)"
                    + " or @permissionAuthorizationService.hasPermission('VISUALIZAR_RELATORIOS', authentication)";
    private static final Set<String> CAMPOS_ORDENACAO = Set.of("numero", "criadoEm", "total");

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ComposicaoDrinkRepository composicaoDrinkRepository;
    private final EstoqueRepository estoqueRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final BusinessEventLogger businessEventLogger;

    public VendaService(
            PedidoRepository pedidoRepository,
            ProdutoRepository produtoRepository,
            ComposicaoDrinkRepository composicaoDrinkRepository,
            EstoqueRepository estoqueRepository,
            MovimentacaoEstoqueService movimentacaoEstoqueService,
            BusinessEventLogger businessEventLogger
    ) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.composicaoDrinkRepository = composicaoDrinkRepository;
        this.estoqueRepository = estoqueRepository;
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.hasPermission('REGISTRAR_VENDA', authentication)")
    public RespostaVendaDto registrar(RequisicaoVendaDto dto) {
        UUID idEstabelecimento = claimUuid("establishmentId");
        UUID idUsuario = claimUuid("userId");
        Map<UUID, ProdutoEntity> produtos = carregarProdutos(dto.itens(), idEstabelecimento);
        Map<UUID, List<ComposicaoDrinkEntity>> receitas = carregarReceitas(produtos.values());
        LocalDateTime agora = FusoHorarioOperacao.agora();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setEstabelecimentoId(idEstabelecimento);
        pedido.setOperadorUsuarioId(idUsuario);
        pedido.setCanal(dto.canal());
        pedido.setStatus(StatusPedido.CONCLUIDO);
        pedido.setNomeCliente(normalizar(dto.nomeCliente()));
        pedido.setDocumentoCliente(normalizar(dto.documentoCliente()));
        pedido.setObservacao(normalizar(dto.observacao()));
        pedido.setCriadoEm(agora);
        pedido.setConcluidoEm(agora);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (RequisicaoVendaDto.Item requisicao : dto.itens()) {
            ProdutoEntity produto = produtos.get(requisicao.produtoId());
            ItemPedidoEntity item = new ItemPedidoEntity();
            item.setProdutoId(produto.getId());
            item.setDescricaoProduto(produto.getNome());
            item.setQuantidade(requisicao.quantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());
            item.setDesconto(BigDecimal.ZERO);
            item.setSubtotal(dinheiro(produto.getPrecoVenda().multiply(requisicao.quantidade())));
            item.setCustoUnitario(custoUnitario(produto, receitas.getOrDefault(produto.getId(), List.of())));
            pedido.adicionarItem(item);
            subtotal = subtotal.add(item.getSubtotal());
        }

        BigDecimal desconto = dto.desconto() == null ? BigDecimal.ZERO : dinheiro(dto.desconto());
        if (desconto.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("O desconto não pode ser maior que o subtotal da venda");
        }
        BigDecimal total = subtotal.subtract(desconto);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("O total da venda deve ser maior que zero");
        }
        pedido.setSubtotal(subtotal);
        pedido.setDesconto(desconto);
        pedido.setTotal(total);
        BigDecimal troco = registrarPagamentos(pedido, dto.pagamentos(), total, agora);

        pedidoRepository.bloquearEstabelecimento(idEstabelecimento)
                .orElseThrow(() -> new AccessDeniedException("Sessão inválida"));
        pedido.setNumero(pedidoRepository.ultimoNumero(idEstabelecimento) + 1);

        // Bloqueia todos os estoques envolvidos em ordem fixa antes das baixas, evitando deadlock
        // entre vendas simultâneas que compartilham produtos ou ingredientes.
        List<UUID> idsEstoque = idsComEstoque(produtos.values(), receitas);
        if (!idsEstoque.isEmpty()) {
            estoqueRepository.findAllByProdutoIdInForUpdate(idsEstoque);
        }
        pedidoRepository.saveAndFlush(pedido);

        String motivo = "Venda #" + pedido.getNumero();
        for (ItemPedidoEntity item : pedido.getItens()) {
            movimentacaoEstoqueService.baixarItemVendido(
                    produtos.get(item.getProdutoId()), item.getQuantidade(), item.getId(), motivo, idUsuario);
        }

        businessEventLogger.success("sale.registered", "sale", pedido.getId());
        return paraResposta(pedido, troco);
    }

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.hasPermission('CANCELAR_VENDA', authentication)")
    public RespostaVendaDto cancelar(UUID idVenda, String motivo) {
        UUID idEstabelecimento = claimUuid("establishmentId");
        PedidoEntity pedido = pedidoRepository.buscarParaAtualizacao(idVenda, idEstabelecimento)
                .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada"));
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new IllegalArgumentException("A venda já está cancelada");
        }

        String motivoNormalizado = motivo.trim();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setCanceladoEm(FusoHorarioOperacao.agora());
        pedido.setMotivoCancelamento(motivoNormalizado);
        pedido.getPagamentos().forEach(pagamento -> pagamento.setStatus(StatusPagamento.ESTORNADO));
        movimentacaoEstoqueService.estornarItensVendidos(
                pedido.getItens().stream().map(ItemPedidoEntity::getId).toList(),
                "Cancelamento da venda #%d: %s".formatted(pedido.getNumero(), motivoNormalizado),
                claimUuid("userId"));

        businessEventLogger.success("sale.cancelled", "sale", pedido.getId());
        return paraResposta(pedido, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PODE_CONSULTAR)
    public RespostaVendaDto obter(UUID idVenda) {
        return pedidoRepository.findByIdAndEstabelecimentoId(idVenda, claimUuid("establishmentId"))
                .map(pedido -> paraResposta(pedido, null))
                .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada"));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PODE_CONSULTAR)
    public RespostaPaginadaDto<RespostaVendaDto> listar(
            LocalDate dataInicial,
            LocalDate dataFinal,
            StatusPedido status,
            CanalVenda canal,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        if (dataInicial != null && dataFinal != null && dataInicial.isAfter(dataFinal)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
        UUID idEstabelecimento = claimUuid("establishmentId");
        Specification<PedidoEntity> filtro = (root, query, builder) ->
                builder.equal(root.get("estabelecimentoId"), idEstabelecimento);
        if (dataInicial != null) {
            filtro = filtro.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("criadoEm"), dataInicial.atStartOfDay()));
        }
        if (dataFinal != null) {
            filtro = filtro.and((root, query, builder) ->
                    builder.lessThan(root.get("criadoEm"), dataFinal.plusDays(1).atStartOfDay()));
        }
        if (status != null) {
            filtro = filtro.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (canal != null) {
            filtro = filtro.and((root, query, builder) -> builder.equal(root.get("canal"), canal));
        }
        Page<PedidoEntity> resultado = pedidoRepository.findAll(filtro,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao, CAMPOS_ORDENACAO));
        return RespostaPaginadaDto.de(resultado, pedido -> paraResposta(pedido, null));
    }

    /**
     * Grava os pagamentos e devolve o troco. A soma recebida deve cobrir o total; a sobra só pode sair
     * de pagamentos em dinheiro, que são gravados já sem o troco (valor efetivamente recebido).
     */
    private BigDecimal registrarPagamentos(
            PedidoEntity pedido,
            List<RequisicaoVendaDto.Pagamento> pagamentos,
            BigDecimal total,
            LocalDateTime agora
    ) {
        BigDecimal recebido = pagamentos.stream().map(pagamento -> dinheiro(pagamento.valor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (recebido.compareTo(total) < 0) {
            throw new IllegalArgumentException("Os pagamentos (%s) não cobrem o total da venda (%s)"
                    .formatted(moeda(recebido), moeda(total)));
        }
        BigDecimal troco = recebido.subtract(total);
        BigDecimal emDinheiro = pagamentos.stream()
                .filter(pagamento -> pagamento.forma() == FormaPagamento.DINHEIRO)
                .map(pagamento -> dinheiro(pagamento.valor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (troco.compareTo(emDinheiro) > 0) {
            throw new IllegalArgumentException("Troco só é permitido sobre pagamentos em dinheiro");
        }

        // o troco sai dos pagamentos em dinheiro, do último para o primeiro
        List<BigDecimal> liquidos = new ArrayList<>(pagamentos.stream().map(p -> dinheiro(p.valor())).toList());
        BigDecimal trocoADescontar = troco;
        for (int i = pagamentos.size() - 1; i >= 0 && trocoADescontar.signum() > 0; i--) {
            if (pagamentos.get(i).forma() == FormaPagamento.DINHEIRO) {
                BigDecimal abatido = liquidos.get(i).min(trocoADescontar);
                liquidos.set(i, liquidos.get(i).subtract(abatido));
                trocoADescontar = trocoADescontar.subtract(abatido);
            }
        }
        for (int i = 0; i < pagamentos.size(); i++) {
            if (liquidos.get(i).signum() > 0) {
                PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
                pagamento.setForma(pagamentos.get(i).forma());
                pagamento.setStatus(StatusPagamento.PAGO);
                pagamento.setValor(liquidos.get(i));
                pagamento.setPagoEm(agora);
                pagamento.setCriadoEm(agora);
                pedido.adicionarPagamento(pagamento);
            }
        }
        return troco;
    }

    private Map<UUID, ProdutoEntity> carregarProdutos(List<RequisicaoVendaDto.Item> itens, UUID idEstabelecimento) {
        Set<UUID> ids = itens.stream().map(RequisicaoVendaDto.Item::produtoId).collect(Collectors.toSet());
        Map<UUID, ProdutoEntity> produtos = produtoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProdutoEntity::getId, Function.identity()));
        for (UUID id : ids) {
            ProdutoEntity produto = produtos.get(id);
            if (produto == null) {
                throw new EntityNotFoundException("Produto não encontrado");
            }
            if (!produto.getEstabelecimentoId().equals(idEstabelecimento)) {
                throw new AccessDeniedException("Acesso negado");
            }
            if (!produto.isAtivo()) {
                throw new IllegalArgumentException("O produto %s está inativo".formatted(produto.getNome()));
            }
            if (produto.getTipo() == ProdutoTipo.PADRAO && produto.isFracionado()
                    && (produto.getVolumeEmbalagemMl() == null || produto.getVolumeEmbalagemMl().signum() <= 0)) {
                throw new IllegalArgumentException(
                        "O produto %s é fracionado e não possui volume de embalagem".formatted(produto.getNome()));
            }
        }
        return produtos;
    }

    private Map<UUID, List<ComposicaoDrinkEntity>> carregarReceitas(java.util.Collection<ProdutoEntity> produtos) {
        return produtos.stream()
                .filter(produto -> produto.getTipo() == ProdutoTipo.DRINK)
                .collect(Collectors.toMap(ProdutoEntity::getId,
                        drink -> composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId())));
    }

    private List<UUID> idsComEstoque(
            java.util.Collection<ProdutoEntity> produtos,
            Map<UUID, List<ComposicaoDrinkEntity>> receitas
    ) {
        Set<UUID> ids = new HashSet<>();
        produtos.stream().filter(produto -> produto.getTipo() == ProdutoTipo.PADRAO)
                .forEach(produto -> ids.add(produto.getId()));
        receitas.values().forEach(receita -> receita.forEach(item -> ids.add(item.getProduto().getId())));
        return new ArrayList<>(ids); // a consulta de bloqueio ordena pelo id do produto
    }

    /** Custo de uma unidade vendida: custo do produto ou, no drink, a soma proporcional dos ingredientes. */
    private BigDecimal custoUnitario(ProdutoEntity produto, List<ComposicaoDrinkEntity> receita) {
        if (produto.getTipo() != ProdutoTipo.DRINK) {
            return produto.getPrecoCusto().setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal custo = BigDecimal.ZERO;
        for (ComposicaoDrinkEntity item : receita) {
            ProdutoEntity ingrediente = item.getProduto();
            BigDecimal parcela = item.getQuantidade().multiply(ingrediente.getPrecoCusto());
            if (item.getUnidadeConsumo() == UnidadeConsumoDrink.ML) {
                BigDecimal volume = ingrediente.getVolumeEmbalagemMl();
                parcela = volume == null || volume.signum() <= 0
                        ? BigDecimal.ZERO
                        : parcela.divide(volume, 6, RoundingMode.HALF_UP);
            }
            custo = custo.add(parcela);
        }
        return custo.setScale(4, RoundingMode.HALF_UP);
    }

    private RespostaVendaDto paraResposta(PedidoEntity pedido, BigDecimal troco) {
        return new RespostaVendaDto(
                pedido.getId(), pedido.getNumero(), pedido.getCanal(), pedido.getStatus(),
                pedido.getSubtotal(), pedido.getDesconto(), pedido.getTotal(), troco,
                pedido.getNomeCliente(), pedido.getDocumentoCliente(), pedido.getObservacao(),
                pedido.getOperadorUsuarioId(), pedido.getConcluidoEm(), pedido.getCanceladoEm(),
                pedido.getMotivoCancelamento(),
                pedido.getItens().stream().map(item -> new RespostaVendaDto.Item(
                        item.getId(), item.getProdutoId(), item.getDescricaoProduto(), item.getQuantidade(),
                        item.getPrecoUnitario(), item.getSubtotal())).toList(),
                pedido.getPagamentos().stream().map(pagamento -> new RespostaVendaDto.Pagamento(
                        pagamento.getForma(), pagamento.getStatus(), pagamento.getValor())).toList()
        );
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static UUID claimUuid(String nome) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            String valor = token.getToken().getClaimAsString(nome);
            if (valor == null || valor.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }
            return UUID.fromString(valor);
        } catch (IllegalArgumentException excecao) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
