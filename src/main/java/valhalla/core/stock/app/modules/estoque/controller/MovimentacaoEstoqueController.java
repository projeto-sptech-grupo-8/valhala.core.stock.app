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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoRequestDto;
import valhalla.core.stock.app.modules.estoque.dto.MovimentacaoResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.SaidaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.entity.TipoMovimentacaoEstoque;
import valhalla.core.stock.app.modules.estoque.service.MovimentacaoEstoqueService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/movimentacoes-estoque")
@Tag(name = "Movimentações de estoque", description = "Histórico e alterações auditáveis do saldo dos produtos.")
@SecurityRequirement(name = "accessTokenCookie")
public class MovimentacaoEstoqueController {

    private final MovimentacaoEstoqueService movimentacaoEstoqueService;

    public MovimentacaoEstoqueController(MovimentacaoEstoqueService movimentacaoEstoqueService) {
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
    }

    @GetMapping
    @Operation(summary = "Listar movimentações com filtros, ordenação e paginação aplicados no banco")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Movimentações retornadas."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar estoque.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<MovimentacaoResponseDto>> listarMovimentacoes(
            @Parameter(description = "ID do produto movimentado. Omitido: todos os produtos.")
            @RequestParam(required = false) UUID produtoId,
            @Parameter(description = "Tipo da movimentação. Omitido: todos os tipos.", example = "SAIDA")
            @RequestParam(required = false) TipoMovimentacaoEstoque tipo,
            @Parameter(description = "ID do usuário que registrou a movimentação. Omitido: todos os usuários.")
            @RequestParam(required = false) UUID usuarioId,
            @Parameter(description = "Data inicial inclusiva, em yyyy-MM-dd. Omitido: sem limite inferior.", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @Parameter(description = "Data final inclusiva, em yyyy-MM-dd. Omitido: sem limite superior.", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @Parameter(description = "Número da página, iniciado em 0. Padrão: 0.", schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página. Padrão: 20; máximo: 100.", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: ocorridoEm, tipo, quantidade ou saldoPosterior. Padrão: ocorridoEm.", schema = @Schema(defaultValue = "ocorridoEm"))
            @RequestParam(defaultValue = "ocorridoEm") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC. Padrão: DESC.", schema = @Schema(defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(movimentacaoEstoqueService.listarMovimentacoes(
                produtoId, tipo, usuarioId, dataInicial, dataFinal,
                pagina, tamanho, ordenarPor, direcao
        ));
    }

    @PostMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Registrar entrada, saída, ajuste ou perda de produto padrão")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Movimentação registrada."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, estoque insuficiente ou regra violada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou produto de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Produto ou estoque não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<MovimentacaoResponseDto> registrarMovimentacao(
            @Valid @RequestBody MovimentacaoRequestDto requestDto
    ) {
        MovimentacaoResponseDto resposta = movimentacaoEstoqueService.registrarMovimentacao(requestDto);
        return ResponseEntity.created(URI.create("/movimentacoes-estoque/" + resposta.id())).body(resposta);
    }

    @PostMapping("/saida-drink")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Baixar todos os ingredientes de um drink")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ingredientes baixados."),
            @ApiResponse(responseCode = "400", description = "Receita inválida ou saldo insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou drink de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Drink, ingrediente ou estoque não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<MovimentacaoResponseDto>> registrarSaidaDrink(
            @Valid @RequestBody SaidaDrinkRequestDto requestDto
    ) {
        List<MovimentacaoResponseDto> respostas = movimentacaoEstoqueService.registrarSaidaDrink(requestDto);
        return ResponseEntity.created(URI.create("/movimentacoes-estoque/saida-drink")).body(respostas);
    }
}
