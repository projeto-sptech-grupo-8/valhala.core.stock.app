package valhalla.core.stock.app.modules.estoque.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoLoteResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.SaidaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.LoteEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MotivoPerda;
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
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
        String motivo = normalizar(dto.motivo());
        MovimentacaoCommand command = new MovimentacaoCommand(
                dto.tipo(), dto.quantidade(), motivo, normalizar(dto.lote()),
                normalizar(dto.numeroNotaFiscal()), null, null, dto.validade(), dto.loteId(),
                resolverMotivoPerda(dto.tipo(), dto.motivoPerda(), motivo), null
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

        UUID usuarioId = obterIdUsuarioAutenticado();
        List<MovimentacaoResponseDto> resposta = baixarDrink(
                drink, dto.quantidade(), normalizar(dto.motivo()), usuarioId, nomeUsuario(usuarioId), null);
        businessEventLogger.success("stock.drink-output.registered", "product", drink.getId());
        return resposta;
    }

    /**
     * Baixa o estoque de um item vendido, vinculando as movimentações ao item do pedido. Produto padrão sai
     * por FEFO sem vencidos (garrafas viram ML quando fracionado); drink baixa os ingredientes da receita.
     * Chamado dentro da transação da venda, que já validou a permissão e o estabelecimento.
     */
    @Transactional
    public void baixarItemVendido(
            ProdutoEntity produto,
            BigDecimal quantidadeVendida,
            Long idItemPedido,
            String motivo,
            UUID usuarioId
    ) {
        if (produto.getTipo() == ProdutoTipo.DRINK) {
            try {
                baixarDrink(produto, quantidadeVendida, motivo, usuarioId, null, idItemPedido);
            } catch (IllegalArgumentException excecao) {
                throw new IllegalArgumentException(produto.getNome() + ": " + excecao.getMessage(), excecao);
            }
            return;
        }
        EstoqueEntity estoque = estoqueRepository.findByProdutoIdForUpdate(produto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Estoque do produto não encontrado"));
        BigDecimal quantidadeEstoque = produto.isFracionado()
                ? quantidadeVendida.multiply(produto.getVolumeEmbalagemMl())
                : quantidadeVendida;
        try {
            aplicarMovimentacao(estoque, new MovimentacaoCommand(
                    TipoMovimentacaoEstoque.SAIDA, quantidadeEstoque, motivo, null, null, null, null,
                    null, null, null, idItemPedido
            ), usuarioId, null);
        } catch (IllegalArgumentException excecao) {
            throw new IllegalArgumentException(produto.getNome() + ": " + excecao.getMessage(), excecao);
        }
    }

    /**
     * Devolve ao estoque, nos mesmos lotes de origem, tudo o que foi baixado para os itens informados.
     * Cada baixa gera um AJUSTE_POSITIVO vinculado ao mesmo item do pedido.
     */
    @Transactional
    public void estornarItensVendidos(Collection<Long> idsItensPedido, String motivo, UUID usuarioId) {
        if (idsItensPedido.isEmpty()) {
            return;
        }
        List<MovimentacaoEstoqueEntity> baixas = movimentacaoEstoqueRepository
                .findAllByItemPedidoIdInOrderByIdAsc(idsItensPedido).stream()
                .filter(movimentacao -> movimentacao.getTipo() == TipoMovimentacaoEstoque.SAIDA
                        || movimentacao.getTipo() == TipoMovimentacaoEstoque.SAIDA_DRINK)
                .toList();
        if (baixas.isEmpty()) {
            return;
        }
        Map<UUID, EstoqueEntity> estoques = estoqueRepository.findAllByProdutoIdInForUpdate(
                        baixas.stream().map(baixa -> baixa.getEstoque().getProduto().getId()).distinct().toList())
                .stream()
                .collect(Collectors.toMap(estoque -> estoque.getProduto().getId(), Function.identity()));
        Map<Long, List<MovimentacaoLoteEntity>> lotesPorBaixa = movimentacaoLoteRepository
                .findAllByMovimentacaoIdInOrderByIdAsc(baixas.stream().map(MovimentacaoEstoqueEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(vinculo -> vinculo.getMovimentacao().getId()));

        EstrategiaMovimentacaoEstoque estrategia = estrategiaPara(TipoMovimentacaoEstoque.AJUSTE_POSITIVO);
        for (MovimentacaoEstoqueEntity baixa : baixas) {
            EstoqueEntity estoque = estoques.get(baixa.getEstoque().getProduto().getId());
            MovimentacaoCommand command = new MovimentacaoCommand(
                    TipoMovimentacaoEstoque.AJUSTE_POSITIVO, baixa.getQuantidade(), motivo, null, null,
                    null, null, null, null, null, baixa.getItemPedidoId());
            estrategia.validar(command);
            BigDecimal saldoAnterior = estoque.getQuantidadeAtual();
            BigDecimal saldoPosterior = estrategia.calcularSaldoPosterior(saldoAnterior, baixa.getQuantidade());
            // lotes antes do saldo, como em aplicarMovimentacao
            List<Alocacao> alocacoes = controleLotes.devolver(estoque,
                    lotesPorBaixa.getOrDefault(baixa.getId(), List.of()).stream()
                            .map(vinculo -> new Alocacao(vinculo.getLote(), vinculo.getQuantidade()))
                            .toList(),
                    baixa.getQuantidade());
            estoque.setQuantidadeAtual(saldoPosterior);
            estoqueRepository.save(estoque);
            MovimentacaoEstoqueEntity estorno = salvarHistorico(estoque, command, saldoAnterior, saldoPosterior,
                    usuarioId, ControleLotesEstoque.descreverCodigos(alocacoes));
            controleLotes.vincular(estorno, alocacoes);
        }
    }

    private List<MovimentacaoResponseDto> baixarDrink(
            ProdutoEntity drink,
            BigDecimal quantidadeDrinks,
            String motivo,
            UUID usuarioId,
            String usuarioNome,
            Long idItemPedido
    ) {
        UUID idEstabelecimento = drink.getEstabelecimentoId();
        List<ComposicaoDrinkEntity> receita = composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId());
        if (receita.isEmpty()) {
            throw new IllegalArgumentException("O drink %s não possui ingredientes cadastrados".formatted(drink.getNome()));
        }
        validarReceitaParaSaida(receita, idEstabelecimento);

        List<UUID> idsIngredientes = receita.stream().map(item -> item.getProduto().getId()).toList();
        Map<UUID, EstoqueEntity> estoques = estoqueRepository.findAllByProdutoIdInForUpdate(idsIngredientes)
                .stream()
                .collect(Collectors.toMap(estoque -> estoque.getProduto().getId(), Function.identity()));
        if (estoques.size() != idsIngredientes.size()) {
            throw new EntityNotFoundException("Estoque de um ingrediente não encontrado");
        }

        JsonNode receitaAplicada = criarSnapshotReceita(drink, receita, quantidadeDrinks);
        List<ItemSaidaDrink> itens = receita.stream()
                .map(item -> new ItemSaidaDrink(
                        estoques.get(item.getProduto().getId()),
                        item.getQuantidade().multiply(quantidadeDrinks)
                ))
                .toList();
        MovimentacaoCommand commandBase = new MovimentacaoCommand(
                TipoMovimentacaoEstoque.SAIDA_DRINK, null, motivo, null, null,
                drink.getId(), receitaAplicada
        );

        EstrategiaMovimentacaoEstoque estrategia = estrategiaPara(TipoMovimentacaoEstoque.SAIDA_DRINK);
        estrategia.validar(commandBase);
        itens.forEach(item -> estrategia.calcularSaldoPosterior(
                item.estoque().getQuantidadeAtual(), item.quantidade()
        ));

        return itens.stream()
                .map(item -> aplicarMovimentacao(item.estoque(), new MovimentacaoCommand(
                        TipoMovimentacaoEstoque.SAIDA_DRINK,
                        item.quantidade(),
                        commandBase.motivo(), null, null, drink.getId(), receitaAplicada.deepCopy(),
                        null, null, null, idItemPedido
                ), usuarioId, usuarioNome))
                .toList();
    }

    private MotivoPerda resolverMotivoPerda(TipoMovimentacaoEstoque tipo, MotivoPerda informado, String motivo) {
        if (tipo != TipoMovimentacaoEstoque.PERDA) {
            if (informado != null) {
                throw new IllegalArgumentException("O motivo da perda só se aplica a movimentações do tipo PERDA");
            }
            return null;
        }
        return informado != null ? informado : MotivoPerda.inferir(motivo);
    }

    @Transactional
    public void registrarEntradaInicial(EstoqueEntity estoque, BigDecimal quantidadeInicial) {
        if (quantidadeInicial == null || quantidadeInicial.signum() == 0) {
            return;
        }
        MovimentacaoCommand command = new MovimentacaoCommand(
                TipoMovimentacaoEstoque.ENTRADA_INICIAL, quantidadeInicial,
                "Saldo inicial do produto", null, null, null, null
        );
        EstrategiaMovimentacaoEstoque estrategia = estrategiaPara(command.tipo());
        estrategia.validar(command);
        BigDecimal saldoPosterior = estrategia.calcularSaldoPosterior(ZERO, quantidadeInicial);
        if (estoque.getQuantidadeAtual().compareTo(saldoPosterior) != 0) {
            throw new IllegalStateException("O saldo inicial informado é inconsistente");
        }
        List<Alocacao> alocacoes = controleLotes.registrarSaldoInicial(estoque, quantidadeInicial);
        MovimentacaoEstoqueEntity movimento = salvarHistorico(
                estoque, command, ZERO, saldoPosterior, obterIdUsuarioAutenticado(),
                ControleLotesEstoque.descreverCodigos(alocacoes));
        controleLotes.vincular(movimento, alocacoes);
        businessEventLogger.success("stock.initial-entry.registered", "stock-movement", movimento.getId());
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
        movimentacao.setMotivoPerda(command.motivoPerda());
        movimentacao.setItemPedidoId(command.itemPedidoId());
        movimentacao.setLote(codigosLotes != null ? codigosLotes : command.lote());
        movimentacao.setNumeroNotaFiscal(command.numeroNotaFiscal());
        movimentacao.setOrigemDrinkId(command.origemDrinkId());
        movimentacao.setReceitaAplicada(command.receitaAplicada());
        movimentacao.setOcorridoEm(FusoHorarioOperacao.agora());
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
                movimentacao.getMotivo(), movimentacao.getMotivoPerda(), movimentacao.getLote(), movimentacao.getNumeroNotaFiscal(),
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
