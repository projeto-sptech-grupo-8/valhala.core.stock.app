package valhalla.core.stock.app.modules.estoque.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.exception.ProdutoPossuiMovimentacoesException;
import valhalla.core.stock.app.modules.estoque.mapper.ProdutoMapper;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.MovimentacaoEstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Service
public class ProdutoService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final EstoqueRepository estoqueRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;

    public ProdutoService(
            ProdutoRepository produtoRepository,
            CategoriaRepository categoriaRepository,
            EstoqueRepository estoqueRepository,
            MovimentacaoEstoqueService movimentacaoEstoqueService,
            MovimentacaoEstoqueRepository movimentacaoEstoqueRepository
    ) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.estoqueRepository = estoqueRepository;
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'VISUALIZAR_ESTOQUE',
                authentication
            )
            """)
    public RespostaPaginadaDto<ProdutoResponseDto> listarProdutos(
            String busca,
            Integer categoriaId,
            ProdutoTipo tipo,
            Boolean ativo,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        Specification<ProdutoEntity> especificacao = (root, query, builder) ->
                builder.equal(root.get("estabelecimentoId"), idEstabelecimento);
        String buscaNormalizada = normalizarBusca(busca);

        if (buscaNormalizada != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("nome")), "%" + buscaNormalizada + "%"),
                    builder.like(builder.lower(root.get("sku")), "%" + buscaNormalizada + "%"),
                    builder.like(builder.lower(root.get("codigoBarras")), "%" + buscaNormalizada + "%")
            ));
        }
        if (categoriaId != null) {
            especificacao = especificacao.and((root, query, builder) ->
                    builder.equal(root.get("categoria").get("id"), categoriaId));
        }
        if (tipo != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.equal(root.get("tipo"), tipo));
        }
        if (ativo != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.equal(root.get("ativo"), ativo));
        }

        Page<ProdutoEntity> resultado = produtoRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao,
                        Set.of("nome", "sku", "precoVenda", "precoCusto", "criadoEm", "atualizadoEm"))
        );
        List<ProdutoEntity> produtos = resultado.getContent();
        Map<UUID, EstoqueEntity> estoques = estoquesPorProduto(produtos);
        return RespostaPaginadaDto.de(resultado,
                produto -> ProdutoMapper.paraResposta(produto, estoques.get(produto.getId())));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'VISUALIZAR_ESTOQUE',
                authentication
            )
            """)
    public ProdutoResponseDto buscarProduto(UUID idProduto) {
        ProdutoEntity produto = buscarProdutoDoEstabelecimentoAtual(idProduto);
        EstoqueEntity estoque = estoqueRepository.findByProdutoId(produto.getId()).orElse(null);
        return ProdutoMapper.paraResposta(produto, estoque);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'GERENCIAR_ESTOQUE',
                authentication
            )
            """)
    public ProdutoResponseDto criarProduto(ProdutoRequestDto dto) {
        ProdutoTipo tipo = dto.tipo() == null ? ProdutoTipo.PADRAO : dto.tipo();
        validarCamposEspecificosDoTipo(tipo, Boolean.TRUE.equals(dto.fracionado()),
                dto.volumeEmbalagemMl(), dto.estoqueMinimo(), dto.quantidadeInicial());

        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        ProdutoEntity produto = new ProdutoEntity();
        produto.setEstabelecimentoId(idEstabelecimento);
        produto.setCategoria(buscarCategoriaAtivaDoEstabelecimento(dto.categoriaId(), idEstabelecimento));
        produto.setSku(gerarSku(idEstabelecimento));
        produto.setNome(normalizarObrigatorio(dto.nome(), "O nome do produto não pode ficar vazio"));
        produto.setDescricao(normalizarOpcional(dto.descricao()));
        produto.setTipo(tipo);
        produto.setUnidadeMedida(normalizarObrigatorio(dto.unidadeMedida(), "A unidade de medida não pode ficar vazia"));
        produto.setPrecoCusto(dto.precoCusto() == null ? ZERO : dto.precoCusto());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setCodigoBarras(normalizarOpcional(dto.codigoBarras()));
        produto.setFracionado(Boolean.TRUE.equals(dto.fracionado()));
        produto.setVolumeEmbalagemMl(dto.volumeEmbalagemMl());
        produto.setAtivo(true);

        ProdutoEntity produtoSalvo = produtoRepository.saveAndFlush(produto);
        EstoqueEntity estoque = tipo == ProdutoTipo.PADRAO
                ? criarEstoqueInicial(produtoSalvo, dto.quantidadeInicial(), dto.estoqueMinimo())
                : null;
        if (estoque != null) {
            movimentacaoEstoqueService.registrarEntradaInicial(estoque, estoque.getQuantidadeAtual());
        }

        return ProdutoMapper.paraResposta(produtoSalvo, estoque);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'GERENCIAR_ESTOQUE',
                authentication
            )
            """)
    public ProdutoResponseDto atualizarProduto(UUID idProduto, ProdutoAtualizacaoRequestDto dto) {
        ProdutoEntity produto = buscarProdutoDoEstabelecimentoAtual(idProduto);
        validarAlteracaoDeFracionamento(produto, dto);
        if (dto.volumeEmbalagemMl() != null) {
            validarVolumeParaTipo(produto.getTipo(), dto.volumeEmbalagemMl());
        }

        CategoriaEntity categoria = dto.categoriaId() == null
                ? null
                : buscarCategoriaAtivaDoEstabelecimento(dto.categoriaId(), produto.getEstabelecimentoId());
        ProdutoMapper.aplicarAtualizacao(produto, dto, categoria);

        EstoqueEntity estoque = estoqueRepository.findByProdutoId(produto.getId()).orElse(null);
        atualizarEstoqueMinimo(dto.estoqueMinimo(), produto.getTipo(), estoque);

        ProdutoEntity produtoSalvo = produtoRepository.saveAndFlush(produto);
        return ProdutoMapper.paraResposta(produtoSalvo, estoque);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'GERENCIAR_ESTOQUE',
                authentication
            )
            """)
    public void excluirProduto(UUID idProduto) {
        ProdutoEntity produto = buscarProdutoDoEstabelecimentoAtual(idProduto);
        estoqueRepository.deleteByProdutoId(produto.getId());

        try {
            produtoRepository.delete(produto);
            produtoRepository.flush();
        } catch (DataIntegrityViolationException excecao) {
            throw new ProdutoPossuiMovimentacoesException(
                    "Produto não pode ser excluído porque possui movimentações ou vínculos registrados",
                    excecao
            );
        }
    }

    private EstoqueEntity criarEstoqueInicial(
            ProdutoEntity produto,
            BigDecimal quantidadeInicial,
            BigDecimal estoqueMinimo
    ) {
        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(quantidadeInicial == null ? ZERO : quantidadeInicial);
        estoque.setQuantidadeReservada(ZERO);
        estoque.setEstoqueMinimo(estoqueMinimo);
        return estoqueRepository.saveAndFlush(estoque);
    }

    private ProdutoEntity buscarProdutoDoEstabelecimentoAtual(UUID idProduto) {
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        ProdutoEntity produto = produtoRepository.findById(idProduto)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        if (!produto.getEstabelecimentoId().equals(idEstabelecimento)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return produto;
    }

    private CategoriaEntity buscarCategoriaAtivaDoEstabelecimento(
            Integer idCategoria,
            UUID idEstabelecimento
    ) {
        CategoriaEntity categoria = categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        if (!categoria.getEstabelecimentoId().equals(idEstabelecimento)) {
            throw new AccessDeniedException("Acesso negado");
        }
        if (!categoria.isAtivo()) {
            throw new IllegalArgumentException("A categoria informada está inativa");
        }
        return categoria;
    }

    private Map<UUID, EstoqueEntity> estoquesPorProduto(Collection<ProdutoEntity> produtos) {
        if (produtos.isEmpty()) {
            return Map.of();
        }

        List<UUID> idsProdutos = produtos.stream().map(ProdutoEntity::getId).toList();
        Map<UUID, EstoqueEntity> resultado = new HashMap<>();
        estoqueRepository.findAllByProdutoIdIn(idsProdutos)
                .forEach(estoque -> resultado.put(estoque.getProduto().getId(), estoque));
        return resultado;
    }

    private String gerarSku(UUID idEstabelecimento) {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            String sku = "PRD-" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
            if (!produtoRepository.existsByEstabelecimentoIdAndSku(idEstabelecimento, sku)) {
                return sku;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um SKU único para o produto");
    }

    private void validarCamposEspecificosDoTipo(
            ProdutoTipo tipo,
            boolean fracionado,
            BigDecimal volumeEmbalagemMl,
            BigDecimal estoqueMinimo,
            BigDecimal quantidadeInicial
    ) {
        if (tipo != ProdutoTipo.DRINK) {
            if (fracionado && volumeEmbalagemMl == null) {
                throw new IllegalArgumentException(
                        "Produto fracionado deve informar o volume da embalagem em mililitros"
                );
            }
            return;
        }
        if (fracionado || volumeEmbalagemMl != null || estoqueMinimo != null || quantidadeInicial != null) {
            throw new IllegalArgumentException(
                    "Drink não possui estoque próprio; informe esses dados nos produtos ingredientes"
            );
        }
    }

    private void validarVolumeParaTipo(ProdutoTipo tipo, BigDecimal volumeEmbalagemMl) {
        if (tipo == ProdutoTipo.DRINK && volumeEmbalagemMl != null) {
            throw new IllegalArgumentException("Volume de embalagem é aplicável apenas a produto padrão");
        }
    }

    private void validarAlteracaoDeFracionamento(
            ProdutoEntity produto,
            ProdutoAtualizacaoRequestDto dto
    ) {
        boolean alteraFracionado = dto.fracionado() != null && dto.fracionado() != produto.isFracionado();
        boolean alteraVolume = dto.volumeEmbalagemMl() != null
                && (produto.getVolumeEmbalagemMl() == null
                || dto.volumeEmbalagemMl().compareTo(produto.getVolumeEmbalagemMl()) != 0);
        if (!alteraFracionado && !alteraVolume) {
            return;
        }
        if (produto.getTipo() == ProdutoTipo.DRINK) {
            throw new IllegalArgumentException("Drink não possui características de fracionamento próprias");
        }
        if (estoqueRepository.findByProdutoId(produto.getId()).isPresent()
                || movimentacaoEstoqueRepository.existsByEstoqueProdutoId(produto.getId())) {
            throw new IllegalArgumentException(
                    "Não é permitido alterar fracionamento ou volume de produto que já possui estoque ou movimentações"
            );
        }
        boolean seraFracionado = dto.fracionado() == null ? produto.isFracionado() : dto.fracionado();
        BigDecimal volumeFinal = dto.volumeEmbalagemMl() == null
                ? produto.getVolumeEmbalagemMl()
                : dto.volumeEmbalagemMl();
        if (seraFracionado && volumeFinal == null) {
            throw new IllegalArgumentException(
                    "Produto fracionado deve informar o volume da embalagem em mililitros"
            );
        }
    }

    private void atualizarEstoqueMinimo(
            BigDecimal estoqueMinimo,
            ProdutoTipo tipoProduto,
            EstoqueEntity estoque
    ) {
        if (estoqueMinimo == null) {
            return;
        }
        if (tipoProduto == ProdutoTipo.DRINK || estoque == null) {
            throw new IllegalArgumentException("Drink não possui estoque mínimo próprio");
        }
        estoque.setEstoqueMinimo(estoqueMinimo);
        estoqueRepository.save(estoque);
    }

    private String normalizarBusca(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }
        return busca.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizarObrigatorio(String valor, String mensagemErro) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagemErro);
        }
        return valor.trim();
    }

    private String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
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
        } catch (IllegalArgumentException excecao) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
