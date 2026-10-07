package valhalla.core.stock.app.modules.estoque.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.ProdutoResponseDto;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.service.ProdutoService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/produtos")
@Tag(name = "Produtos", description = "Produtos do estabelecimento autenticado.")
@SecurityRequirement(name = "accessTokenCookie")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    @Operation(summary = "Listar produtos com filtros, ordenação e paginação aplicados no banco")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produtos retornados."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar estoque.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<ProdutoResponseDto>> listarProdutos(
            @Parameter(description = "Busca por nome, SKU ou código de barras", example = "absolut")
            @RequestParam(required = false) String busca,
            @Parameter(description = "ID da categoria", example = "1")
            @RequestParam(required = false) Integer categoriaId,
            @Parameter(description = "Tipo do produto: PADRAO ou DRINK. Omitido: ambos.", example = "PADRAO")
            @RequestParam(required = false) ProdutoTipo tipo,
            @Parameter(description = "Situação do produto. Omitido: ativos e inativos.", example = "true")
            @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "Número da página, iniciado em 0. Padrão: 0.", schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página. Padrão: 20; máximo: 100.", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: nome, sku, precoVenda, precoCusto, criadoEm ou atualizadoEm. Padrão: nome.", schema = @Schema(defaultValue = "nome"))
            @RequestParam(defaultValue = "nome") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC. Padrão: ASC.", schema = @Schema(defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(produtoService.listarProdutos(
                busca, categoriaId, tipo, ativo, pagina, tamanho, ordenarPor, direcao
        ));
    }

    @PostMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Criar produto e gerar SKU no backend")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou categoria de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProdutoResponseDto> criarProduto(
            @Valid @RequestBody ProdutoRequestDto requestDto
    ) {
        ProdutoResponseDto produtoCriado = produtoService.criarProduto(requestDto);
        return ResponseEntity.created(URI.create("/produtos/" + produtoCriado.id())).body(produtoCriado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto encontrado."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Produto de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProdutoResponseDto> buscarProduto(@PathVariable UUID id) {
        return ResponseEntity.ok(produtoService.buscarProduto(id));
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Atualizar dados do produto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto atualizado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou produto de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto ou categoria não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ProdutoResponseDto> atualizarProduto(
            @PathVariable UUID id,
            @Valid @RequestBody ProdutoAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(produtoService.atualizarProduto(id, requestDto));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Excluir produto sem movimentações ou vínculos")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Produto excluído."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou produto de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Produto possui movimentações ou vínculos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> excluirProduto(@PathVariable UUID id) {
        produtoService.excluirProduto(id);
        return ResponseEntity.noContent().build();
    }
}
