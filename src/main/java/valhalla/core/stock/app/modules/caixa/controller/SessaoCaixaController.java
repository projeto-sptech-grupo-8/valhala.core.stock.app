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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.dto.AbrirSessaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.FecharSessaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.MovimentacaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.ResolverDivergenciaCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.SessaoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.dto.SessaoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.StatusSessaoCaixa;
import valhalla.core.stock.app.modules.caixa.service.SessaoCaixaService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/caixa/sessoes")
@Tag(name = "Sessões de caixa", description = "Abertura, operação, fechamento e conferência das sessões de caixa.")
@SecurityRequirement(name = "accessTokenCookie")
public class SessaoCaixaController {

    private final SessaoCaixaService sessaoCaixaService;

    public SessaoCaixaController(SessaoCaixaService sessaoCaixaService) {
        this.sessaoCaixaService = sessaoCaixaService;
    }

    @PostMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Abrir uma sessão de caixa",
            description = """
                    Abre uma sessão para o operador autenticado usando o terminal configurado
                    para o estabelecimento. Um terminal não aceita duas sessões ABERTAS ao
                    mesmo tempo; sessões EM_FECHAMENTO aguardando aprovação não bloqueiam
                    uma nova abertura.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sessão aberta."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para operar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe sessão aberta para o operador ou terminal.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SessaoCaixaDetalheResponseDto> abrirSessao(
            @Valid @RequestBody AbrirSessaoCaixaRequestDto requestDto
    ) {
        SessaoCaixaDetalheResponseDto sessao = sessaoCaixaService.abrirSessao(requestDto);
        return ResponseEntity.created(URI.create("/caixa/sessoes/" + sessao.id())).body(sessao);
    }

    @GetMapping("/atual")
    @Operation(
            summary = "Consultar a sessão aberta do operador autenticado",
            description = "Retorna 204 quando o operador não possui uma sessão ABERTA."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão aberta retornada."),
            @ApiResponse(responseCode = "204", description = "Nenhuma sessão aberta para o operador."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SessaoCaixaDetalheResponseDto> buscarSessaoAtual() {
        return sessaoCaixaService.buscarSessaoAtual()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    @Operation(summary = "Listar sessões de caixa com filtros e paginação")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessões retornadas."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<SessaoCaixaResumoResponseDto>> listarSessoes(
            @Parameter(description = "Situação da sessão. Omitido: todas.", example = "ABERTA")
            @RequestParam(required = false) StatusSessaoCaixa status,
            @Parameter(description = "Trecho do identificador do terminal. Omitido: todos.",
                    example = "TERM-001")
            @RequestParam(required = false) String terminalId,
            @Parameter(description = "Operador que abriu a sessão. Omitido: todos.")
            @RequestParam(required = false) UUID operadorUsuarioId,
            @Parameter(description = "Data inicial da abertura no formato ISO (AAAA-MM-DD).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final da abertura no formato ISO (AAAA-MM-DD), inclusiva.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Número da página, iniciado em 0. Padrão: 0.",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página. Padrão: 20; máximo: 100.",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: abertaEm, fechadaEm, atualizadoEm, status ou diferenca. Padrão: abertaEm.",
                    schema = @Schema(defaultValue = "abertaEm"))
            @RequestParam(defaultValue = "abertaEm") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC. Padrão: DESC.",
                    schema = @Schema(defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(sessaoCaixaService.listarSessoes(
                status,
                terminalId,
                operadorUsuarioId,
                dataInicio,
                dataFim,
                pagina,
                tamanho,
                ordenarPor,
                direcao
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar o detalhe de uma sessão de caixa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão encontrada."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sessão de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SessaoCaixaDetalheResponseDto> buscarSessao(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(sessaoCaixaService.buscarSessao(id));
    }

    @PostMapping("/{id}/movimentacoes")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Registrar sangria ou reforço",
            description = """
                    A sangria é limitada ao dinheiro disponível na gaveta: fundo de troco,
                    vendas aprovadas em dinheiro e reforços, menos sangrias anteriores.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Movimentação registrada."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou sessão de outro operador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Sessão não está aberta ou saldo insuficiente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SessaoCaixaDetalheResponseDto> registrarMovimentacao(
            @PathVariable("id") UUID id,
            @Valid @RequestBody MovimentacaoCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(sessaoCaixaService.registrarMovimentacao(id, requestDto));
    }

    @PostMapping("/{id}/fechamento")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Fechar a sessão de caixa",
            description = """
                    Com diferença zero a sessão passa para FECHADA. Com diferença, a
                    justificativa é obrigatória e a sessão passa para EM_FECHAMENTO até a
                    aprovação gerencial. Pagamentos pendentes bloqueiam o fechamento.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fechamento registrado."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou sessão de outro operador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Sessão não está aberta, falta justificativa ou há pagamento pendente.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SessaoCaixaDetalheResponseDto> fecharSessao(
            @PathVariable("id") UUID id,
            @Valid @RequestBody FecharSessaoCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(sessaoCaixaService.fecharSessao(id, requestDto));
    }

    @PostMapping("/{id}/resolucao-divergencia")
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(
            summary = "Aprovar a divergência de uma sessão em fechamento",
            description = "Conclui a sessão EM_FECHAMENTO como FECHADA, preservando quem encerrou o turno."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Divergência resolvida."),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para resolver divergência.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Sessão não está em fechamento ou a divergência já foi resolvida.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SessaoCaixaDetalheResponseDto> resolverDivergencia(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ResolverDivergenciaCaixaRequestDto requestDto
    ) {
        return ResponseEntity.ok(sessaoCaixaService.resolverDivergencia(id, requestDto));
    }
}
