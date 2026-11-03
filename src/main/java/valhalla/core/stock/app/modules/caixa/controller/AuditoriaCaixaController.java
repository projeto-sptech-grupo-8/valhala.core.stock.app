package valhalla.core.stock.app.modules.caixa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.caixa.dto.AuditoriaCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.service.AuditoriaCaixaService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;
import valhalla.core.stock.app.shared.pagination.DirecaoOrdenacao;
import valhalla.core.stock.app.shared.pagination.RespostaPaginadaDto;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/caixa/auditoria")
@Tag(name = "Auditoria do caixa", description = "Trilha de eventos do caixa, incluindo configuração, abertura, fechamento, sangria, reforço e divergências.")
@SecurityRequirement(name = "accessTokenCookie")
public class AuditoriaCaixaController {

    private final AuditoriaCaixaService auditoriaCaixaService;

    public AuditoriaCaixaController(AuditoriaCaixaService auditoriaCaixaService) {
        this.auditoriaCaixaService = auditoriaCaixaService;
    }

    @GetMapping
    @Operation(summary = "Listar eventos de auditoria do caixa com filtros e paginação")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos retornados."),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão para visualizar o caixa.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<RespostaPaginadaDto<AuditoriaCaixaResponseDto>> listar(
            @Parameter(description = "Código do evento. Omitido: todos.", example = "FECHAMENTO_CAIXA")
            @RequestParam(required = false) String tipoEvento,
            @Parameter(description = "Sessão de caixa relacionada. Omitido: todas.")
            @RequestParam(required = false) UUID sessaoCaixaId,
            @Parameter(description = "Usuário responsável pelo evento. Omitido: todos.")
            @RequestParam(required = false) UUID usuarioId,
            @Parameter(description = "Data inicial do evento no formato ISO (AAAA-MM-DD).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final do evento no formato ISO (AAAA-MM-DD), inclusiva.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Número da página, iniciado em 0. Padrão: 0.",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Itens por página. Padrão: 20; máximo: 100.",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @Parameter(description = "Campo de ordenação: ocorridoEm, valor, tipoEvento ou usuarioNome. Padrão: ocorridoEm.",
                    schema = @Schema(defaultValue = "ocorridoEm"))
            @RequestParam(defaultValue = "ocorridoEm") String ordenarPor,
            @Parameter(description = "Direção da ordenação: ASC ou DESC. Padrão: DESC.",
                    schema = @Schema(defaultValue = "DESC"))
            @RequestParam(defaultValue = "DESC") DirecaoOrdenacao direcao
    ) {
        return ResponseEntity.ok(auditoriaCaixaService.listar(
                tipoEvento,
                sessaoCaixaId,
                usuarioId,
                dataInicio,
                dataFim,
                pagina,
                tamanho,
                ordenarPor,
                direcao
        ));
    }
}
