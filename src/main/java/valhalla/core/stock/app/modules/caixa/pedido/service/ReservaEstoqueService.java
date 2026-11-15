package valhalla.core.stock.app.modules.caixa.pedido.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.point.config.MercadoPagoPointProperties;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ReservaEstoqueEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusReservaEstoque;
import valhalla.core.stock.app.modules.caixa.exception.EstoqueInsuficientePedidoException;
import valhalla.core.stock.app.modules.caixa.pedido.repository.ReservaEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.estoque.service.MovimentacaoEstoqueService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class ReservaEstoqueService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ReservaEstoqueRepository reservaEstoqueRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueRepository estoqueRepository;
    private final ComposicaoDrinkRepository composicaoDrinkRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final MercadoPagoPointProperties pointProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReservaEstoqueService(
            ReservaEstoqueRepository reservaEstoqueRepository,
            ProdutoRepository produtoRepository,
            EstoqueRepository estoqueRepository,
            ComposicaoDrinkRepository composicaoDrinkRepository,
            MovimentacaoEstoqueService movimentacaoEstoqueService,
            MercadoPagoPointProperties pointProperties
    ) {
        this.reservaEstoqueRepository = reservaEstoqueRepository;
        this.produtoRepository = produtoRepository;
        this.estoqueRepository = estoqueRepository;
        this.composicaoDrinkRepository = composicaoDrinkRepository;
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.pointProperties = pointProperties;
    }

    @Transactional
    public List<ReservaEstoqueEntity> reservarParaPagamentoPoint(
            PedidoEntity pedido,
            List<ItemPedidoEntity> itens,
            UUID pagamentoPedidoId
    ) {
        if (itens == null || itens.isEmpty()) {
            return List.of();
        }

        List<ReservaEstoqueEntity> ativasExistentes = reservaEstoqueRepository
                .buscarPorPedidoEStatusParaAtualizacao(pedido.getId(), StatusReservaEstoque.ATIVA);

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime novaExpiracao = agora.plusSeconds(pointProperties.reservaExpiracaoSegundos());

        if (!ativasExistentes.isEmpty()) {
            for (ReservaEstoqueEntity reserva : ativasExistentes) {
                reserva.setPagamentoPedidoId(pagamentoPedidoId);
                reserva.setExpiraEm(novaExpiracao);
            }
            return reservaEstoqueRepository.saveAllAndFlush(ativasExistentes);
        }

        List<ReservaEstoqueEntity> efetivadas = reservaEstoqueRepository
                .findByPedidoIdAndStatusOrderByIdAsc(pedido.getId(), StatusReservaEstoque.EFETIVADA);
        if (!efetivadas.isEmpty()) {
            return efetivadas;
        }

        UUID estabelecimentoId = pedido.getEstabelecimentoId();
        Map<UUID, BigDecimal> consumoTotalPorProduto = new LinkedHashMap<>();
        Map<UUID, ProdutoEntity> produtosConsumidos = new HashMap<>();
        List<ItemReservaPlanejada> reservasPlanejadas = new ArrayList<>();

        for (ItemPedidoEntity item : itens) {
            ProdutoEntity produto = buscarProdutoDoEstabelecimento(item.getProdutoId(), estabelecimentoId);
            if (!produto.isAtivo()) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " está inativo");
            }

            if (produto.getTipo() == ProdutoTipo.PADRAO) {
                BigDecimal consumo = calcularQuantidadeEstoquePadrao(produto, item.getQuantidade());
                consumoTotalPorProduto.merge(produto.getId(), consumo, BigDecimal::add);
                produtosConsumidos.put(produto.getId(), produto);
                reservasPlanejadas.add(new ItemReservaPlanejada(
                        item.getId(),
                        produto.getId(),
                        null,
                        consumo
                ));
            } else {
                List<ComposicaoDrinkEntity> receita =
                        composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(produto.getId());
                if (receita.isEmpty()) {
                    throw new IllegalArgumentException(
                            "O drink " + produto.getNome() + " não possui ingredientes cadastrados"
                    );
                }
                validarReceitaParaSaida(receita, estabelecimentoId);

                Map<UUID, BigDecimal> consumoPorIngredienteNoItem = new LinkedHashMap<>();
                for (ComposicaoDrinkEntity comp : receita) {
                    ProdutoEntity ingrediente = comp.getProduto();
                    BigDecimal consumo = comp.getQuantidade().multiply(item.getQuantidade());
                    consumoTotalPorProduto.merge(ingrediente.getId(), consumo, BigDecimal::add);
                    consumoPorIngredienteNoItem.merge(ingrediente.getId(), consumo, BigDecimal::add);
                    produtosConsumidos.put(ingrediente.getId(), ingrediente);
                }
                for (Map.Entry<UUID, BigDecimal> ingEntry : consumoPorIngredienteNoItem.entrySet()) {
                    reservasPlanejadas.add(new ItemReservaPlanejada(
                            item.getId(),
                            ingEntry.getKey(),
                            produto.getId(),
                            ingEntry.getValue()
                    ));
                }
            }
        }

        if (consumoTotalPorProduto.isEmpty()) {
            return List.of();
        }

        Map<UUID, EstoqueEntity> estoques = estoqueRepository
                .findAllByProdutoIdInForUpdate(consumoTotalPorProduto.keySet())
                .stream()
                .collect(Collectors.toMap(e -> e.getProduto().getId(), Function.identity()));

        for (Map.Entry<UUID, BigDecimal> entry : consumoTotalPorProduto.entrySet()) {
            ProdutoEntity produto = produtosConsumidos.get(entry.getKey());
            EstoqueEntity estoque = estoques.get(entry.getKey());
            movimentacaoEstoqueService.validarSaldoVendavel(produto, estoque, entry.getValue());
        }

        for (Map.Entry<UUID, BigDecimal> entry : consumoTotalPorProduto.entrySet()) {
            EstoqueEntity estoque = estoques.get(entry.getKey());
            BigDecimal reservadoAtual = estoque.getQuantidadeReservada() == null ? ZERO : estoque.getQuantidadeReservada();
            estoque.setQuantidadeReservada(reservadoAtual.add(entry.getValue()));
            estoqueRepository.save(estoque);
        }
        estoqueRepository.flush();

        List<ReservaEstoqueEntity> novasReservas = new ArrayList<>();
        for (ItemReservaPlanejada planejada : reservasPlanejadas) {
            EstoqueEntity estoque = estoques.get(planejada.produtoId());
            ReservaEstoqueEntity reserva = new ReservaEstoqueEntity();
            reserva.setPedidoId(pedido.getId());
            reserva.setItemPedidoId(planejada.itemPedidoId());
            reserva.setPagamentoPedidoId(pagamentoPedidoId);
            reserva.setProdutoId(planejada.produtoId());
            reserva.setEstoqueId(estoque.getId());
            reserva.setOrigemDrinkId(planejada.origemDrinkId());
            reserva.setQuantidade(planejada.quantidade());
            reserva.setStatus(StatusReservaEstoque.ATIVA);
            reserva.setCriadoEm(agora);
            reserva.setAtualizadoEm(agora);
            reserva.setExpiraEm(novaExpiracao);
            novasReservas.add(reserva);
        }

        return reservaEstoqueRepository.saveAllAndFlush(novasReservas);
    }

    @Transactional
    public void efetivarReservasOuBaixarEstoque(
            PedidoEntity pedido,
            List<ItemPedidoEntity> itens,
            UUID operadorUsuarioId,
            String origemMovimentacao
    ) {
        List<ReservaEstoqueEntity> reservasAtivas = reservaEstoqueRepository
                .buscarPorPedidoEStatusParaAtualizacao(pedido.getId(), StatusReservaEstoque.ATIVA);

        if (reservasAtivas.isEmpty()) {
            List<ReservaEstoqueEntity> jaEfetivadas = reservaEstoqueRepository
                    .findByPedidoIdAndStatusOrderByIdAsc(pedido.getId(), StatusReservaEstoque.EFETIVADA);
            if (!jaEfetivadas.isEmpty()) {
                return;
            }
            movimentacaoEstoqueService.baixarEstoquePedido(pedido, itens, operadorUsuarioId);
            return;
        }

        Set<UUID> produtoIds = reservasAtivas.stream()
                .map(ReservaEstoqueEntity::getProdutoId)
                .collect(Collectors.toSet());

        Map<UUID, EstoqueEntity> estoques = estoqueRepository
                .findAllByProdutoIdInForUpdate(produtoIds)
                .stream()
                .collect(Collectors.toMap(e -> e.getProduto().getId(), Function.identity()));

        Map<Long, ItemPedidoEntity> itensPorId = itens.stream()
                .filter(i -> i.getId() != null)
                .collect(Collectors.toMap(ItemPedidoEntity::getId, Function.identity()));

        Map<Long, JsonNode> snapshotsPorItemDrink = new HashMap<>();
        for (ReservaEstoqueEntity reserva : reservasAtivas) {
            if (reserva.getOrigemDrinkId() != null && !snapshotsPorItemDrink.containsKey(reserva.getItemPedidoId())) {
                ProdutoEntity drink = produtoRepository.findById(reserva.getOrigemDrinkId())
                        .orElseThrow(() -> new EntityNotFoundException("Drink não encontrado"));
                List<ComposicaoDrinkEntity> receita =
                        composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId());
                ItemPedidoEntity item = itensPorId.get(reserva.getItemPedidoId());
                BigDecimal qtdDrinks = item != null ? item.getQuantidade() : BigDecimal.ONE;
                snapshotsPorItemDrink.put(
                        reserva.getItemPedidoId(),
                        criarSnapshotReceita(drink, receita, qtdDrinks)
                );
            }
        }

        LocalDateTime agora = LocalDateTime.now();
        String origem = (origemMovimentacao == null || origemMovimentacao.isBlank()) ? "WEBHOOK" : origemMovimentacao;
        String motivo = "Venda concluída - Pedido #" + pedido.getNumero();

        for (ReservaEstoqueEntity reserva : reservasAtivas) {
            EstoqueEntity estoque = estoques.get(reserva.getProdutoId());
            if (estoque == null) {
                throw new EntityNotFoundException("Estoque não encontrado para efetivação de reserva");
            }

            BigDecimal reservadoAnterior = estoque.getQuantidadeReservada() == null
                    ? ZERO
                    : estoque.getQuantidadeReservada();
            BigDecimal novoReservado = reservadoAnterior.subtract(reserva.getQuantidade());
            if (novoReservado.compareTo(ZERO) < 0) {
                novoReservado = ZERO;
            }
            estoque.setQuantidadeReservada(novoReservado);

            UUID usuarioMovimentacao = "USUARIO".equals(origem) || "PEDIDO".equals(origem)
                    ? operadorUsuarioId
                    : null;
            TipoMovimentacaoEstoque tipo = reserva.getOrigemDrinkId() != null
                    ? TipoMovimentacaoEstoque.SAIDA_DRINK
                    : TipoMovimentacaoEstoque.SAIDA;
            JsonNode receitaAplicada = null;
            if (reserva.getOrigemDrinkId() != null) {
                JsonNode snap = snapshotsPorItemDrink.get(reserva.getItemPedidoId());
                if (snap != null) {
                    receitaAplicada = snap.deepCopy();
                }
            }

            MovimentacaoEstoqueEntity movimentacaoSalva = movimentacaoEstoqueService.registrarBaixaPedidoComFefo(
                    estoque,
                    tipo,
                    reserva.getQuantidade(),
                    motivo,
                    origem,
                    usuarioMovimentacao,
                    reserva.getItemPedidoId(),
                    reserva.getOrigemDrinkId(),
                    receitaAplicada,
                    agora
            );

            reserva.setStatus(StatusReservaEstoque.EFETIVADA);
            reserva.setEfetivadaEm(agora);
            reserva.setMovimentacaoEstoqueId(movimentacaoSalva.getId());
            reservaEstoqueRepository.save(reserva);
        }

        estoqueRepository.flush();
        reservaEstoqueRepository.flush();
    }

    @Transactional
    public int liberarReservasAtivasDoPedido(
            UUID pedidoId,
            StatusReservaEstoque statusDestino,
            String motivo
    ) {
        if (statusDestino != StatusReservaEstoque.LIBERADA && statusDestino != StatusReservaEstoque.EXPIRADA) {
            throw new IllegalArgumentException("O status de liberação deve ser LIBERADA ou EXPIRADA");
        }

        List<ReservaEstoqueEntity> reservasAtivas = reservaEstoqueRepository
                .buscarPorPedidoEStatusParaAtualizacao(pedidoId, StatusReservaEstoque.ATIVA);

        if (reservasAtivas.isEmpty()) {
            return 0;
        }

        Set<UUID> produtoIds = reservasAtivas.stream()
                .map(ReservaEstoqueEntity::getProdutoId)
                .collect(Collectors.toSet());

        Map<UUID, EstoqueEntity> estoques = estoqueRepository
                .findAllByProdutoIdInForUpdate(produtoIds)
                .stream()
                .collect(Collectors.toMap(e -> e.getProduto().getId(), Function.identity()));

        LocalDateTime agora = LocalDateTime.now();
        String motivoNormalizado = motivo == null || motivo.isBlank()
                ? "Reserva liberada"
                : (motivo.length() > 200 ? motivo.substring(0, 200) : motivo);

        for (ReservaEstoqueEntity reserva : reservasAtivas) {
            EstoqueEntity estoque = estoques.get(reserva.getProdutoId());
            if (estoque != null) {
                BigDecimal reservadoAtual = estoque.getQuantidadeReservada() == null
                        ? ZERO
                        : estoque.getQuantidadeReservada();
                BigDecimal novoReservado = reservadoAtual.subtract(reserva.getQuantidade());
                if (novoReservado.compareTo(ZERO) < 0) {
                    novoReservado = ZERO;
                }
                estoque.setQuantidadeReservada(novoReservado);
                estoqueRepository.save(estoque);
            }

            reserva.setStatus(statusDestino);
            reserva.setLiberadaEm(agora);
            reserva.setMotivoLiberacao(motivoNormalizado);
            reservaEstoqueRepository.save(reserva);
        }

        estoqueRepository.flush();
        reservaEstoqueRepository.flush();
        return reservasAtivas.size();
    }

    private BigDecimal calcularQuantidadeEstoquePadrao(ProdutoEntity produto, BigDecimal quantidadeVendida) {
        if (produto.isFracionado() && produto.getVolumeEmbalagemMl() != null) {
            return quantidadeVendida.multiply(produto.getVolumeEmbalagemMl());
        }
        return quantidadeVendida;
    }

    private ProdutoEntity buscarProdutoDoEstabelecimento(UUID idProduto, UUID idEstabelecimento) {
        ProdutoEntity produto = produtoRepository.findById(idProduto)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
        if (!produto.getEstabelecimentoId().equals(idEstabelecimento)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return produto;
    }

    private void validarReceitaParaSaida(List<ComposicaoDrinkEntity> receita, UUID idEstabelecimento) {
        for (ComposicaoDrinkEntity item : receita) {
            ProdutoEntity ingrediente = item.getProduto();
            if (!ingrediente.getEstabelecimentoId().equals(idEstabelecimento)
                    || ingrediente.getTipo() != ProdutoTipo.PADRAO
                    || !ingrediente.isAtivo()) {
                throw new IllegalArgumentException("A receita possui um ingrediente inválido ou inativo");
            }
            UnidadeConsumoDrink unidadeEsperada = ingrediente.isFracionado()
                    ? UnidadeConsumoDrink.ML
                    : UnidadeConsumoDrink.UN;
            if (item.getUnidadeConsumo() != unidadeEsperada) {
                throw new IllegalArgumentException("A receita possui unidade incompatível com o ingrediente");
            }
        }
    }

    private JsonNode criarSnapshotReceita(
            ProdutoEntity drink,
            List<ComposicaoDrinkEntity> receita,
            BigDecimal quantidadeDrinks
    ) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("drinkId", drink.getId().toString());
        snapshot.put("drinkNome", drink.getNome());
        snapshot.put("quantidadeDrinks", quantidadeDrinks);
        ArrayNode ingredientes = snapshot.putArray("ingredientes");
        receita.forEach(item -> {
            ObjectNode ingrediente = ingredientes.addObject();
            ingrediente.put("produtoId", item.getProduto().getId().toString());
            ingrediente.put("produtoNome", item.getProduto().getNome());
            ingrediente.put("quantidadePorDrink", item.getQuantidade());
            ingrediente.put("unidadeConsumo", item.getUnidadeConsumo().name());
            ingrediente.put("quantidadeConsumida", item.getQuantidade().multiply(quantidadeDrinks));
        });
        return snapshot;
    }

    private record ItemReservaPlanejada(
            Long itemPedidoId,
            UUID produtoId,
            UUID origemDrinkId,
            BigDecimal quantidade
    ) {
    }
}
