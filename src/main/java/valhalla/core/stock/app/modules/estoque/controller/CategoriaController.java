package valhalla.core.stock.app.modules.estoque.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.estoque.dto.CategoriaRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.CategoriaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.CategoriaResponseDto;
import valhalla.core.stock.app.modules.estoque.service.CategoriaService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/categorias")
@Tag(name = "Categorias", description = "Categorias de produtos do estabelecimento autenticado.")
@SecurityRequirement(name = "accessTokenCookie")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    @Operation(summary = "Listar categorias ativas do estabelecimento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categorias retornadas."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar estoque.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<CategoriaResponseDto>> listarCategorias() {
        return ResponseEntity.ok(categoriaService.listarCategorias());
    }

    @PostMapping
    @Operation(summary = "Criar nova categoria no estabelecimento")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para gerenciar estoque.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nome de categoria duplicado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CategoriaResponseDto> criarCategoria(@Valid @RequestBody CategoriaRequestDto requestDto) {
        CategoriaResponseDto categoriaCriada = categoriaService.criarCategoria(requestDto);
        return ResponseEntity.created(URI.create("/categorias/" + categoriaCriada.id())).body(categoriaCriada);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar categoria por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou categoria de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CategoriaResponseDto> buscarCategoria(@PathVariable("id") Integer idCategoria) {
        return ResponseEntity.ok(categoriaService.buscarCategoria(idCategoria));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualizar categoria")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou categoria de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nome de categoria duplicado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CategoriaResponseDto> atualizarCategoria(
            @PathVariable("id") Integer idCategoria,
            @Valid @RequestBody CategoriaAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(categoriaService.atualizarCategoria(idCategoria, requestDto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir categoria")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoria excluída."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou categoria de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Categoria possui produtos vinculados.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> excluirCategoria(@PathVariable("id") Integer idCategoria) {
        categoriaService.excluirCategoria(idCategoria);
        return ResponseEntity.noContent().build();
    }
}
