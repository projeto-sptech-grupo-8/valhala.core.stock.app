package valhalla.core.stock.app.modules.estoque.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.exception.EstoqueInsuficientePedidoException;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoLoteResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.SaidaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoLoteEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoLoteRepository;
import valhalla.core.stock.app.modules.estoque.service.ControleLotesEstoque.Alocacao;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.strategy.MovimentacaoCommand;
import valhalla.core.stock.app.modules.estoque.strategy.PoliticaLote;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.databind.util.RawValue;

@Service
public class MovimentacaoEstoqueService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<TipoMovimentacaoEstoque> TIPOS_DIRETOS = Set.of(
            TipoMovimentacaoEstoque.ENTRADA,
            TipoMovimentacaoEstoque.SAIDA,
            TipoMovimentacaoEstoque.AJUSTE_POSITIVO,
            TipoMovimentacaoEstoque.AJUSTE_NEGATIVO,
            TipoMovimentacaoEstoque.PERDA
    );

    public record ItemConsumoValidacao(UUID produtoId, BigDecimal quantidade) {
    }

    private final ProdutoRepository produtoRepository;
    private final EstoqueRepository estoqueRepository;
    private final ComposicaoDrinkRepository composicaoDrinkRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<TipoMovimentacaoEstoque, EstrategiaMovimentacaoEstoque> estrategias;
    private final BusinessEventLogger businessEventLogger;
    private final ControleLotesEstoque controleLotes;
    private final MovimentacaoLoteRepository movimentacaoLoteRepository;

    public MovimentacaoEstoqueService(
            ProdutoRepository produtoRepository,
            EstoqueRepository estoqueRepository,
            ComposicaoDrinkRepository composicaoDrinkRepository,
            MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
            UserRepository userRepository,
            List<EstrategiaMovimentacaoEstoque> estrategias,
            BusinessEventLogger businessEventLogger,
            ControleLotesEstoque controleLotes,
            MovimentacaoLoteRepository movimentacaoLoteRepository
    ) {
        this.controleLotes = controleLotes;
        this.movimentacaoLoteRepository = movimentacaoLoteRepository;
        this.produtoRepository = produtoRepository;
        this.estoqueRepository = estoqueRepository;
        this.composicaoDrinkRepository = composicaoDrinkRepository;
        this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
        this.userRepository = userRepository;
        this.businessEventLogger = businessEventLogger;
        this.estrategias = estrategias.stream().collect(Collectors.toMap(
                EstrategiaMovimentacaoEstoque::tipoSuportado,
                Function.identity(),
                (primeira, segunda) -> {
                    throw new IllegalStateException("Estratégia de movimentação duplicada");
                },
                () -> new EnumMap<>(TipoMovimentacaoEstoque.class)
        ));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_ESTOQUE', authentication)")
    public RespostaPaginadaDto<MovimentacaoResponseDto> listarMovimentacoes(
            UUID produtoId,
            TipoMovimentacaoEstoque tipo,
            UUID usuarioId,
            LocalDate dataInicial,
            LocalDate dataFinal,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        Specification<MovimentacaoEstoqueEntity> especificacao = criarEspecificacaoMovimentacoes(
                produtoId, tipo, usuarioId, dataInicial, dataFinal
        );

        Page<MovimentacaoEstoqueEntity> resultado = movimentacaoEstoqueRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao,
                        Set.of("ocorridoEm", "tipo", "quantidade", "saldoPosterior"))
        );
        Map<UUID, String> nomesUsuarios = nomesUsuariosPorId(resultado.getContent());
        Map<Long, List<MovimentacaoLoteResponseDto>> lotes = lotesPorMovimentacao(resultado.getContent());
        return RespostaPaginadaDto.de(resultado,
                movimentacao -> paraResposta(movimentacao,
                        nomesUsuarios.getOrDefault(movimentacao.getUsuarioId(), "Usuário não encontrado"),
                        lotes.getOrDefault(movimentacao.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_ESTOQUE', authentication)")
    public List<MovimentacaoResponseDto> listarMovimentacoesParaExportacao(
            UUID produtoId,
            TipoMovimentacaoEstoque tipo,
            UUID usuarioId,
            LocalDate dataInicial,
            LocalDate dataFinal,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        Specification<MovimentacaoEstoqueEntity> especificacao = criarEspecificacaoMovimentacoes(
                produtoId, tipo, usuarioId, dataInicial, dataFinal
        );
        Pageable paginacaoOrdenacao = Paginacao.criar(
                0, 100, ordenarPor, direcao,
                Set.of("ocorridoEm", "tipo", "quantidade", "saldoPosterior")
        );
        List<MovimentacaoEstoqueEntity> movimentacoes = movimentacaoEstoqueRepository.findAll(
                especificacao, paginacaoOrdenacao.getSort()
        );
        Map<UUID, String> nomesUsuarios = nomesUsuariosPorId(movimentacoes);
        Map<Long, List<MovimentacaoLoteResponseDto>> lotes = lotesPorMovimentacao(movimentacoes);
        return movimentacoes.stream()
                .map(movimentacao -> paraResposta(movimentacao,
                        nomesUsuarios.getOrDefault(movimentacao.getUsuarioId(), "Usuário não encontrado"),
                        lotes.getOrDefault(movimentacao.getId(), List.of())))
                .toList();
    }

    private Specification<MovimentacaoEstoqueEntity> criarEspecificacaoMovimentacoes(
            UUID produtoId,
            TipoMovimentacaoEstoque tipo,
            UUID usuarioId,
            LocalDate dataInicial,
            LocalDate dataFinal
    ) {
        if (dataInicial != null && dataFinal != null && dataInicial.isAfter(dataFinal)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        Specification<MovimentacaoEstoqueEntity> especificacao = (root, query, builder) -> builder.equal(
                root.join("estoque").join("produto").get("estabelecimentoId"), idEstabelecimento
        );
        if (produtoId != null) {
            especificacao = especificacao.and((root, query, builder) ->
                    builder.equal(root.join("estoque").get("produto").get("id"), produtoId));
        }
        if (tipo != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.equal(root.get("tipo"), tipo));
        }
        if (usuarioId != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.equal(root.get("usuarioId"), usuarioId));
        }
        if (dataInicial != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.greaterThanOrEqualTo(
                    root.get("ocorridoEm"), dataInicial.atStartOfDay()
            ));
        }
        if (dataFinal != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.lessThan(
                    root.get("ocorridoEm"), dataFinal.plusDays(1).atStartOfDay()
            ));
        }
        return especificacao;
    }

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.hasPermission('MOVIMENTAR_ESTOQUE', authentication)")
    public MovimentacaoResponseDto registrarMovimentacao(MovimentacaoRequestDto dto) {
        if (!TIPOS_DIRETOS.contains(dto.tipo())) {
            throw new IllegalArgumentException("O tipo informado não pode ser registrado diretamente");
        }

        ProdutoEntity produto = buscarProdutoDoEstabelecimento(dto.produtoId(), obterIdEstabelecimentoAutenticado());
        if (produto.getTipo() == ProdutoTipo.DRINK) {
            throw new IllegalArgumentException("Drink não possui estoque próprio; registre a saída de drink");
        }

        EstoqueEntity estoque = estoqueRepository.findByProdutoIdForUpdate(produto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Estoque do produto não encontrado"));
        BigDecimal quantidadeBase = converterQuantidadeMovimentacao(produto, dto.quantidade(), Boolean.TRUE.equals(dto.emFardo()));
        String motivo = comporMotivoMovimentacao(normalizar(dto.motivo()), dto.quantidade(), Boolean.TRUE.equals(dto.emFardo()));
        MovimentacaoCommand command = new MovimentacaoCommand(
                dto.tipo(), quantidadeBase, motivo, normalizar(dto.lote()),
                normalizar(dto.numeroNotaFiscal()), null, null, dto.validade(), dto.loteId()
        );
        UUID usuarioId = obterIdUsuarioAutenticado();
        MovimentacaoResponseDto response = aplicarMovimentacao(estoque, command, usuarioId, nomeUsuario(usuarioId));
        businessEventLogger.success("stock.movement.registered." + dto.tipo().name().toLowerCase(),
                "stock-movement", response.id());
        return response;
    }

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.hasPermission('MOVIMENTAR_ESTOQUE', authentication)")
    public List<MovimentacaoResponseDto> registrarSaidaDrink(SaidaDrinkRequestDto dto) {
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        ProdutoEntity drink = buscarProdutoDoEstabelecimento(dto.drinkId(), idEstabelecimento);
        if (drink.getTipo() != ProdutoTipo.DRINK) {
            throw new IllegalArgumentException("O produto informado não é um drink");
        }
        if (!drink.isAtivo()) {
            throw new IllegalArgumentException("O drink informado está inativo");
        }

        List<ComposicaoDrinkEntity> receita = composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId());
        if (receita.isEmpty()) {
            throw new IllegalArgumentException("O drink não possui ingredientes cadastrados");
        }
        validarReceitaParaSaida(receita, idEstabelecimento);

        List<UUID> idsIngredientes = receita.stream().map(item -> item.getProduto().getId()).toList();
        Map<UUID, EstoqueEntity> estoques = estoqueRepository.findAllByProdutoIdInForUpdate(idsIngredientes)
                .stream()
                .collect(Collectors.toMap(estoque -> estoque.getProduto().getId(), Function.identity()));
        if (estoques.size() != idsIngredientes.size()) {
            throw new EntityNotFoundException("Estoque de um ingrediente não encontrado");
        }

        JsonNode receitaAplicada = criarSnapshotReceita(drink, receita, dto.quantidade());
        List<ItemSaidaDrink> itens = receita.stream()
                .map(item -> new ItemSaidaDrink(
                        estoques.get(item.getProduto().getId()),
                        item.getQuantidade().multiply(dto.quantidade())
                ))
                .toList();
        MovimentacaoCommand commandBase = new MovimentacaoCommand(
                TipoMovimentacaoEstoque.SAIDA_DRINK, null, normalizar(dto.motivo()), null, null,
                drink.getId(), receitaAplicada
        );

        EstrategiaMovimentacaoEstoque estrategia = estrategiaPara(TipoMovimentacaoEstoque.SAIDA_DRINK);
        estrategia.validar(commandBase);
        itens.forEach(item -> estrategia.calcularSaldoPosterior(
                item.estoque().getQuantidadeAtual(), item.quantidade()
        ));

        UUID usuarioId = obterIdUsuarioAutenticado();
        String usuarioNome = nomeUsuario(usuarioId);
        List<MovimentacaoResponseDto> resposta = itens.stream()
                .map(item -> aplicarMovimentacao(item.estoque(), new MovimentacaoCommand(
                        TipoMovimentacaoEstoque.SAIDA_DRINK,
                        item.quantidade(),
                        commandBase.motivo(), null, null, drink.getId(), receitaAplicada.deepCopy()
                ), usuarioId, usuarioNome))
                .toList();
        businessEventLogger.success("stock.drink-output.registered", "product", drink.getId());
        return resposta;
    }

    @Transactional
    public void registrarEntradaInicial(EstoqueEntity estoque, BigDecimal quantidadeInicial) {
        registrarEntradaInicial(estoque, quantidadeInicial, null, null);
    }

    @Transactional
    public void registrarEntradaInicial(
            EstoqueEntity estoque,
            BigDecimal quantidadeInicial,
            String lote,
            LocalDate validade
    ) {
        if (quantidadeInicial == null || quantidadeInicial.signum() == 0) {
            if (lote != null || validade != null) {
                controleLotes.registrarSaldoInicial(estoque, ZERO, lote, validade);
            }
            return;
        }
        MovimentacaoCommand command = new MovimentacaoCommand(
                TipoMovimentacaoEstoque.ENTRADA_INICIAL, quantidadeInicial,
                "Saldo inicial do produto", lote, null, null, null, validade, null
        );
        EstrategiaMovimentacaoEstoque estrategia = estrategiaPara(command.tipo());
        estrategia.validar(command);
        BigDecimal saldoPosterior = estrategia.calcularSaldoPosterior(ZERO, quantidadeInicial);
        if (estoque.getQuantidadeAtual().compareTo(saldoPosterior) != 0) {
            throw new IllegalStateException("O saldo inicial informado é inconsistente");
        }
        List<Alocacao> alocacoes = controleLotes.registrarSaldoInicial(estoque, quantidadeInicial, lote, validade);
        MovimentacaoEstoqueEntity movimento = salvarHistorico(
                estoque, command, ZERO, saldoPosterior, obterIdUsuarioAutenticado(),
                ControleLotesEstoque.descreverCodigos(alocacoes));
        controleLotes.vincular(movimento, alocacoes);
        businessEventLogger.success("stock.initial-entry.registered", "stock-movement", movimento.getId());
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularCustoUnitario(ProdutoEntity produto) {
        if (produto.getTipo() != ProdutoTipo.DRINK) {
            return produto.getPrecoCusto() == null
                    ? ZERO.setScale(2, RoundingMode.HALF_UP)
                    : produto.getPrecoCusto().setScale(2, RoundingMode.HALF_UP);
        }

        List<ComposicaoDrinkEntity> receita = composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(produto.getId());
        BigDecimal custoTotal = ZERO;
        for (ComposicaoDrinkEntity item : receita) {
            ProdutoEntity ingrediente = item.getProduto();
            BigDecimal precoCustoIngrediente = ingrediente.getPrecoCusto() == null ? ZERO : ingrediente.getPrecoCusto();
            BigDecimal custoItem;
            if (ingrediente.isFracionado()
                    && ingrediente.getVolumeEmbalagemMl() != null
                    && ingrediente.getVolumeEmbalagemMl().compareTo(ZERO) > 0) {
                custoItem = precoCustoIngrediente
                        .multiply(item.getQuantidade())
                        .divide(ingrediente.getVolumeEmbalagemMl(), 6, RoundingMode.HALF_UP);
            } else {
                custoItem = precoCustoIngrediente.multiply(item.getQuantidade());
            }
            custoTotal = custoTotal.add(custoItem);
        }
        return custoTotal.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public void validarDisponibilidadeItens(UUID estabelecimentoId, List<ItemConsumoValidacao> itens) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        Map<UUID, BigDecimal> consumoPorProdutoPadrao = new LinkedHashMap<>();
        Map<UUID, ProdutoEntity> produtosPadrao = new HashMap<>();

        for (ItemConsumoValidacao item : itens) {
            ProdutoEntity produto = buscarProdutoDoEstabelecimento(item.produtoId(), estabelecimentoId);
            if (!produto.isAtivo()) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " está inativo");
            }
            if (produto.getTipo() == ProdutoTipo.PADRAO) {
                BigDecimal quantidadeConsumida = calcularQuantidadeEstoquePadrao(produto, item.quantidade());
                consumoPorProdutoPadrao.merge(produto.getId(), quantidadeConsumida, BigDecimal::add);
                produtosPadrao.put(produto.getId(), produto);
            } else {
                List<ComposicaoDrinkEntity> receita = composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(produto.getId());
                if (receita.isEmpty()) {
                    throw new IllegalArgumentException("O drink " + produto.getNome() + " não possui ingredientes cadastrados");
                }
                validarReceitaParaSaida(receita, estabelecimentoId);
                for (ComposicaoDrinkEntity ingrediente : receita) {
                    ProdutoEntity produtoIngrediente = ingrediente.getProduto();
                    BigDecimal quantidadeConsumida = ingrediente.getQuantidade().multiply(item.quantidade());
                    consumoPorProdutoPadrao.merge(produtoIngrediente.getId(), quantidadeConsumida, BigDecimal::add);
                    produtosPadrao.put(produtoIngrediente.getId(), produtoIngrediente);
                }
            }
        }

        if (consumoPorProdutoPadrao.isEmpty()) {
            return;
        }

        Map<UUID, EstoqueEntity> estoques = estoqueRepository.findAllByProdutoIdIn(consumoPorProdutoPadrao.keySet())
                .stream()
                .collect(Collectors.toMap(e -> e.getProduto().getId(), Function.identity()));

        for (Map.Entry<UUID, BigDecimal> entry : consumoPorProdutoPadrao.entrySet()) {
            ProdutoEntity produto = produtosPadrao.get(entry.getKey());
            EstoqueEntity estoque = estoques.get(entry.getKey());
            validarSaldoVendavel(produto, estoque, entry.getValue());
        }
    }

    /**
     * Valida saldo físico livre (descontando reservas) e saldo dentro da validade para venda/reserva.
     */
    public void validarSaldoVendavel(ProdutoEntity produto, EstoqueEntity estoque, BigDecimal quantidadeNecessaria) {
        if (estoque == null) {
            throw new EntityNotFoundException("Estoque do produto " + produto.getNome() + " não encontrado");
        }
        BigDecimal reservado = estoque.getQuantidadeReservada() == null ? ZERO : estoque.getQuantidadeReservada();
        BigDecimal disponivelFisico = estoque.getQuantidadeAtual().subtract(reservado);
        if (disponivelFisico.compareTo(quantidadeNecessaria) < 0) {
            throw new EstoqueInsuficientePedidoException(
                    "Estoque insuficiente para o produto " + produto.getNome(),
                    produto.getNome(),
                    disponivelFisico
            );
        }
        BigDecimal disponivelNaoVencido = controleLotes.saldoNaoVencidoDisponivel(estoque);
        if (disponivelNaoVencido.compareTo(quantidadeNecessaria) < 0) {
            throw new EstoqueInsuficientePedidoException(
                    "Saldo dentro da validade insuficiente para o produto " + produto.getNome(),
                    produto.getNome(),
                    disponivelNaoVencido
            );
        }
    }

    @Transactional
    public void baixarEstoquePedido(
            PedidoEntity pedido,
            List<ItemPedidoEntity> itens,
            UUID operadorUsuarioId
    ) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        UUID estabelecimentoId = pedido.getEstabelecimentoId();
        Map<UUID, ProdutoEntity> produtosDoPedido = new HashMap<>();
        Map<UUID, List<ComposicaoDrinkEntity>> receitasPorDrink = new HashMap<>();
        Map<UUID, BigDecimal> consumoTotalPorProduto = new LinkedHashMap<>();
        Map<UUID, ProdutoEntity> produtosConsumidos = new HashMap<>();

        for (ItemPedidoEntity item : itens) {
            ProdutoEntity produto = buscarProdutoDoEstabelecimento(item.getProdutoId(), estabelecimentoId);
            if (!produto.isAtivo()) {
                throw new IllegalArgumentException("O produto " + produto.getNome() + " está inativo");
            }
            produtosDoPedido.put(produto.getId(), produto);

            if (produto.getTipo() == ProdutoTipo.PADRAO) {
                BigDecimal consumo = calcularQuantidadeEstoquePadrao(produto, item.getQuantidade());
                consumoTotalPorProduto.merge(produto.getId(), consumo, BigDecimal::add);
                produtosConsumidos.put(produto.getId(), produto);
            } else {
                List<ComposicaoDrinkEntity> receita = composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(produto.getId());
                if (receita.isEmpty()) {
                    throw new IllegalArgumentException("O drink " + produto.getNome() + " não possui ingredientes cadastrados");
                }
                validarReceitaParaSaida(receita, estabelecimentoId);
                receitasPorDrink.put(produto.getId(), receita);
                for (ComposicaoDrinkEntity comp : receita) {
                    ProdutoEntity ingrediente = comp.getProduto();
                    BigDecimal consumo = comp.getQuantidade().multiply(item.getQuantidade());
                    consumoTotalPorProduto.merge(ingrediente.getId(), consumo, BigDecimal::add);
                    produtosConsumidos.put(ingrediente.getId(), ingrediente);
                }
            }
        }

        if (consumoTotalPorProduto.isEmpty()) {
            return;
        }

        Map<UUID, EstoqueEntity> estoques = estoqueRepository.findAllByProdutoIdInForUpdate(consumoTotalPorProduto.keySet())
                .stream()
                .collect(Collectors.toMap(e -> e.getProduto().getId(), Function.identity()));

        for (Map.Entry<UUID, BigDecimal> entry : consumoTotalPorProduto.entrySet()) {
            ProdutoEntity produto = produtosConsumidos.get(entry.getKey());
            EstoqueEntity estoque = estoques.get(entry.getKey());
            validarSaldoVendavel(produto, estoque, entry.getValue());
        }

        String motivoPadrao = "Venda concluída - Pedido #" + pedido.getNumero();
        LocalDateTime agora = LocalDateTime.now();
        for (ItemPedidoEntity item : itens) {
            ProdutoEntity produto = produtosDoPedido.get(item.getProdutoId());
            if (produto.getTipo() == ProdutoTipo.PADRAO) {
                EstoqueEntity estoque = estoques.get(produto.getId());
                BigDecimal consumo = calcularQuantidadeEstoquePadrao(produto, item.getQuantidade());
                registrarBaixaPedidoComFefo(
                        estoque,
                        TipoMovimentacaoEstoque.SAIDA,
                        consumo,
                        motivoPadrao,
                        "PEDIDO",
                        operadorUsuarioId,
                        item.getId(),
                        null,
                        null,
                        agora
                );
            } else {
                List<ComposicaoDrinkEntity> receita = receitasPorDrink.get(produto.getId());
                JsonNode snapshot = criarSnapshotReceita(produto, receita, item.getQuantidade());
                for (ComposicaoDrinkEntity comp : receita) {
                    EstoqueEntity estoqueIngrediente = estoques.get(comp.getProduto().getId());
                    BigDecimal consumo = comp.getQuantidade().multiply(item.getQuantidade());
                    registrarBaixaPedidoComFefo(
                            estoqueIngrediente,
                            TipoMovimentacaoEstoque.SAIDA_DRINK,
                            consumo,
                            motivoPadrao,
                            "PEDIDO",
                            operadorUsuarioId,
                            item.getId(),
                            produto.getId(),
                            snapshot.deepCopy(),
                            agora
                    );
                }
            }
        }
    }

    /**
     * Realiza baixa de estoque para pedido aplicando FEFO sem lotes vencidos, gravando
     * histórico e vínculos em movimentacao_estoque_lote. Requer estoque bloqueado (FOR UPDATE).
     */
    public MovimentacaoEstoqueEntity registrarBaixaPedidoComFefo(
            EstoqueEntity estoque,
            TipoMovimentacaoEstoque tipo,
            BigDecimal quantidade,
            String motivo,
            String origem,
            UUID usuarioId,
            Long itemPedidoId,
            UUID origemDrinkId,
            JsonNode receitaAplicada,
            LocalDateTime ocorridoEm
    ) {
        if (tipo != TipoMovimentacaoEstoque.SAIDA && tipo != TipoMovimentacaoEstoque.SAIDA_DRINK) {
            throw new IllegalArgumentException("Tipo de movimentação inválido para baixa de pedido: " + tipo);
        }
        BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
        BigDecimal saldoPosterior = saldoAnterior.subtract(quantidade);
        if (saldoPosterior.compareTo(ZERO) < 0) {
            throw new EstoqueInsuficientePedidoException(
                    "Saldo de estoque insuficiente para o produto " + estoque.getProduto().getNome(),
                    estoque.getProduto().getNome(),
                    saldoAnterior
            );
        }

        List<Alocacao> alocacoes;
        try {
            // lotes antes do saldo: reconciliar() compara soma dos lotes com o saldo ainda não alterado
            alocacoes = controleLotes.movimentar(
                    estoque,
                    PoliticaLote.DEBITO_SEM_VENCIDOS,
                    quantidade,
                    null,
                    null,
                    null
            );
        } catch (IllegalArgumentException ex) {
            throw new EstoqueInsuficientePedidoException(
                    ex.getMessage(),
                    estoque.getProduto().getNome(),
                    controleLotes.saldoNaoVencidoDisponivel(estoque)
            );
        }

        estoque.setQuantidadeAtual(saldoPosterior);
        estoqueRepository.save(estoque);

        return persistirMovimentacaoPedido(
                estoque,
                tipo,
                quantidade,
                saldoAnterior,
                saldoPosterior,
                motivo,
                origem,
                usuarioId,
                itemPedidoId,
                origemDrinkId,
                receitaAplicada,
                ocorridoEm,
                alocacoes
        );
    }

    @Transactional
    public void estornarEstoquePedido(
            PedidoEntity pedido,
            List<ItemPedidoEntity> itens,
            UUID usuarioId
    ) {
        if (itens == null || itens.isEmpty()) {
            return;
        }
        List<Long> itemIds = itens.stream()
                .map(ItemPedidoEntity::getId)
                .filter(id -> id != null)
                .toList();
        if (itemIds.isEmpty()) {
            return;
        }

        List<MovimentacaoEstoqueEntity> saidas = movimentacaoEstoqueRepository
                .findAllByItemPedidoIdInAndTipoInOrderByIdAsc(
                        itemIds,
                        Set.of(TipoMovimentacaoEstoque.SAIDA, TipoMovimentacaoEstoque.SAIDA_DRINK)
                );
        if (saidas.isEmpty()) {
            return;
        }

        Set<UUID> produtoIds = saidas.stream()
                .map(m -> m.getEstoque().getProduto().getId())
                .collect(Collectors.toSet());
        Map<UUID, EstoqueEntity> estoques = estoqueRepository.findAllByProdutoIdInForUpdate(produtoIds)
                .stream()
                .collect(Collectors.toMap(e -> e.getProduto().getId(), Function.identity()));

        List<Long> idsSaidas = saidas.stream()
                .map(MovimentacaoEstoqueEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<MovimentacaoLoteEntity>> vinculosPorMovimentacao = idsSaidas.isEmpty()
                ? Map.of()
                : movimentacaoLoteRepository.findAllByMovimentacaoIdInOrderByIdAsc(idsSaidas).stream()
                        .collect(Collectors.groupingBy(vinculo -> vinculo.getMovimentacao().getId()));

        String motivoEstorno = "Estorno do pedido #" + pedido.getNumero();
        LocalDateTime agora = LocalDateTime.now();
        for (MovimentacaoEstoqueEntity saida : saidas) {
            UUID produtoId = saida.getEstoque().getProduto().getId();
            EstoqueEntity estoque = estoques.get(produtoId);
            if (estoque == null) {
                throw new EntityNotFoundException("Estoque do produto não encontrado para estorno");
            }
            List<MovimentacaoLoteEntity> vinculosOriginais = saida.getId() == null
                    ? List.of()
                    : vinculosPorMovimentacao.getOrDefault(saida.getId(), List.of());
            List<Alocacao> alocacoes = controleLotes.devolverParaLotes(
                    estoque,
                    saida.getQuantidade(),
                    vinculosOriginais
            );
            BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
            BigDecimal saldoPosterior = saldoAnterior.add(saida.getQuantidade());
            estoque.setQuantidadeAtual(saldoPosterior);
            estoqueRepository.save(estoque);

            persistirMovimentacaoPedido(
                    estoque,
                    TipoMovimentacaoEstoque.AJUSTE_POSITIVO,
                    saida.getQuantidade(),
                    saldoAnterior,
                    saldoPosterior,
                    motivoEstorno,
                    "PEDIDO",
                    usuarioId,
                    saida.getItemPedidoId(),
                    null,
                    null,
                    agora,
                    alocacoes
            );
        }
    }

    private BigDecimal calcularQuantidadeEstoquePadrao(ProdutoEntity produto, BigDecimal quantidadeVendida) {
        if (produto.isFracionado() && produto.getVolumeEmbalagemMl() != null) {
            return quantidadeVendida.multiply(produto.getVolumeEmbalagemMl());
        }
        return quantidadeVendida;
    }

    private BigDecimal converterQuantidadeMovimentacao(
            ProdutoEntity produto,
            BigDecimal quantidade,
            boolean emFardo
    ) {
        if (!emFardo) {
            return quantidade;
        }
        if (!produto.isFardo() || produto.getUnidadesPorFardo() == null || produto.getUnidadesPorFardo() < 2) {
            throw new IllegalArgumentException("O produto informado não possui configuração de fardo");
        }
        return quantidade.multiply(BigDecimal.valueOf(produto.getUnidadesPorFardo()));
    }

    private String comporMotivoMovimentacao(String motivo, BigDecimal quantidadeInformada, boolean emFardo) {
        if (!emFardo) {
            return motivo;
        }
        String sufixo = quantidadeInformada.stripTrailingZeros().toPlainString() + " fardo(s)";
        return motivo == null ? sufixo : motivo + " (" + sufixo + ")";
    }

    private MovimentacaoEstoqueEntity persistirMovimentacaoPedido(
            EstoqueEntity estoque,
            TipoMovimentacaoEstoque tipo,
            BigDecimal quantidade,
            BigDecimal saldoAnterior,
            BigDecimal saldoPosterior,
            String motivo,
            String origem,
            UUID usuarioId,
            Long itemPedidoId,
            UUID origemDrinkId,
            JsonNode receitaAplicada,
            LocalDateTime ocorridoEm,
            List<Alocacao> alocacoes
    ) {
        MovimentacaoEstoqueEntity movimentacao = new MovimentacaoEstoqueEntity();
        movimentacao.setEstoque(estoque);
        movimentacao.setUsuarioId(usuarioId);
        movimentacao.setOrigem(origem);
        movimentacao.setItemPedidoId(itemPedidoId);
        movimentacao.setTipo(tipo);
        movimentacao.setQuantidade(quantidade);
        movimentacao.setSaldoAnterior(saldoAnterior);
        movimentacao.setSaldoPosterior(saldoPosterior);
        movimentacao.setMotivo(motivo);
        movimentacao.setLote(ControleLotesEstoque.descreverCodigos(alocacoes));
        movimentacao.setOrigemDrinkId(origemDrinkId);
        movimentacao.setReceitaAplicada(receitaAplicada);
        movimentacao.setOcorridoEm(ocorridoEm);
        MovimentacaoEstoqueEntity movimentacaoSalva = movimentacaoEstoqueRepository.saveAndFlush(movimentacao);
        controleLotes.vincular(movimentacaoSalva, alocacoes);
        return movimentacaoSalva;
    }

    private MovimentacaoResponseDto aplicarMovimentacao(
            EstoqueEntity estoque,
            MovimentacaoCommand command,
            UUID usuarioId,
            String usuarioNome
    ) {
        EstrategiaMovimentacaoEstoque estrategia = estrategiaPara(command.tipo());
        estrategia.validar(command);
        BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
        BigDecimal saldoPosterior = estrategia.calcularSaldoPosterior(saldoAnterior, command.quantidade());
        // lotes antes do saldo: a reconciliação compara a soma dos lotes com o saldo ainda não alterado
        List<Alocacao> alocacoes = controleLotes.movimentar(
                estoque, estrategia.politicaLote(), command.quantidade(),
                command.lote(), command.validade(), command.loteId()
        );
        estoque.setQuantidadeAtual(saldoPosterior);
        estoqueRepository.save(estoque);
        MovimentacaoEstoqueEntity movimentacao = salvarHistorico(
                estoque, command, saldoAnterior, saldoPosterior, usuarioId,
                ControleLotesEstoque.descreverCodigos(alocacoes));
        controleLotes.vincular(movimentacao, alocacoes);
        return paraResposta(movimentacao, usuarioNome, alocacoes.stream()
                .map(alocacao -> paraLoteResposta(alocacao.lote(), alocacao.quantidade()))
                .toList());
    }

    private MovimentacaoEstoqueEntity salvarHistorico(
            EstoqueEntity estoque,
            MovimentacaoCommand command,
            BigDecimal saldoAnterior,
            BigDecimal saldoPosterior,
            UUID usuarioId,
            String codigosLotes
    ) {
        MovimentacaoEstoqueEntity movimentacao = new MovimentacaoEstoqueEntity();
        movimentacao.setEstoque(estoque);
        movimentacao.setUsuarioId(usuarioId);
        movimentacao.setTipo(command.tipo());
        movimentacao.setQuantidade(command.quantidade());
        movimentacao.setSaldoAnterior(saldoAnterior);
        movimentacao.setSaldoPosterior(saldoPosterior);
        movimentacao.setMotivo(command.motivo());
        movimentacao.setLote(codigosLotes != null ? codigosLotes : command.lote());
        movimentacao.setNumeroNotaFiscal(command.numeroNotaFiscal());
        movimentacao.setOrigemDrinkId(command.origemDrinkId());
        movimentacao.setReceitaAplicada(command.receitaAplicada());
        movimentacao.setOcorridoEm(LocalDateTime.now());
        return movimentacaoEstoqueRepository.save(movimentacao);
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

    private EstrategiaMovimentacaoEstoque estrategiaPara(TipoMovimentacaoEstoque tipo) {
        EstrategiaMovimentacaoEstoque estrategia = estrategias.get(tipo);
        if (estrategia == null) {
            throw new IllegalStateException("Estratégia não configurada para " + tipo);
        }
        return estrategia;
    }

    private void validarReceitaParaSaida(
            List<ComposicaoDrinkEntity> receita,
            UUID idEstabelecimento
    ) {
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

    private Map<Long, List<MovimentacaoLoteResponseDto>> lotesPorMovimentacao(List<MovimentacaoEstoqueEntity> movimentacoes) {
        List<Long> ids = movimentacoes.stream().map(MovimentacaoEstoqueEntity::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return movimentacaoLoteRepository.findAllByMovimentacaoIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(
                        vinculo -> vinculo.getMovimentacao().getId(),
                        Collectors.mapping(vinculo -> paraLoteResposta(vinculo.getLote(), vinculo.getQuantidade()),
                                Collectors.toList())
                ));
    }

    private MovimentacaoLoteResponseDto paraLoteResposta(LoteEstoqueEntity lote, BigDecimal quantidade) {
        return new MovimentacaoLoteResponseDto(lote.getId(), lote.getCodigo(), lote.getValidade(), quantidade);
    }

    private MovimentacaoResponseDto paraResposta(
            MovimentacaoEstoqueEntity movimentacao,
            String usuarioNome,
            List<MovimentacaoLoteResponseDto> lotes
    ) {
        ProdutoEntity produto = movimentacao.getEstoque().getProduto();
        return new MovimentacaoResponseDto(
                movimentacao.getId(), usuarioNome, produto.getId(), produto.getNome(),
                produto.isFracionado() ? "ML" : produto.getUnidadeMedida(),
                movimentacao.getTipo(), movimentacao.getQuantidade(),
                movimentacao.getSaldoAnterior(), movimentacao.getSaldoPosterior(),
                movimentacao.getMotivo(), movimentacao.getLote(), movimentacao.getNumeroNotaFiscal(),
                movimentacao.getOrigemDrinkId(), receitaAplicadaParaResposta(movimentacao.getReceitaAplicada()), movimentacao.getOcorridoEm(),
                lotes
        );
    }

    private Map<UUID, String> nomesUsuariosPorId(List<MovimentacaoEstoqueEntity> movimentacoes) {
        Set<UUID> idsUsuarios = movimentacoes.stream()
                .map(MovimentacaoEstoqueEntity::getUsuarioId)
                .collect(Collectors.toSet());
        if (idsUsuarios.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(idsUsuarios).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));
    }

    private String nomeUsuario(UUID usuarioId) {
        return userRepository.findById(usuarioId)
                .map(UserEntity::getName)
                .orElse("Usuário não encontrado");
    }

    private RawValue receitaAplicadaParaResposta(JsonNode receitaAplicada) {
        return receitaAplicada == null ? null : new RawValue(receitaAplicada.toString());
    }

    private ProdutoEntity buscarProdutoDoEstabelecimento(UUID idProduto, UUID idEstabelecimento) {
        ProdutoEntity produto = produtoRepository.findById(idProduto)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
        if (!produto.getEstabelecimentoId().equals(idEstabelecimento)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return produto;
    }

    private UUID obterIdEstabelecimentoAutenticado() {
        return UUID.fromString(obterClaimObrigatoria("establishmentId"));
    }

    private UUID obterIdUsuarioAutenticado() {
        return UUID.fromString(obterClaimObrigatoria("userId"));
    }

    private String obterClaimObrigatoria(String nomeClaim) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            String valor = token.getToken().getClaimAsString(nomeClaim);
            if (valor == null || valor.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }
            UUID.fromString(valor);
            return valor;
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }

    private String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private record ItemSaidaDrink(EstoqueEntity estoque, BigDecimal quantidade) {
    }
}
