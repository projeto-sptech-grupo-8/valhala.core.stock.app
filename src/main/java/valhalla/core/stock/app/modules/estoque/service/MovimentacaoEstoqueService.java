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
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.SaidaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.MovimentacaoEstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.estoque.strategy.EstrategiaMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.strategy.MovimentacaoCommand;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
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

    public MovimentacaoEstoqueService(
            ProdutoRepository produtoRepository,
            EstoqueRepository estoqueRepository,
            ComposicaoDrinkRepository composicaoDrinkRepository,
            MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
            UserRepository userRepository,
            List<EstrategiaMovimentacaoEstoque> estrategias,
            BusinessEventLogger businessEventLogger
    ) {
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
        return RespostaPaginadaDto.de(resultado,
                movimentacao -> paraResposta(movimentacao,
                        nomesUsuarios.getOrDefault(movimentacao.getUsuarioId(), "Usuário não encontrado")));
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
        MovimentacaoCommand command = new MovimentacaoCommand(
                dto.tipo(), dto.quantidade(), normalizar(dto.motivo()), normalizar(dto.lote()),
                normalizar(dto.numeroNotaFiscal()), null, null
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
        MovimentacaoEstoqueEntity movimento = salvarHistorico(
                estoque, command, ZERO, saldoPosterior, obterIdUsuarioAutenticado());
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
        estoque.setQuantidadeAtual(saldoPosterior);
        estoqueRepository.save(estoque);
        return paraResposta(salvarHistorico(estoque, command, saldoAnterior, saldoPosterior, usuarioId), usuarioNome);
    }

    private MovimentacaoEstoqueEntity salvarHistorico(
            EstoqueEntity estoque,
            MovimentacaoCommand command,
            BigDecimal saldoAnterior,
            BigDecimal saldoPosterior,
            UUID usuarioId
    ) {
        MovimentacaoEstoqueEntity movimentacao = new MovimentacaoEstoqueEntity();
        movimentacao.setEstoque(estoque);
        movimentacao.setUsuarioId(usuarioId);
        movimentacao.setTipo(command.tipo());
        movimentacao.setQuantidade(command.quantidade());
        movimentacao.setSaldoAnterior(saldoAnterior);
        movimentacao.setSaldoPosterior(saldoPosterior);
        movimentacao.setMotivo(command.motivo());
        movimentacao.setLote(command.lote());
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

    private MovimentacaoResponseDto paraResposta(MovimentacaoEstoqueEntity movimentacao, String usuarioNome) {
        ProdutoEntity produto = movimentacao.getEstoque().getProduto();
        return new MovimentacaoResponseDto(
                movimentacao.getId(), usuarioNome, produto.getId(), produto.getNome(),
                produto.isFracionado() ? "ML" : produto.getUnidadeMedida(),
                movimentacao.getTipo(), movimentacao.getQuantidade(),
                movimentacao.getSaldoAnterior(), movimentacao.getSaldoPosterior(),
                movimentacao.getMotivo(), movimentacao.getLote(), movimentacao.getNumeroNotaFiscal(),
                movimentacao.getOrigemDrinkId(), receitaAplicadaParaResposta(movimentacao.getReceitaAplicada()), movimentacao.getOcorridoEm()
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
