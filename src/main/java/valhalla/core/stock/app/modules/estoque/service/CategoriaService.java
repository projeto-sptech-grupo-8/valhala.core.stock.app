package valhalla.core.stock.app.modules.estoque.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.estoque.dto.CategoriaRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.CategoriaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.CategoriaResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.exception.CategoriaJaExisteException;
import valhalla.core.stock.app.modules.estoque.exception.CategoriaPossuiProdutosException;
import valhalla.core.stock.app.modules.estoque.mapper.CategoriaMapper;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.Paginacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final BusinessEventLogger businessEventLogger;

    public CategoriaService(CategoriaRepository categoriaRepository, BusinessEventLogger businessEventLogger) {
        this.categoriaRepository = categoriaRepository;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'VISUALIZAR_ESTOQUE',
                authentication
            )
            """)
    public RespostaPaginadaDto<CategoriaResponseDto> listarCategorias(
            String busca,
            Boolean ativo,
            int pagina,
            int tamanho,
            String ordenarPor,
            DirecaoOrdenacao direcao
    ) {
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        String buscaNormalizada = busca == null || busca.isBlank() ? null : busca.trim().toLowerCase(Locale.ROOT);
        Specification<CategoriaEntity> especificacao = (root, query, builder) ->
                builder.equal(root.get("estabelecimentoId"), idEstabelecimento);

        if (buscaNormalizada != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.like(
                    builder.lower(root.get("nome")), "%" + buscaNormalizada + "%"
            ));
        }
        if (ativo != null) {
            especificacao = especificacao.and((root, query, builder) -> builder.equal(root.get("ativo"), ativo));
        }

        Page<CategoriaEntity> resultado = categoriaRepository.findAll(
                especificacao,
                Paginacao.criar(pagina, tamanho, ordenarPor, direcao,
                        Set.of("nome", "criadoEm", "atualizadoEm"))
        );
        return RespostaPaginadaDto.de(resultado, CategoriaMapper::paraResposta);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'GERENCIAR_ESTOQUE',
                authentication
            )
            """)
    public CategoriaResponseDto criarCategoria(CategoriaRequestDto dto) {
        UUID idEstabelecimento = obterIdEstabelecimentoAutenticado();
        String nome = dto.nome().trim();

        boolean existePorNomeAndEstabelecimento =
                categoriaRepository.existsByEstabelecimentoIdAndNomeIgnoreCase(idEstabelecimento, nome);

        if (existePorNomeAndEstabelecimento) {
            throw new CategoriaJaExisteException("Categoria já existe no estabelecimento");
        }

        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setEstabelecimentoId(idEstabelecimento);
        categoria.setNome(nome);
        categoria.setDescricao(normalizarDescricao(dto.descricao()));
        categoria.setAtivo(true);

        CategoriaEntity categoriaSalva = salvarCategoria(categoria);

        businessEventLogger.success("stock.category.created", "category", categoriaSalva.getId());
        return CategoriaMapper.paraResposta(categoriaSalva);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'VISUALIZAR_ESTOQUE',
                authentication
            )
            """)
    public CategoriaResponseDto buscarCategoria(Integer idCategoria) {
        return CategoriaMapper.paraResposta(buscarCategoriaDoEstabelecimentoAtual(idCategoria));
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'GERENCIAR_ESTOQUE',
                authentication
            )
            """)
    public CategoriaResponseDto atualizarCategoria(
            Integer idCategoria,
            CategoriaAtualizacaoRequestDto dto
    ) {
        validarAtualizacao(dto);
        CategoriaEntity categoria = buscarCategoriaDoEstabelecimentoAtual(idCategoria);

        if (dto.nome() != null) {
            String nome = dto.nome().trim();
            if (categoriaRepository.existsByEstabelecimentoIdAndNomeIgnoreCaseAndIdNot(
                    categoria.getEstabelecimentoId(), nome, categoria.getId())) {
                throw new CategoriaJaExisteException("Categoria já existe no estabelecimento");
            }
            categoria.setNome(nome);
        }

        if (dto.descricao() != null) {
            categoria.setDescricao(normalizarDescricao(dto.descricao()));
        }

        if (dto.ativo() != null) {
            categoria.setAtivo(dto.ativo());
        }

        CategoriaEntity categoriaSalva = salvarCategoria(categoria);
        businessEventLogger.success("stock.category.updated", "category", categoriaSalva.getId());
        return CategoriaMapper.paraResposta(categoriaSalva);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'GERENCIAR_ESTOQUE',
                authentication
            )
            """)
    public void excluirCategoria(Integer idCategoria) {
        CategoriaEntity categoria = buscarCategoriaDoEstabelecimentoAtual(idCategoria);

        try {
            categoriaRepository.delete(categoria);
            categoriaRepository.flush();
            businessEventLogger.success("stock.category.deleted", "category", idCategoria);
        } catch (DataIntegrityViolationException excecao) {
            throw new CategoriaPossuiProdutosException(
                    "Categoria não pode ser excluída porque possui produtos vinculados",
                    excecao
            );
        }
    }

    private CategoriaEntity buscarCategoriaDoEstabelecimentoAtual(Integer idCategoria) {
        CategoriaEntity categoria = categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        if (!categoria.getEstabelecimentoId().equals(obterIdEstabelecimentoAutenticado())) {
            throw new AccessDeniedException("Acesso negado");
        }

        return categoria;
    }

    private CategoriaEntity salvarCategoria(CategoriaEntity categoria) {
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException excecao) {
            throw new CategoriaJaExisteException("Categoria já existe no estabelecimento");
        }
    }

    private void validarAtualizacao(CategoriaAtualizacaoRequestDto dto) {
        if (dto.nome() == null && dto.descricao() == null && dto.ativo() == null) {
            throw new IllegalArgumentException("Informe ao menos um campo para atualização");
        }

        if (dto.nome() != null && dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome da categoria não pode ficar vazio");
        }
    }

    private String normalizarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        return descricao.trim();
    }

    private UUID obterIdEstabelecimentoAutenticado() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }

        try {
            String idEstabelecimento = token.getToken()
                    .getClaimAsString("establishmentId");

            if (idEstabelecimento == null || idEstabelecimento.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }

            return UUID.fromString(idEstabelecimento);
        } catch (IllegalArgumentException excecao) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
