package valhalla.core.stock.app.modules.estoque.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.dto.IngredienteDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.IngredienteDrinkResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.ReceitaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ReceitaDrinkService {

    private final ProdutoRepository produtoRepository;
    private final ComposicaoDrinkRepository composicaoDrinkRepository;

    public ReceitaDrinkService(
            ProdutoRepository produtoRepository,
            ComposicaoDrinkRepository composicaoDrinkRepository
    ) {
        this.produtoRepository = produtoRepository;
        this.composicaoDrinkRepository = composicaoDrinkRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_ESTOQUE', authentication)")
    public List<IngredienteDrinkResponseDto> buscarReceita(UUID idDrink) {
        ProdutoEntity drink = buscarDrinkDoEstabelecimentoAtual(idDrink);
        return composicaoDrinkRepository.findAllByDrinkIdOrderByIdAsc(drink.getId())
                .stream()
                .map(this::paraResposta)
                .toList();
    }

    @Transactional
    @PreAuthorize("@permissionAuthorizationService.hasPermission('GERENCIAR_ESTOQUE', authentication)")
    public List<IngredienteDrinkResponseDto> substituirReceita(
            UUID idDrink,
            ReceitaDrinkRequestDto dto
    ) {
        ProdutoEntity drink = buscarDrinkDoEstabelecimentoAtual(idDrink);
        validarIngredientes(dto.ingredientes(), drink.getEstabelecimentoId());

        composicaoDrinkRepository.deleteAllByDrinkId(drink.getId());
        composicaoDrinkRepository.flush();

        List<ComposicaoDrinkEntity> receita = dto.ingredientes().stream()
                .map(ingrediente -> criarComposicao(drink, ingrediente))
                .toList();

        return composicaoDrinkRepository.saveAll(receita).stream()
                .map(this::paraResposta)
                .toList();
    }

    private void validarIngredientes(
            List<IngredienteDrinkRequestDto> ingredientes,
            UUID idEstabelecimento
    ) {
        Set<UUID> idsProdutos = new HashSet<>();
        for (IngredienteDrinkRequestDto ingrediente : ingredientes) {
            if (!idsProdutos.add(ingrediente.produtoId())) {
                throw new IllegalArgumentException("Um ingrediente não pode se repetir na receita");
            }

            ProdutoEntity produto = buscarProdutoDoEstabelecimento(ingrediente.produtoId(), idEstabelecimento);
            if (produto.getTipo() != ProdutoTipo.PADRAO) {
                throw new IllegalArgumentException("A receita pode conter somente produtos do tipo PADRAO");
            }
            if (!produto.isAtivo()) {
                throw new IllegalArgumentException("O produto ingrediente está inativo");
            }

            UnidadeConsumoDrink unidadeEsperada = produto.isFracionado()
                    ? UnidadeConsumoDrink.ML
                    : UnidadeConsumoDrink.UN;
            if (ingrediente.unidadeConsumo() != unidadeEsperada) {
                throw new IllegalArgumentException(
                        "Ingrediente " + produto.getNome() + " deve usar a unidade " + unidadeEsperada
                );
            }
        }
    }

    private ComposicaoDrinkEntity criarComposicao(
            ProdutoEntity drink,
            IngredienteDrinkRequestDto ingrediente
    ) {
        ProdutoEntity produto = produtoRepository.findById(ingrediente.produtoId())
                .orElseThrow(() -> new EntityNotFoundException("Produto ingrediente não encontrado"));

        ComposicaoDrinkEntity composicao = new ComposicaoDrinkEntity();
        composicao.setDrink(drink);
        composicao.setProduto(produto);
        composicao.setQuantidade(ingrediente.quantidade());
        composicao.setUnidadeConsumo(ingrediente.unidadeConsumo());
        return composicao;
    }

    private IngredienteDrinkResponseDto paraResposta(ComposicaoDrinkEntity composicao) {
        return new IngredienteDrinkResponseDto(
                composicao.getProduto().getId(),
                composicao.getProduto().getNome(),
                composicao.getQuantidade(),
                composicao.getUnidadeConsumo()
        );
    }

    private ProdutoEntity buscarDrinkDoEstabelecimentoAtual(UUID idDrink) {
        ProdutoEntity produto = buscarProdutoDoEstabelecimento(idDrink, obterIdEstabelecimentoAutenticado());
        if (produto.getTipo() != ProdutoTipo.DRINK) {
            throw new IllegalArgumentException("O produto informado não é um drink");
        }
        return produto;
    }

    private ProdutoEntity buscarProdutoDoEstabelecimento(UUID idProduto, UUID idEstabelecimento) {
        ProdutoEntity produto = produtoRepository.findById(idProduto)
                .orElseThrow(() -> new EntityNotFoundException("Produto ingrediente não encontrado"));
        if (!produto.getEstabelecimentoId().equals(idEstabelecimento)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return produto;
    }

    private UUID obterIdEstabelecimentoAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }

        try {
            String idEstabelecimento = token.getToken().getClaimAsString("establishmentId");
            if (idEstabelecimento == null || idEstabelecimento.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }
            return UUID.fromString(idEstabelecimento);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
