package valhalla.core.stock.app.modules.caixa.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.dto.CaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.CaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.CaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaAtualizacaoRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.TerminalCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.service.CaixaService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/caixa/caixas")
@Tag(name = "Caixas e terminais", description = "Cadastro dos caixas (postos físicos) e das maquininhas vinculadas.")
@SecurityRequirement(name = "accessTokenCookie")
public class CaixaController {

    private final CaixaService caixaService;

    public CaixaController(CaixaService caixaService) {
        this.caixaService = caixaService;
    }

    @GetMapping
    @Operation(summary = "Listar caixas do estabelecimento com suas maquininhas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Caixas retornados."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<CaixaResponseDto>> listarCaixas(
            @Parameter(description = "Trecho do nome do caixa. Omitido: sem filtro.", example = "Principal")
            @RequestParam(required = false) String busca,
            @Parameter(description = "Situação do caixa. Omitido: ativos e inativos.", example = "true")
            @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "Número da página, iniciado em 0. Padrão: 0.",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página. Padrão: 20; máximo: 100.",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: nome, criadoEm ou atualizadoEm. Padrão: nome.",
                    schema = @Schema(defaultValue = "nome"))
            @RequestParam(defaultValue = "nome") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC. Padrão: ASC.",
                    schema = @Schema(defaultValue = "ASC"))
            @RequestParam(defaultValue = "ASC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(caixaService.listarCaixas(
                busca, ativo, pagina, tamanho, ordenarPor, direcao
        ));
    }

    @PostMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Cadastrar um novo caixa")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Caixa criado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para configurar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe um caixa com este nome.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CaixaResponseDto> criarCaixa(
            @Valid @RequestBody CaixaRequestDto requestDto
    ) {
        CaixaResponseDto caixa = caixaService.criarCaixa(requestDto);
        return ResponseEntity.created(URI.create("/caixa/caixas/" + caixa.id())).body(caixa);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar um caixa pelo identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Caixa encontrado."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caixa de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Caixa não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CaixaResponseDto> buscarCaixa(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(caixaService.buscarCaixa(id));
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Atualizar o nome ou a situação de um caixa",
            description = "Não é permitido inativar um caixa com sessão aberta nem deixar a Point sem terminal ativo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Caixa atualizado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou caixa de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Caixa não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nome duplicado, sessão aberta ou último terminal da Point.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CaixaResponseDto> atualizarCaixa(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CaixaAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(caixaService.atualizarCaixa(id, requestDto));
    }

    @PostMapping("/{caixaId}/terminais")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Vincular uma maquininha ao caixa",
            description = "Um caixa pode ter diversas maquininhas; o terminalId do dispositivo é único no sistema."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Terminal vinculado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou caixa de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Caixa não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Terminal ou nome já em uso.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TerminalCaixaResponseDto> criarTerminal(
            @PathVariable("caixaId") UUID caixaId,
            @Valid @RequestBody TerminalCaixaRequestDto requestDto
    ) {
        TerminalCaixaResponseDto terminal = caixaService.criarTerminal(caixaId, requestDto);
        return ResponseEntity
                .created(URI.create("/caixa/caixas/" + caixaId + "/terminais/" + terminal.id()))
                .body(terminal);
    }

    @PatchMapping("/{caixaId}/terminais/{terminalId}")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Atualizar o nome ou a situação de um terminal",
            description = "O terminalId do dispositivo não é editável; para trocá-lo, inative o atual e cadastre outro."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Terminal atualizado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou caixa de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Caixa ou terminal não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Nome duplicado ou último terminal da Point.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TerminalCaixaResponseDto> atualizarTerminal(
            @PathVariable("caixaId") UUID caixaId,
            @PathVariable("terminalId") UUID terminalId,
            @Valid @RequestBody TerminalCaixaAtualizacaoRequestDto requestDto
    ) {
        return ResponseEntity.ok(caixaService.atualizarTerminal(caixaId, terminalId, requestDto));
    }
}
